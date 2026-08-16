# Stale Match Execution Ownership Fix Implementation Plan

> **For Luna:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task using inline execution and checkpoints.

**Goal:** Prevent a stopped `playUntilFinished()` invocation that is still blocked in synchronous dialogue generation from overlapping with a resumed invocation and continuing the same match concurrently.

**Architecture:** Keep the existing execution-generation token for stale dialogue rejection, but serialize whole `MatchEngine.playUntilFinished()` invocations at the engine boundary. `stopCurrentMatch()` must remain unsynchronized so Stop can invalidate dialogue and set the stop flag while the current execution is blocked. A resumed executor task may be submitted immediately, but it must wait until the stopped invocation exits before it can reset shared execution state and continue the match.

**Tech Stack:** Java 25, Spring Boot 4.1, JUnit 5, Mockito, Maven.

## Global Constraints

- Work only on PR #57 branch `feature/issue-43-dialogue-lifecycle`.
- Inline execution only with `superpowers:executing-plans`.
- Do not add another executor, lock class, cancellation framework, provider interruption mechanism, database change, frontend change, or new dependency.
- Do not change dialogue persistence, provider timeouts, personality selection, or rendering.
- Preserve the existing behavior that `stopCurrentMatch()` can be called while match execution is inside Stockfish, evaluation, dialogue, or pacing work.
- Preserve all existing sequential stop/resume tests and dialogue-authority generation tests.
- The fix must make overlapping `playUntilFinished()` invocations impossible, not merely make stale dialogue harmless.

---

## File Map

- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
  - Owns the single synchronous match execution. Serialize `playUntilFinished()` here.
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
  - Add the deterministic concurrent Stop/Resume regression that reproduces PR #57's review blocker.

No other production file should need to change.

---

### Task 1: Reproduce the Overlapping Stop/Resume Execution Race

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`

**Interfaces:**
- Consumes: existing `MatchEngine.playUntilFinished()`, `MatchEngine.stopCurrentMatch()`, `FakeChessPlayer`, and mocked `MatchDialogueCoordinator`.
- Produces: one regression proving a resumed execution cannot enter `playUntilFinished()` while the stopped invocation is still blocked in move dialogue.

- [ ] **Step 1: Add concurrency imports**

Add these imports beside the existing `java.util.concurrent` imports:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
```

Keep the existing `AtomicReference` import.

- [ ] **Step 2: Add the failing concurrent regression test**

Place this test beside the existing `stopInvalidatesAuthorityCapturedByRunningDialogue()` and `resumeGetsANewExecutionGeneration()` tests:

```java
@Test
void resumeWaitsForStoppedExecutionToLeaveDialogueBeforeStartingAnotherLoop() throws Exception {
  FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5", "c7c5");
  MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
  CountDownLatch firstDialogueEntered = new CountDownLatch(1);
  CountDownLatch releaseFirstDialogue = new CountDownLatch(1);
  AtomicInteger moveDialogueCalls = new AtomicInteger();

  doAnswer(
          invocation -> {
            if (moveDialogueCalls.incrementAndGet() == 1) {
              firstDialogueEntered.countDown();
              assertTrue(releaseFirstDialogue.await(5, TimeUnit.SECONDS));
            }
            return null;
          })
      .when(coordinator)
      .onMove(any(), any(), any());

  MatchEngine engine =
      matchEngine(
          chessPlayer,
          250,
          2,
          NO_OP_PACING,
          event -> {},
          new FakeChessEvaluationService(),
          coordinator);
  ExecutorService executor = Executors.newFixedThreadPool(2);

  try {
    Future<Match> stoppedExecution = executor.submit(engine::playUntilFinished);
    assertTrue(firstDialogueEntered.await(5, TimeUnit.SECONDS));

    engine.stopCurrentMatch();

    CountDownLatch resumeTaskStarted = new CountDownLatch(1);
    Future<Match> resumedExecution =
        executor.submit(
            () -> {
              resumeTaskStarted.countDown();
              return engine.playUntilFinished();
            });
    assertTrue(resumeTaskStarted.await(5, TimeUnit.SECONDS));

    assertThrows(
        TimeoutException.class,
        () -> resumedExecution.get(500, TimeUnit.MILLISECONDS));

    releaseFirstDialogue.countDown();

    stoppedExecution.get(5, TimeUnit.SECONDS);
    Match finished = resumedExecution.get(5, TimeUnit.SECONDS);

    assertTrue(finished.isFinished());
    assertEquals(2, finished.moveCount());
    assertEquals("e2e4", finished.moves().get(0).notation().value());
    assertEquals("e7e5", finished.moves().get(1).notation().value());
    assertEquals(2, chessPlayer.chooseMoveMatches.size());
    assertEquals(finished, engine.currentMatch());
  } finally {
    releaseFirstDialogue.countDown();
    executor.shutdownNow();
  }
}
```

Why the third fake move exists: on the buggy implementation, the old invocation can resume after the new invocation resets `stopRequested` and can request another stale move. The final assertion requires exactly two `chooseMove` calls, so a stale third move is forbidden.

The `resumeTaskStarted` latch proves the second executor worker has actually been scheduled before the timeout assertion. The expected timeout is therefore checking engine serialization, not merely executor scheduling.

