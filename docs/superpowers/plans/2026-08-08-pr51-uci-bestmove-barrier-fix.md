# PR #51 UCI Bestmove Barrier Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make failed Stockfish search recovery correct under the actual UCI protocol by retaining ownership of a timed-out read, draining the stopped search through its mandatory `bestmove`, and only then performing `isready` / `readyok` before engine reuse.

**Architecture:** Keep the existing single Stockfish process and single reader executor. Replace the current cancel-and-forget timed-out `readLine()` behavior with one retained pending read future so exactly one task owns stdout at a time. A failed in-flight search recovers in this exact order: `stop` -> consume that search's `bestmove` -> `isready` -> consume `readyok`; if any recovery step fails, keep the existing fail-closed `usable=false` behavior. Do not add another process, restart orchestration, a queue-based production reader, or a new public API.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Stockfish 17.1/UCI, JUnit 5, AssertJ, existing Maven/Spotless/Error Prone/SpotBugs verification.

## Source of Truth

- PR: `#51 feat: add Stockfish evaluation swing analysis`
- Issue: `#39 Phase 2: Add lightweight Stockfish evaluation swing analysis`
- Original plan: `docs/superpowers/plans/2026-08-08-stockfish-evaluation-swing-analysis.md`
- First review-fix plan: `docs/superpowers/plans/2026-08-08-pr51-uci-search-recovery.md`
- Latest review finding: `readyok` is not a search-completion barrier. UCI permits `isready` to be answered while a search is still running. For a started `go`, the corresponding `bestmove` is the search-completion message that must be consumed before `readyok` is used as the final readiness check.

## Global Constraints

- Keep exactly one Stockfish process.
- Keep the existing single-threaded `lineReaderExecutor`; do not add another executor, background production reader loop, queue, scheduler, or reactive abstraction.
- There must never be more than one outstanding `reader.readLine()` operation.
- A timeout must **not** cancel and discard the outstanding read future. That future remains owned by `StockfishEngine` and is reused by recovery.
- Recovery for an actually in-flight failed search is exactly: `stop` -> consume lines until that search's `bestmove` -> `isready` -> consume lines until `readyok`.
- `readyok` alone must never be treated as proof that a stopped search completed.
- If `bestmove` was already consumed before the failure (for example evaluation receives `bestmove` but no score), do **not** send `stop` and do not wait for a second `bestmove`.
- If recovery cannot consume `bestmove` and then `readyok` within the existing bounded timeout, keep the current behavior that marks the engine unusable and blocks later commands.
- Apply the same search-state invariant to both `bestMove()` and `evaluate()`.
- Preserve issue #39 behavior unchanged: evaluation remains best-effort, mover-perspective normalization and thresholds do not change, legal committed moves are not rolled back, and baseline reuse still requires exact FEN + ply.
- Preserve the stop-during-post-move-evaluation lifecycle test already added in the previous review fix.
- Do not modify `StockfishClient`, `ChessEvaluationService`, `StockfishEvaluationService`, match event payloads, WebSocket schema, frontend, persistence, Spring AI code, or evaluation configuration defaults.
- No new runtime dependency.
- Run Spotless before full verification.

## File Map

**Modify only:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java` — retain one pending read future, distinguish `searchStarted` from `bestmoveReceived`, and recover through `bestmove` before `readyok`.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java` — make the protocol fake asynchronous and interrupt-insensitive so it reproduces both the early-`readyok` ordering problem and the orphaned-reader problem.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java` — `stop()` already exists.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/StockfishClient.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java` unless compilation requires a purely mechanical fixture adjustment; no behavioral redesign is needed there.
- `client/**`
- persistence/Flyway files
- `ai/**`

---

