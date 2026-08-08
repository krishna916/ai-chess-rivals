# PR #51 UCI Bestmove Barrier Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make failed Stockfish search recovery correct under the actual UCI protocol by retaining ownership of a timed-out read, draining the stopped search through its mandatory `bestmove`, and only then performing `isready` / `readyok` before engine reuse.

**Architecture:** Keep the existing single Stockfish process and single reader executor. Replace the current “cancel-and-forget” timed-out `readLine()` behavior with one retained package-internal pending read future so exactly one thread owns stdout at a time. A failed in-flight search recovers in this exact order: `stop` -> drain through that search's `bestmove` -> `isready` -> `readyok`; if any recovery step fails, keep the existing fail-closed `usable=false` behavior. Do not add another engine process, restart orchestration, a queue-based reader framework, or a new public API.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Stockfish 17.1/UCI, JUnit 5, AssertJ, existing Maven/Spotless/Error Prone/SpotBugs verification.

## Source of Truth

- PR: `#51 feat: add Stockfish evaluation swing analysis`
- Issue: `#39 Phase 2: Add lightweight Stockfish evaluation swing analysis`
- Original implementation plan: `docs/superpowers/plans/2026-08-08-stockfish-evaluation-swing-analysis.md`
- First review-fix plan: `docs/superpowers/plans/2026-08-08-pr51-uci-search-recovery.md`
- Latest review finding: `readyok` is not a search-completion barrier; UCI allows `isready` to be answered while a search is still running. The stopped search's mandatory `bestmove` is the completion barrier that must be consumed before `readyok` is used as the final readiness check.

## Global Constraints

- Keep exactly one Stockfish process.
- Keep the existing single-threaded `lineReaderExecutor`; do not introduce another executor, background reader loop, reactive stream, queue, or scheduler.
- There must never be more than one outstanding `reader.readLine()` operation.
- A timeout must **not** cancel and discard the outstanding read future. That future remains owned by `StockfishEngine` and is reused by recovery.
- Recovery order for an actually in-flight failed search is exactly: `stop` -> consume lines until that search's `bestmove` -> `isready` -> consume lines until `readyok`.
- `readyok` alone must never be treated as proof that a stopped search completed.
- If `bestmove` was already consumed before the failure (for example evaluation ends with `bestmove` but no score), do **not** send `stop` and do not wait for another `bestmove`.
- If recovery cannot consume `bestmove` and then `readyok` within the existing bounded timeout, keep the current behavior that marks the engine unusable and prevents later commands.
- Apply the same search-completion invariant to both `bestMove()` and `evaluate()`.
- Preserve issue #39 behavior unchanged: evaluation remains best-effort, mover-perspective normalization and thresholds do not change, legal committed moves are not rolled back, baseline reuse still requires exact FEN + ply.
- Preserve the stop-during-post-move-evaluation lifecycle test already added in the previous review fix.
- Do not modify `StockfishClient`, `ChessEvaluationService`, `StockfishEvaluationService`, match event payloads, WebSocket schema, frontend, persistence, Spring AI code, or evaluation configuration defaults.
- No new runtime dependencies.
- Run Spotless before full verification.

## File Map

**Modify only:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java` — retain one pending read future, distinguish “search started” from “bestmove consumed”, and recover through `bestmove` before `readyok`.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java` — replace the optimistic fake ordering with a deterministic asynchronous fake that can emit `readyok` before the delayed stopped-search `bestmove`, and emulate a process pipe whose blocked read ignores thread interruption.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java` — `stop()` already exists.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/StockfishClient.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java` unless compilation forces a mechanical fixture update; no behavioral redesign is required there.
- `client/**`
- persistence/Flyway files
- `ai/**`

---