- [ ] **Step 3: Run the regression and confirm RED**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchEngineTest#resumeWaitsForStoppedExecutionToLeaveDialogueBeforeStartingAnotherLoop test
```

Expected on the current PR #57 implementation: **FAIL** because `resumedExecution` enters `playUntilFinished()` while the first invocation is still blocked, resets shared execution state, and completes instead of timing out.

Do not weaken the test timeout or change the expected behavior to make current code pass.

---

### Task 2: Serialize Whole Match Executions at `MatchEngine`

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`

**Interfaces:**
- Consumes: the existing single-match `MatchEngine` lifecycle and unsynchronized `stopCurrentMatch()`.
- Produces: at most one active `playUntilFinished()` invocation per `MatchEngine` instance while preserving Stop's ability to invalidate the active execution.

- [ ] **Step 1: Apply the minimal production fix**

Change only the `playUntilFinished()` method declaration from:

```java
public Match playUntilFinished() {
```

to:

```java
public synchronized Match playUntilFinished() {
```

Do **not** synchronize `stopCurrentMatch()`.

Do **not** add a second lock or condition variable.

Do **not** remove `executionGeneration`. It still has a separate job: a Stop must immediately make authority suppliers captured by an in-flight dialogue provider return `false`, even before the provider call returns.

The intended lifecycle is now:

```text
Execution A owns playUntilFinished monitor
  -> commits move
  -> blocks in synchronous dialogue

Stop
  -> stopCurrentMatch remains callable
  -> executionGeneration increments
  -> stopRequested = true
  -> stale dialogue is rejected

Resume request
  -> submits Execution B
  -> B waits at playUntilFinished monitor
  -> B cannot reset stopRequested yet

Provider returns to A
  -> A observes stopped execution and leaves its loop
  -> A releases playUntilFinished monitor

Execution B enters
  -> increments its generation
  -> resets stopRequested = false
  -> resumes the authoritative match exactly once
```

This deliberately serializes a method that already represents the app's single synchronous match runner. Holding this monitor across the bounded dialogue call is acceptable here because `stopCurrentMatch()` is not synchronized and the product supports one showcase match at a time.

- [ ] **Step 2: Run the new regression and confirm GREEN**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchEngineTest#resumeWaitsForStoppedExecutionToLeaveDialogueBeforeStartingAnotherLoop test
```

Expected: **PASS**.

- [ ] **Step 3: Run the complete MatchEngine regression suite**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchEngineTest test
```

Expected: **PASS**.

Pay particular attention to these existing tests; they must remain unchanged in intent and pass:

- `stopInvalidatesAuthorityCapturedByRunningDialogue`
- `resumeGetsANewExecutionGeneration`
- `playUntilFinishedStopsAfterCurrentIterationWhenStopIsRequested`
- `playUntilFinishedResumesStoppedMatch`
- `playUntilFinishedResumesWithoutDuplicatingEvaluationBaseline`
- `stopDuringPostMoveEvaluationKeepsCommittedPlyAndResumeBaselineConsistent`
- `resumingStoppedMatchDoesNotEmitAnotherMatchStarted`

If one of these fails, do not paper over it by weakening assertions. Re-check that only `playUntilFinished()` was synchronized and `stopCurrentMatch()` stayed unsynchronized.

- [ ] **Step 4: Commit the focused fix**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java
git commit -m "fix: serialize stopped match resume execution"
```

---

### Task 3: Verify PR #57 Without Expanding Scope

**Files:**
- No production changes expected.

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: evidence that the corrective change preserves issue #43 behavior and the repository quality gates.

- [ ] **Step 1: Run backend verification**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: **PASS**, including formatting/static analysis/unit tests/Modulith verification configured by the repository.

If Spotless reports only formatting differences, apply the repository formatter and rerun:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml verify
```

- [ ] **Step 2: Run the repository verifier**

From repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
```

Expected: **PASS** for backend and frontend verification.

The PostgreSQL dialogue integration test does **not** need to be changed or rerun specifically for this correction because no persistence code or migration changes. Do not modify it merely to increase test count.

- [ ] **Step 3: Push and check hosted CI**

Push the existing PR branch:

```bash
git push origin feature/issue-43-dialogue-lifecycle
```

Confirm PR #57's hosted jobs pass again, especially:

- Backend verification
- Frontend verification
- Native image verification

Do not mark the PR ready solely because CI is green; confirm the new concurrent regression is present in the pushed diff.

- [ ] **Step 4: Final self-review**

Before handing back, verify the diff is intentionally tiny:

```text
Expected corrective production change:
- MatchEngine.playUntilFinished(): synchronized

Expected corrective test change:
- one deterministic concurrent Stop/Resume regression
- required concurrency imports
```

Reject any implementation that introduces:

- a new executor or scheduler,
- `synchronized` on `stopCurrentMatch()`,
- provider cancellation/interruption logic,
- new database state,
- frontend changes,
- issue #44 personality-selection work,
- issue #45 dialogue-rendering work,
- broad refactoring of `MatchEngine`.

If the implementation needs materially more than this, stop and re-evaluate the approach instead of expanding the PR.