### Task 1: Replace the Optimistic Protocol Fake with a Realistic Regression Harness

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java`

**Interfaces:**
- Consumes: current package-private `StockfishEngine(Process, ChessProperties.Stockfish)` constructor.
- Produces: a deterministic fake process whose stdout read ignores interruption and whose stopped-search `bestmove` is delayed independently of `isready`.
- Produces: trace entries formatted exactly as `IN <command>` and `OUT <response>`.

- [ ] **Step 1: Replace the fake process stdout pipe with an interrupt-insensitive test stream**

Inside `StockfishEngineRecoveryTest`, add this nested class:

```java
private static final class InterruptIgnoringInputStream extends InputStream {

  private final java.util.concurrent.BlockingQueue<Integer> bytes =
      new java.util.concurrent.LinkedBlockingQueue<>();
  private volatile boolean closed;

  void emitLine(String line) {
    byte[] encoded =
        (line + System.lineSeparator())
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (byte value : encoded) {
      bytes.add(value & 0xFF);
    }
  }

  @Override
  public int read() {
    while (true) {
      Integer value = bytes.poll();
      if (value != null) {
        return value;
      }
      if (closed) {
        return -1;
      }
      try {
        Thread.sleep(5);
      } catch (InterruptedException ignored) {
        // Deliberately ignore interruption to model a process-pipe read that
        // Future.cancel(true) cannot safely detach from the underlying stream.
      }
    }
  }

  @Override
  public int read(byte[] target, int offset, int length) {
    int first = read();
    if (first < 0) {
      return -1;
    }
    target[offset] = (byte) first;
    int count = 1;
    while (count < length) {
      Integer value = bytes.poll();
      if (value == null) {
        break;
      }
      target[offset + count] = (byte) (int) value;
      count++;
    }
    return count;
  }

  @Override
  public void close() {
    closed = true;
  }
}
```

In `ScriptedUciProcess`, replace the old fake-engine-to-client pipe fields:

```java
private final PipedInputStream clientReads = new PipedInputStream();
private final PipedOutputStream engineWrites;
```

with:

```java
private final InterruptIgnoringInputStream clientReads =
    new InterruptIgnoringInputStream();