### Task 1: Reproduce the Real UCI Ordering Bug

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java`

**Interfaces:**
- Consumes: current package-private `StockfishEngine(Process, ChessProperties.Stockfish)` constructor.
- Produces: deterministic protocol regression proving recovery does not declare success on an early `readyok` and does not orphan the timed-out read.

- [ ] **Step 1: Replace the current synchronous stale-bestmove fake behavior**

The current fake writes `bestmove a2a3` directly inside the `stop` command handler. Replace that behavior because it accidentally guarantees `bestmove` precedes `readyok`, which real UCI does not guarantee.

Add a trace list to `ScriptedUciProcess`:

```java
private final List<String> trace = new CopyOnWriteArrayList<>();
```

Expose it:

```java
List<String> trace() {
  return List.copyOf(trace);
}
```

Whenever the fake receives a command, record it before the switch:

```java
commands.add(command);
trace.add("IN " + command);
```

Replace direct output writes with an instance helper that also records output:

```java
private synchronized void emitLine(BufferedWriter writer, String line) throws IOException {
  trace.add("OUT " + line);
  writer.write(line);
  writer.newLine();
  writer.flush();
}
```

For multiple handshake lines, call `emitLine(...)` once per line.

- [ ] **Step 2: Make stopped-search bestmove asynchronous and later than an early readyok**

In the recoverable fake, change the `stop` case to schedule the stale evaluation result asynchronously rather than writing it immediately:

```java
case "stop" -> {
  if (recoverable) {
    Thread delayedBestmove =
        new Thread(
            () -> {
              try {
                Thread.sleep(75);
                emitLine(writer, "bestmove a2a3");
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              } catch (IOException exception) {
                alive = false;
              }
            },
            "scripted-delayed-stale-bestmove");
    delayedBestmove.setDaemon(true);
    delayedBestmove.start();
  }
}
```

Keep `isready` immediate:

```java
case "isready" -> {
  if (recoverable || commands.stream().noneMatch("go depth 8 movetime 1"::equals)) {
    emitLine(writer, "readyok");
  } else {
    closeEngineOutput();
  }
}
```

For the real move search, delay the valid move long enough that a stale `bestmove` would win the race if recovery incorrectly returned on `readyok`:

```java
case "go movetime 10" -> {
  Thread validBestmove =
      new Thread(
          () -> {
            try {
              Thread.sleep(150);
              emitLine(writer, "bestmove e2e4");
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            } catch (IOException exception) {
              alive = false;
            }
          },
          "scripted-delayed-valid-bestmove");
  validBestmove.setDaemon(true);
  validBestmove.start();
}
```

Do not add sleeps to production code.

- [ ] **Step 3: Make fake stdout ignore reader-thread interruption**

The production bug also depends on the fact that cancelling a Java future does not reliably abort an OS process-pipe read. The regression fake must model that explicitly instead of relying on `PipedInputStream` interrupt behavior.

Create this nested test-only input stream inside `StockfishEngineRecoveryTest`:

```java
private static final class InterruptIgnoringInputStream extends InputStream {

  private final java.util.concurrent.BlockingQueue<Integer> bytes =
      new java.util.concurrent.LinkedBlockingQueue<>();
  private volatile boolean closed;

  void emitLine(String line) {
    byte[] encoded = (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
        // Deliberately ignore interruption to model a blocking process-pipe read that
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

Replace the fake process's `clientReads = new PipedInputStream()` / `engineWrites` pair with:

```java
private final InterruptIgnoringInputStream clientReads = new InterruptIgnoringInputStream();
```

The fake engine should emit stdout through `clientReads.emitLine(...)`; keep the existing pipe used for commands sent from `StockfishEngine` to the fake process.

Update `getInputStream()` to return `clientReads`.

This is test-only code. Do not copy this stream implementation into production.

- [ ] **Step 4: Strengthen the main regression assertion**

Keep the behavioral assertion:

```java
assertThat(move).isEqualTo("e2e4");
```

Replace the old command-only subsequence assertion with this protocol ordering assertion:

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

The essential invariant is that **the stale stopped-search `bestmove` must be consumed before recovery sends `isready`**.

- [ ] **Step 5: Add a completed-search failure regression**

Add a third fake mode where evaluation emits `bestmove a2a3` without any preceding `info ... score ...` line. `evaluate()` must throw because there is no score, but recovery must **not** send `stop` because that search's `bestmove` has already been consumed.

Add test:

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

Implement `bestmoveWithoutScore()` so the evaluation `go depth 8 movetime 10` emits only:

```text
bestmove a2a3
```

and the later `go movetime 10` emits `bestmove e2e4`.

- [ ] **Step 6: Run only the protocol regression and verify RED**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest test
```

Expected before production changes: at least the delayed-order/pending-read regression fails. The current `readyok`-first recovery is not allowed to satisfy the strengthened test.

Do not proceed until the test genuinely demonstrates the bug.

---

### Task 2: Retain One Pending Read and Recover Through Bestmove

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java`

**Interfaces:**
- Produces: field `private Future<String> pendingRead;`.
- Produces: invariant that `readLineWithTimeout(...)` reuses the same outstanding `reader.readLine()` future after timeout instead of cancelling it.
- Produces: recovery sequence `stop -> waitForToken("bestmove") -> isready -> waitForToken("readyok")`.
- Produces: local search-state distinction between `searchStarted` and `bestmoveReceived` in both search methods.

- [ ] **Step 1: Add one retained pending-read field**

Add beside `lineReaderExecutor`:

```java
private Future<String> pendingRead;
```

Do not use `AtomicReference`; the class contract already states UCI communication is serialized and not safe for concurrent callers.

- [ ] **Step 2: Refactor `readLineWithTimeout` to reuse, not cancel, a timed-out read**

Replace the local future creation in:

```java
private String readLineWithTimeout(long timeout, TimeUnit timeUnit, String waitingFor)
```

with this behavior:

```java
if (pendingRead == null) {
  pendingRead = lineReaderExecutor.submit(reader::readLine);
}
Future<String> future = pendingRead;
```

Use this exact completion/exception ownership pattern:

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

Critical rules:

- Remove `future.cancel(true)` from the timeout branch.
- Do **not** clear `pendingRead` on timeout.
- Do **not** clear `pendingRead` on caller interruption; recovery will fail closed because the interrupt flag remains set.
- Clear `pendingRead` only when that exact future completed normally or completed exceptionally.

This guarantees no second `reader.readLine()` task can be submitted while the timed-out one still owns stdout.

- [ ] **Step 3: Track whether bestmove was already consumed in `bestMove()`**

Change the method-local state to:

```java
boolean searchStarted = false;
boolean bestmoveReceived = false;
```

Immediately after this call succeeds:

```java
UciResponse response = waitForToken("bestmove", deadlineSeconds);
```

set:

```java
bestmoveReceived = true;
```

Then perform `extractBestMove()` as today.

In both `StockfishException` and wrapped-`IOException` catch blocks, recover only when:

```java
if (searchStarted && !bestmoveReceived) {
  recoverAfterSearchFailure(failure);
}
```

If `bestmove` was already consumed but malformed, throw the original failure without sending `stop` or waiting for a nonexistent second `bestmove`.

- [ ] **Step 4: Track whether bestmove was already consumed in `evaluate()`**

Add:

```java
boolean bestmoveReceived = false;
```

When processing a line that starts with `bestmove`, set the flag **before** validating whether `latestScore` exists:

```java
if (response.startsWith("bestmove")) {
  bestmoveReceived = true;
  if (latestScore == null) {
    throw new StockfishException("Stockfish returned bestmove without an evaluation score");
  }
  return latestScore;
}
```

Use the same catch condition as `bestMove()`:

```java
if (searchStarted && !bestmoveReceived) {
  recoverAfterSearchFailure(failure);
}
```

This prevents unnecessary recovery after a completed search whose payload is invalid.

- [ ] **Step 5: Make `bestmove` the recovery completion barrier**

Replace the current recovery body:

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

Keep the existing catch block unchanged in principle:

```java
} catch (IOException | RuntimeException recoveryFailure) {
  usable.set(false);
  failure.addSuppressed(recoveryFailure);
  log.error("Stockfish search recovery failed; engine marked unusable", recoveryFailure);
}
```

Do not swallow or replace the original search failure. Successful recovery makes the engine safe to reuse but the original operation still failed and must still throw.

- [ ] **Step 6: Run protocol tests and verify GREEN**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest test
```

Expected: all recovery tests pass, including:

- delayed stale `bestmove` is drained before `isready`;
- next real move returns `e2e4`, never stale `a2a3`;
- interrupted/failed recovery leaves engine unusable;
- completed evaluation with missing score does not send `stop` or wait for another `bestmove`.

- [ ] **Step 7: Run the surrounding chess tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineTest,StockfishEngineRecoveryTest,StockfishClientIntegrationTest,StockfishEvaluationServiceTest,UciResponseTest,MatchEngineTest test
```

Expected: all selected tests pass with zero failures/errors.

- [ ] **Step 8: Commit the production fix**

```powershell
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java
git commit -m "fix: drain stopped Stockfish search before reuse"
```

---

### Task 3: Full Verification and PR Handoff

**Files:**
- No production design changes expected.
- Update PR description only if the existing “Review fixes” wording still says `stop -> isready -> readyok`; it must say `stop -> bestmove -> isready -> readyok` after this fix.

**Interfaces:**
- Produces: fresh verification evidence for PR #51 head.

- [ ] **Step 1: Format backend code**

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml spotless:check
```

Expected: both commands exit 0.

- [ ] **Step 2: Run full backend verification**

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: exit 0; all backend tests, Spotless, Error Prone, and SpotBugs checks pass.

- [ ] **Step 3: Run frontend verification even though no client code changed**

```powershell
client\npm.cmd run verify
```

Expected: exit 0.

- [ ] **Step 4: Review the final diff for scope creep**

Run:

```powershell
git diff origin/master...HEAD -- server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java
```

Confirm all of the following manually:

- no second Stockfish process;
- no new runtime dependency;
- no new reader executor/queue/background loop;
- no change to evaluation thresholds or move-selection strength;
- no frontend/persistence/AI changes;
- timeout leaves exactly one retained read future;
- stopped search `bestmove` is consumed before `isready`;
- engine still fails closed if recovery cannot complete;
- malformed already-completed search does not trigger a second recovery search.

- [ ] **Step 5: Update PR description wording if necessary**

The recovery summary must say exactly this concept:

```text
Recover a failed in-flight Stockfish search with stop -> drain through bestmove -> isready -> readyok before reusing the shared UCI stream. Retain the timed-out read future so no orphaned reader can consume the stopped search's bestmove.
```

Do not claim `readyok` itself is the search-completion barrier.

- [ ] **Step 6: Push and wait for fresh PR CI**

```powershell
git push
```

Require fresh success on the new head for:

- Backend verification
- Native image verification
- any required repository status checks

Do not reuse CI results from commit `4690f6b94c4308880971f34bd0c4e4ab6058d025`.

- [ ] **Step 7: Final Luna handoff comment**

Add a concise PR comment containing:

```text
Addressed the second UCI recovery review finding.

- Timed-out readLine future is retained and reused; it is no longer cancelled/orphaned.
- Recovery now waits for the stopped search's bestmove before sending isready.
- readyok is used only as the final engine-readiness check.
- Failures after bestmove was already consumed do not attempt a second stop/drain cycle.
- Deterministic fake-process regression now allows readyok to race ahead of delayed bestmove and models an interrupt-insensitive process-pipe read.
- Full backend/frontend verification completed and fresh CI is green.

Ready for re-review.
```

Only say “fresh CI is green” after GitHub actually reports the new-head checks successful.

---

## Final Acceptance Checklist

Before asking for re-review, every item must be true:

- [ ] A timed-out `reader.readLine()` future is retained instead of cancelled/discarded.
- [ ] At most one outstanding `reader.readLine()` exists at any time.
- [ ] Recovery sends `stop` and consumes the stopped search's `bestmove` before sending `isready`.
- [ ] Recovery consumes `readyok` only after `bestmove` has completed the stopped search.
- [ ] A delayed stale evaluation `bestmove` can never become the next real move-selection result.
- [ ] A failure that occurs after `bestmove` was already consumed does not attempt to wait for another `bestmove`.
- [ ] Recovery failure marks the engine unusable and later commands fail before writing to the dirty stream.
- [ ] `bestMove()` and `evaluate()` share the same recovery invariant.
- [ ] Existing stop/resume and issue #39 evaluation tests remain green.
- [ ] Full backend verification passes.
- [ ] Frontend verification passes.
- [ ] Fresh PR CI for the new head passes, including native-image verification.
- [ ] No unrelated architecture or feature work was introduced.