```

Keep the client-to-fake-engine command pipe:

```java
private final PipedInputStream engineReads = new PipedInputStream();
private final PipedOutputStream clientWrites;
```

The constructor should now connect only the command pipe:

```java
try {
  clientWrites = new PipedOutputStream(engineReads);
} catch (IOException exception) {
  throw new IllegalStateException(exception);
}
```

`getInputStream()` continues to return `clientReads`.

In `close()`, close `clientWrites`, `clientReads`, and `engineReads`; remove references to the deleted `engineWrites` field.

This stream exists only in tests. Do not copy it into production.

- [ ] **Step 2: Add deterministic protocol tracing and one output helper**

Add:

```java
private final List<String> trace = new CopyOnWriteArrayList<>();
```

Expose:

```java
List<String> trace() {
  return List.copyOf(trace);
}
```

When the fake receives a command, record both lists before handling it:

```java
commands.add(command);
trace.add("IN " + command);
```

Add exactly one fake-engine output helper:

```java
private synchronized void emitLine(String line) {
  trace.add("OUT " + line);
  clientReads.emitLine(line);
}
```

After this change, every fake response must use `emitLine(...)`. Do not keep a `BufferedWriter` for fake stdout.

The `runEngine()` resource header should therefore be only:

```java
try (BufferedReader reader =
    new BufferedReader(new InputStreamReader(engineReads))) {
```

- [ ] **Step 3: Make the stopped evaluation bestmove asynchronous**

For the recoverable fake, change `stop` handling to:

```java
case "stop" -> {
  if (recoverable) {
    Thread delayedBestmove =
        new Thread(
            () -> {
              try {
                Thread.sleep(75);
                emitLine("bestmove a2a3");
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
            },
            "scripted-delayed-stale-bestmove");
    delayedBestmove.setDaemon(true);
    delayedBestmove.start();
  }
}
```

This delayed `bestmove a2a3` represents the timed-out evaluation search completing after `stop`.

- [ ] **Step 4: Keep `isready` immediate so the old implementation is provably unsafe**

Keep the fake able to respond to `isready` immediately:

```java
case "isready" -> {
  if (recoverable || commands.stream().noneMatch("go depth 8 movetime 1"::equals)) {
    emitLine("readyok");
  } else {
    clientReads.close();
    alive = false;
  }
}
```

This deliberately allows `readyok` to be emitted before the delayed stopped-search `bestmove` if production code sends `isready` too early.

- [ ] **Step 5: Delay the next valid move so stale output would win the race**

Handle the normal move search as:

```java
case "go movetime 10" -> {
  Thread validBestmove =
      new Thread(
          () -> {
            try {
              Thread.sleep(150);
              emitLine("bestmove e2e4");
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          },
          "scripted-delayed-valid-bestmove");
  validBestmove.setDaemon(true);
  validBestmove.start();
}
```

Do not add sleeps to production code.

- [ ] **Step 6: Strengthen the timed-out-evaluation regression**

Keep:

```java
assertThat(move).isEqualTo("e2e4");
```

Replace the old command-only subsequence check with:

```java
assertThat(process.trace())
    .containsSubsequence(
        "IN go depth 8 movetime 1",
        "IN stop",
        "OUT bestmove a2a3",
        "IN isready",
        "OUT readyok",
        "IN position startpos",
        "IN go movetime 10",
        "OUT bestmove e2e4");
```

The critical invariant is that `OUT bestmove a2a3` occurs before recovery sends `IN isready`.

- [ ] **Step 7: Add a completed-search-with-invalid-payload fake mode**

Add a third mode/factory:

```java
static ScriptedUciProcess bestmoveWithoutScore() {
  return new ScriptedUciProcess(Mode.BESTMOVE_WITHOUT_SCORE);
}
```

Replace the boolean `recoverable` with this nested enum if needed to keep the fake explicit:

```java
private enum Mode {
  RECOVERABLE_TIMEOUT,
  UNRECOVERABLE_TIMEOUT,
  BESTMOVE_WITHOUT_SCORE
}
```

For `BESTMOVE_WITHOUT_SCORE`:

- startup `uci` / `isready` behavior remains normal;
- `go depth 8 movetime 10` emits only `bestmove a2a3` with no `info ... score ...` line;
- `go movetime 10` emits `bestmove e2e4`;
- `stop` should not be required.

Add this test:

```java
@Test
void completedEvaluationWithMissingScoreDoesNotWaitForAnotherBestMove() {
  try (ScriptedUciProcess process = ScriptedUciProcess.bestmoveWithoutScore()) {
    StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

    engine.setPosition("startpos");
    assertThatThrownBy(() -> engine.evaluate(8, Duration.ofMillis(10)))
        .isInstanceOf(StockfishException.class)
        .hasMessageContaining("without an evaluation score");

    engine.setPosition("startpos");
    assertThat(engine.bestMove(Duration.ofMillis(10))).isEqualTo("e2e4");
    assertThat(process.commands()).doesNotContain("stop");
    engine.close();
  }
}
```

- [ ] **Step 8: Run the recovery test and verify RED before production edits**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest test
```

Expected before Task 2: the strengthened timeout regression fails under the current `stop -> isready -> readyok` recovery and/or because the timed-out reader is cancelled/orphaned.

Do not continue until the test genuinely exposes the bug.

---

### Task 2: Retain the Pending Read and Use Bestmove as the Search Barrier

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java`

**Interfaces:**
- Produces: field `private Future<String> pendingRead;`.
- Produces: `readLineWithTimeout(...)` that reuses one outstanding read future after timeout.
- Produces: `searchStarted` + `bestmoveReceived` state in both search methods.
- Produces: recovery sequence `stop -> waitForToken("bestmove") -> isready -> waitForToken("readyok")`.

- [ ] **Step 1: Add exactly one retained read future**

Add beside `lineReaderExecutor`:

```java
private Future<String> pendingRead;
```

Do not use `AtomicReference`; `StockfishEngine` already documents that UCI calls are serialized and must not be invoked concurrently.

- [ ] **Step 2: Refactor `readLineWithTimeout(...)` ownership**

At the start of the existing overload:

```java
private String readLineWithTimeout(long timeout, TimeUnit timeUnit, String waitingFor)
    throws IOException {
```

keep the existing `timeout <= 0` guard, then use:

```java
if (pendingRead == null) {
  pendingRead = lineReaderExecutor.submit(reader::readLine);
}
Future<String> future = pendingRead;
```

Use this exception/completion handling:

```java
try {
  String line = future.get(timeout, timeUnit);
  pendingRead = null;
  return line;
} catch (TimeoutException exception) {
  throw new StockfishException(
      ("Stockfish did not respond within %d %s while waiting for %s. "
              + "The engine process may be hung or unresponsive.")
          .formatted(timeout, timeUnit == TimeUnit.SECONDS ? "s" : "ns", waitingFor));
} catch (ExecutionException exception) {
  pendingRead = null;
  Throwable cause = exception.getCause();
  if (cause instanceof IOException ioe) {
    throw ioe;
  }
  throw new StockfishException("Unexpected error reading from Stockfish", cause);
} catch (InterruptedException exception) {
  Thread.currentThread().interrupt();
  throw new StockfishException("Interrupted while waiting for Stockfish response");
}
```

Rules:

- delete `future.cancel(true)` from the timeout branch;
- do not clear `pendingRead` on timeout;
- do not clear it on caller interruption;
- clear it only after that exact future completes normally or exceptionally.

This prevents a second `reader.readLine()` from being queued while the first timed-out task still owns the process stream.

- [ ] **Step 3: Track completed search state in `bestMove()`**

Use:

```java
boolean searchStarted = false;
boolean bestmoveReceived = false;
```

After:

```java
UciResponse response = waitForToken("bestmove", deadlineSeconds);
```

immediately set:

```java
bestmoveReceived = true;
```

Only then validate/extract the move.

In both failure catch paths, change recovery condition to:

```java
if (searchStarted && !bestmoveReceived) {
  recoverAfterSearchFailure(failure);
}
```

A malformed `bestmove` is still an operation failure, but it is not an in-flight search and must not trigger a second stop/drain cycle.

- [ ] **Step 4: Track completed search state in `evaluate()`**

Add:

```java
boolean bestmoveReceived = false;
```

When the response is `bestmove`, set the flag before score validation:

```java
if (response.startsWith("bestmove")) {
  bestmoveReceived = true;
  if (latestScore == null) {
    throw new StockfishException("Stockfish returned bestmove without an evaluation score");
  }
  return latestScore;
}
```

Use the same recovery condition in both catches:

```java
if (searchStarted && !bestmoveReceived) {
  recoverAfterSearchFailure(failure);
}
```

- [ ] **Step 5: Correct the recovery barrier**

Replace:

```java
sendCommand(UciCommand.stop());
sendCommand(UciCommand.isReady());
waitForToken("readyok", moveTimeoutSeconds);
```

with:

```java
sendCommand(UciCommand.stop());
waitForToken("bestmove", moveTimeoutSeconds);
sendCommand(UciCommand.isReady());
waitForToken("readyok", moveTimeoutSeconds);
```

Keep the existing failure handling:

```java
} catch (IOException | RuntimeException recoveryFailure) {
  usable.set(false);
  failure.addSuppressed(recoveryFailure);
  log.error("Stockfish search recovery failed; engine marked unusable", recoveryFailure);
}
```

Successful recovery makes the engine safe to reuse, but the original operation still failed and must still throw the original `StockfishException`.

- [ ] **Step 6: Run the protocol regression and verify GREEN**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest test
```

Expected: all recovery tests pass, including:

- delayed stopped-search `bestmove` is consumed before `isready`;
- next real move is `e2e4`, never stale `a2a3`;
- interrupt-insensitive stdout does not create an orphaned reader;
- recovery failure leaves the engine unusable;
- already-consumed `bestmove` with missing evaluation score does not send `stop` or wait for a second `bestmove`.

- [ ] **Step 7: Run focused surrounding tests**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineTest,StockfishEngineRecoveryTest,StockfishClientIntegrationTest,StockfishEvaluationServiceTest,UciResponseTest,MatchEngineTest test
```

Expected: zero failures and zero errors.

- [ ] **Step 8: Commit the fix**

```powershell
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java
git commit -m "fix: drain stopped Stockfish search before reuse"
```

---

### Task 3: Full Verification and PR Handoff

**Files:**
- No production design changes expected.
- Update the PR description only if its recovery wording still says `stop -> isready -> readyok`.

**Interfaces:**
- Produces: fresh verification evidence for the new PR head.

- [ ] **Step 1: Format and check backend code**

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml spotless:check
```

Expected: both commands exit 0.

- [ ] **Step 2: Run full backend verification**

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: exit 0; tests, Spotless, Error Prone, and SpotBugs all pass.

- [ ] **Step 3: Run frontend verification**

```powershell
client\npm.cmd run verify
```

Expected: exit 0.

- [ ] **Step 4: Inspect the final focused diff**

```powershell
git diff origin/master...HEAD -- server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java
```

Confirm manually:

- one Stockfish process only;
- one reader executor only;
- no production reader queue/background loop;
- one retained pending read maximum;
- no `Future.cancel(true)` on read timeout;
- `bestmove` consumed before `isready` during recovery;
- `readyok` used only as final readiness check;
- already-consumed `bestmove` does not trigger another drain;
- recovery failure still marks engine unusable;
- no change to evaluation thresholds, move strength, frontend, persistence, or AI code.

- [ ] **Step 5: Correct PR description wording**

Use this exact concept in the review-fix summary:

```text
Recover a failed in-flight Stockfish search with stop -> drain through bestmove -> isready -> readyok before reusing the shared UCI stream. Retain the timed-out read future so no orphaned reader can consume the stopped search's bestmove.
```

Do not describe `readyok` itself as the search-completion barrier.

- [ ] **Step 6: Push**

```powershell
git push
```

- [ ] **Step 7: Require fresh CI for the new head**

Do not reuse results from commit `4690f6b94c4308880971f34bd0c4e4ab6058d025`.

Require successful new-head checks for:

- Backend verification
- Native image verification
- any other required repository checks

- [ ] **Step 8: Add the final Luna handoff comment only after CI is actually green**

```text
Addressed the second UCI recovery review finding.

- Timed-out readLine future is retained and reused; it is no longer cancelled/orphaned.
- Recovery waits for the stopped search's bestmove before sending isready.
- readyok is used only as the final engine-readiness check.
- Failures after bestmove was already consumed do not attempt a second stop/drain cycle.
- Deterministic fake-process regression allows readyok to race ahead of delayed bestmove and models an interrupt-insensitive process-pipe read.
- Full backend/frontend verification completed and fresh CI is green.

Ready for re-review.
```

Do not claim fresh CI is green until GitHub reports it on the pushed implementation head.

---

## Final Acceptance Checklist

- [ ] A timed-out `reader.readLine()` future is retained instead of cancelled/discarded.
- [ ] At most one outstanding `reader.readLine()` exists at any time.
- [ ] Recovery sends `stop` and consumes the stopped search's `bestmove` before sending `isready`.
- [ ] Recovery consumes `readyok` only after `bestmove` completed the stopped search.
- [ ] A delayed stale evaluation `bestmove` cannot become the next real move-selection result.
- [ ] A failure occurring after `bestmove` was already consumed does not wait for another `bestmove`.
- [ ] Recovery failure marks the engine unusable and later commands fail before writing to the dirty stream.
- [ ] `bestMove()` and `evaluate()` share the same recovery invariant.
- [ ] Existing stop/resume and issue #39 evaluation tests remain green.
- [ ] Full backend verification passes.
- [ ] Frontend verification passes.
- [ ] Fresh PR CI for the implementation head passes, including native-image verification.
- [ ] No unrelated architecture or feature work is introduced.
