# PR #51 UCI Search Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Stockfish evaluation failure truly non-fatal by guaranteeing that a timed-out/failed UCI search cannot leave stale `bestmove` output for the next search, while preserving the existing stop/resume behavior and issue #39 evaluation semantics.

**Architecture:** Keep the existing single Stockfish process. Recover the UCI protocol inside `StockfishEngine` whenever a started search fails: send `stop`, then `isready`, and drain output until the corresponding `readyok`, which necessarily comes after any `bestmove` produced by the stopped search. If that recovery itself fails, mark the engine unusable and fail closed on later commands rather than ever returning a stale move. Do not add another Stockfish process, restart framework, executor, queue, or new public API.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Stockfish 17.1/UCI, JUnit 5, AssertJ, existing Maven/Spotless/Error Prone/SpotBugs verification.

## Source of Truth

- PR: `#51 feat: add Stockfish evaluation swing analysis`
- Issue: `#39 Phase 2: Add lightweight Stockfish evaluation swing analysis`
- Original implementation plan: `docs/superpowers/plans/2026-08-08-stockfish-evaluation-swing-analysis.md`
- Review finding on PR #51: evaluation timeout can leave late UCI output that a later `bestMove()` call may consume as its own result.
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Keep one Stockfish process. Do not create a second engine just for evaluation.
- Keep evaluation best-effort: a recoverable evaluation failure must still produce the already-committed move with `Optional.empty()` evaluation.
- Never allow a stale `bestmove` from a failed evaluation/search to satisfy a later move-selection search.
- UCI recovery order is exactly: `stop` -> `isready` -> drain until `readyok`.
- Recovery must happen inside `StockfishEngine`; callers must not know raw UCI recovery details.
- If recovery succeeds, rethrow the original search failure so `MatchEngine.safeEvaluate()` keeps the evaluation unavailable while the engine remains safe to reuse.
- If recovery fails, mark the `StockfishEngine` unusable. Later `newGame`, `setPosition`, `bestMove`, and `evaluate` calls must fail before writing another command.
- Do not silently restart Stockfish in this PR. Restart orchestration adds complexity and is unnecessary for issue #39.
- Apply the same search-recovery protection to `bestMove()` as well as `evaluate()`. A failed move search can otherwise poison a later stop/resume attempt in the same way.
- Preserve mover-perspective normalization, mate bounding, thresholds, baseline FEN+ply checks, WebSocket schema, and frontend behavior unchanged.
- Do not modify `client/**`, persistence, Spring AI provider code, or evaluation configuration defaults.
- Add a deterministic protocol-level regression test; a fake `ChessEvaluationService` test alone is insufficient because the bug is in the shared UCI byte stream.
- Run Spotless before backend verification.

## File Map

**Modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java` — add the UCI `stop` command.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java` — add protocol recovery, unhealthy-state guard, and a package-private process-injection constructor used only by deterministic protocol tests.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineTest.java` — add shared property helper adjustments only if constructor extraction requires them; keep existing executable-path tests intact.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java` — add stop-during-post-move-evaluation lifecycle coverage.

**Create:**

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java` — deterministic fake-process tests proving stale `bestmove` is drained and unrecoverable engines fail closed.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/StockfishClient.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MovePlayed.java`
- `server/src/main/resources/application.yaml`
- `server/.env.example`
- `client/**`
- Flyway/persistence files
- `ai/**`

---

### Task 1: Reproduce and Fix Stale UCI Search Output

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java`

**Interfaces:**
- Consumes: existing `StockfishEngine.evaluate(int, Duration)`, `bestMove(Duration)`, `setPosition(String)`, `waitForToken(...)`, and the single-threaded `lineReaderExecutor`.
- Produces: `UciCommand.stop()`.
- Produces: package-private `StockfishEngine(Process process, ChessProperties.Stockfish config)` for protocol tests only.
- Produces: private `recoverAfterSearchFailure(StockfishException failure)` and `ensureUsable()` inside `StockfishEngine`.
- Produces: engine invariant: after a failed started search, either the UCI stream is synchronized through `readyok` or the engine is permanently marked unusable.

- [ ] **Step 1: Add a deterministic fake UCI process test harness**

Create `StockfishEngineRecoveryTest.java`. Use an in-JVM `Process` implementation backed by piped streams so the test is platform-independent and does not need the real Stockfish binary.

Use this structure:

```java
package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StockfishEngineRecoveryTest {

  @Test
  void timedOutEvaluationDoesNotLeakItsBestMoveIntoNextMoveSearch() {
    try (ScriptedUciProcess process = ScriptedUciProcess.recoverable()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

      engine.setPosition("startpos");
      assertThatThrownBy(() -> engine.evaluate(8, Duration.ofMillis(1)))
          .isInstanceOf(StockfishException.class);

      engine.setPosition("startpos");
      String move = engine.bestMove(Duration.ofMillis(10));

      assertThat(move).isEqualTo("e2e4");
      assertThat(process.commands())
          .containsSubsequence(
              "go depth 8 movetime 1",
              "stop",
              "isready",
              "position startpos",
              "go movetime 10");
      engine.close();
    }
  }

  @Test
  void failedRecoveryMarksEngineUnusableInsteadOfAllowingAnotherSearch() {
    try (ScriptedUciProcess process = ScriptedUciProcess.unrecoverable()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

      engine.setPosition("startpos");
      assertThatThrownBy(() -> engine.evaluate(8, Duration.ofMillis(1)))
          .isInstanceOf(StockfishException.class);

      int commandsAfterFailure = process.commands().size();
      assertThatThrownBy(() -> engine.setPosition("startpos"))
          .isInstanceOf(StockfishException.class)
          .hasMessageContaining("not usable after failed search recovery");
      assertThat(process.commands()).hasSize(commandsAfterFailure);
      engine.close();
    }
  }

  private static ChessProperties.Stockfish stockfishConfig() {
    return new ChessProperties.Stockfish(
        "unused-for-injected-process",
        1,
        16,
        1,
        1,
        new ChessProperties.Stockfish.Evaluation(8, 50, 200, 200));
  }

  private static final class ScriptedUciProcess extends Process implements AutoCloseable {

    private final PipedInputStream clientReads = new PipedInputStream();
    private final PipedOutputStream engineWrites;
    private final PipedInputStream engineReads = new PipedInputStream();
    private final PipedOutputStream clientWrites;
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private final boolean recoverable;
    private final Thread engineThread;
    private volatile boolean alive = true;

    private ScriptedUciProcess(boolean recoverable) {
      try {
        engineWrites = new PipedOutputStream(clientReads);
        clientWrites = new PipedOutputStream(engineReads);
      } catch (IOException exception) {
        throw new IllegalStateException(exception);
      }
      engineThread = new Thread(this::runEngine, "scripted-uci-engine");
      engineThread.setDaemon(true);
      engineThread.start();
    }

    static ScriptedUciProcess recoverable() {
      return new ScriptedUciProcess(true);
    }

    static ScriptedUciProcess unrecoverable() {
      return new ScriptedUciProcess(false);
    }

    List<String> commands() {
      return List.copyOf(commands);
    }

    private void runEngine() {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(engineReads));
          BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(engineWrites))) {
        String command;
        while (alive && (command = reader.readLine()) != null) {
          commands.add(command);
          switch (command) {
            case "uci" -> writeLine(writer, "id name ScriptedStockfish", "uciok");
            case "isready" -> {
              if (recoverable || commands.stream().noneMatch("go depth 8 movetime 1"::equals)) {
                writeLine(writer, "readyok");
              } else {
                engineWrites.close();
                alive = false;
              }
            }
            case "stop" -> {
              if (recoverable) {
                // This is intentionally stale output from the timed-out evaluation.
                writeLine(writer, "bestmove a2a3");
              }
            }
            case "go movetime 10" -> writeLine(writer, "bestmove e2e4");
            case "quit" -> alive = false;
            default -> {
              // position/setoption/ucinewgame are accepted without direct output.
              // The evaluation go command intentionally emits nothing until recovery sends stop.
            }
          }
        }
      } catch (IOException ignored) {
        alive = false;
      }
    }

    private static void writeLine(BufferedWriter writer, String... lines) throws IOException {
      for (String line : lines) {
        writer.write(line);
        writer.newLine();
      }
      writer.flush();
    }

    @Override
    public OutputStream getOutputStream() {
      return clientWrites;
    }

    @Override
    public InputStream getInputStream() {
      return clientReads;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
      engineThread.join();
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      engineThread.join(unit.toMillis(timeout));
      return !engineThread.isAlive();
    }

    @Override
    public int exitValue() {
      if (alive) {
        throw new IllegalThreadStateException("process still alive");
      }
      return 0;
    }

    @Override
    public void destroy() {
      close();
    }

    @Override
    public boolean isAlive() {
      return alive;
    }

    @Override
    public void close() {
      alive = false;
      try {
        clientWrites.close();
        clientReads.close();
        engineReads.close();
        engineWrites.close();
      } catch (IOException ignored) {
        // Test resource cleanup only.
      }
    }
  }
}
```

Important behavior of the fake process:

- The first evaluation `go depth 8 movetime 1` intentionally produces no response, forcing the application-side timeout.
- In the recoverable case, `stop` releases a deliberately stale `bestmove a2a3`; the following `isready` emits `readyok`.
- The next real move search emits `bestmove e2e4`.
- The regression passes only if recovery drains/discards `a2a3` before `bestMove()` begins.
- In the unrecoverable case, the stream closes during recovery so the engine must become unusable.

- [ ] **Step 2: Run the new test and verify it fails before production changes**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest test
```

Expected: test compilation fails because the injected-process constructor does not exist yet and runtime behavior does not yet implement UCI recovery.

- [ ] **Step 3: Add the UCI `stop` command**

In `UciCommand.java`, add beside `isReady()` / `quit()`:

```java
/** Stops the current search. Stockfish responds with the search's final bestmove. */
static UciCommand stop() {
  return new UciCommand("stop");
}
```

Do not expose `stop` through `StockfishClient`; it is protocol recovery detail only.

- [ ] **Step 4: Add a package-private injected-process constructor without changing the public bean contract**

In `StockfishEngine`, add a package-private constructor used only by package tests:

```java
StockfishEngine(Process process, ChessProperties.Stockfish config) {
  this.process = Objects.requireNonNull(process, "process must not be null");
  Objects.requireNonNull(config, "config must not be null");
  this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
  this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
  this.startupTimeoutSeconds = config.startupTimeoutSeconds();
  this.moveTimeoutSeconds = config.moveTimeoutSeconds();

  try {
    performUciHandshake();
    configureOptions(config);
    waitForReady(startupTimeoutSeconds);
  } catch (IOException exception) {
    throw new StockfishException("Failed to initialize injected Stockfish process", exception);
  }
}
```

Add `import java.util.Objects;` if it is not already present.

Do not change `StockfishConfiguration`; production must continue constructing the engine from `ChessProperties` exactly as before.

- [ ] **Step 5: Add fail-closed engine state**

Add this field beside `closed`:

```java
private final AtomicBoolean usable = new AtomicBoolean(true);
```

Add:

```java
private void ensureUsable() {
  if (!usable.get()) {
    throw new StockfishException(
        "Stockfish engine is not usable after failed search recovery");
  }
}
```

Call `ensureUsable()` as the first statement in these four methods:

```java
newGame()
setPosition(String fen)
bestMove(Duration thinkTime)
evaluate(int depth, Duration moveTime)
```

Do not call it from `close()`; closing an unhealthy engine must remain safe and idempotent.

- [ ] **Step 6: Add protocol recovery that drains through `readyok`**

Add this helper inside `StockfishEngine`:

```java
private void recoverAfterSearchFailure(StockfishException failure) {
  try {
    sendCommand(UciCommand.stop());
    sendCommand(UciCommand.isReady());
    waitForToken("readyok", moveTimeoutSeconds);
  } catch (IOException | RuntimeException recoveryFailure) {
    usable.set(false);
    failure.addSuppressed(recoveryFailure);
    log.error("Stockfish search recovery failed; engine marked unusable", recoveryFailure);
  }
}
```

Why this exact order matters:

1. `stop` forces the active search to finish and emit its `bestmove`.
2. `isready` is queued after `stop` in the same UCI command stream.
3. Waiting until `readyok` drains any late `info`/`bestmove` lines from the failed search.
4. Only after `readyok` is observed is the shared output stream safe for another search.

Do not merely cancel another reader future. The stale-output bug exists precisely because cancelling a `Future<readLine()>` does not prove the underlying UCI bytes were drained.

- [ ] **Step 7: Recover a failed evaluation search before rethrowing it**

Restructure the body of `evaluate(...)` so recovery runs only after the `go` command has successfully started a search:

```java
boolean searchStarted = false;
try {
  sendCommand(UciCommand.evaluate(depth, moveTime.toMillis()));
  searchStarted = true;

  // Keep the existing single-deadline evaluation loop unchanged here.
  // It must still return the latest parsed score when bestmove arrives normally.

} catch (StockfishException failure) {
  if (searchStarted) {
    recoverAfterSearchFailure(failure);
  }
  throw failure;
} catch (IOException exception) {
  StockfishException failure =
      new StockfishException("Failed to evaluate position with Stockfish", exception);
  if (searchStarted) {
    recoverAfterSearchFailure(failure);
  }
  throw failure;
}
```

Keep validation (`depth > 0`, positive `moveTime`) before `searchStarted` logic so invalid caller input never sends recovery commands.

If normal evaluation reaches `bestmove` and has a valid score, return normally and do not send `stop`/`isready`.

- [ ] **Step 8: Apply the same invariant to failed real move searches**

Restructure `bestMove(...)` using the same `searchStarted` pattern:

```java
boolean searchStarted = false;
try {
  sendCommand(UciCommand.go(thinkTime.toMillis()));
  searchStarted = true;

  long safetyMarginSeconds = moveTimeoutSeconds;
  long deadlineSeconds = thinkTime.toSeconds() + safetyMarginSeconds;
  UciResponse response = waitForToken("bestmove", deadlineSeconds);
  return response
      .extractBestMove()
      .orElseThrow(
          () -> new StockfishException("Unexpected bestmove response: " + response.raw()));
} catch (StockfishException failure) {
  if (searchStarted) {
    recoverAfterSearchFailure(failure);
  }
  throw failure;
} catch (IOException exception) {
  StockfishException failure =
      new StockfishException("Failed to obtain best move from Stockfish", exception);
  if (searchStarted) {
    recoverAfterSearchFailure(failure);
  }
  throw failure;
}
```

This is intentionally a small duplication rather than introducing a generic search framework. The project favors explicitness and this is only two search methods.

- [ ] **Step 9: Run the protocol regression tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest,UciCommandTest,UciResponseTest,StockfishClientIntegrationTest test
```

Expected:

- `timedOutEvaluationDoesNotLeakItsBestMoveIntoNextMoveSearch` passes and returns `e2e4`, never stale `a2a3`.
- `failedRecoveryMarksEngineUnusableInsteadOfAllowingAnotherSearch` passes.
- Existing UCI command/parser tests remain green.
- Real Stockfish integration remains green.

- [ ] **Step 10: Commit the UCI recovery slice**

```powershell
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java `
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java `
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java `
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineTest.java
git commit -m "fix: recover stockfish stream after failed search"
```

If `StockfishEngineTest.java` did not require a change, omit it from `git add`; do not make a cosmetic edit just to match the plan.

---

### Task 2: Lock Down Stop During Post-Move Evaluation

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`

**Interfaces:**
- Consumes: existing `FakeChessEvaluationService`, `MatchEngine.stopCurrentMatch()`, evaluation baseline reuse, and the accepted behavior that an in-flight turn may finish after stop is requested.
- Produces: regression coverage proving a stop fired specifically during post-move evaluation cannot duplicate a ply or attach the completed evaluation baseline to a different FEN/ply after resume.

- [ ] **Step 1: Add an evaluation callback to the existing fake**

Inside `FakeChessEvaluationService`, add:

```java
private Runnable onEvaluate = () -> {};

private FakeChessEvaluationService onEvaluate(Runnable callback) {
  onEvaluate = callback;
  return this;
}
```

At the start of `evaluate(String fen)`, after recording the FEN but before returning/failing, invoke:

```java
onEvaluate.run();
```

Do not add another fake service class.

- [ ] **Step 2: Write the stop-during-post-move-evaluation regression**

Add this test next to the existing stop/resume evaluation test:

```java
@Test
void stopDuringPostMoveEvaluationKeepsCommittedPlyAndResumeBaselineConsistent() {
  FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
  RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
  FakeChessEvaluationService evaluationService =
      new FakeChessEvaluationService().withEvaluations(cp(0), cp(-20), cp(-10));

  MatchEngine[] engine = new MatchEngine[1];
  int[] evaluationCalls = {0};
  evaluationService.onEvaluate(
      () -> {
        evaluationCalls[0]++;
        if (evaluationCalls[0] == 2) {
          engine[0].stopCurrentMatch();
        }
      });

  engine[0] =
      matchEngine(chessPlayer, 250, 2, NO_OP_PACING, eventSink, evaluationService);

  Match stoppedMatch = engine[0].playUntilFinished();
  Match resumedMatch = engine[0].playUntilFinished();

  assertEquals(1, stoppedMatch.moveCount());
  assertEquals("e2e4", stoppedMatch.moves().getFirst().notation().value());
  assertTrue(resumedMatch.isFinished());
  assertEquals(2, resumedMatch.moveCount());
  assertEquals("e7e5", resumedMatch.moves().get(1).notation().value());

  List<MovePlayed> moveEvents =
      eventSink.events.stream()
          .filter(MovePlayed.class::isInstance)
          .map(MovePlayed.class::cast)
          .toList();
  assertEquals(List.of(1, 2), moveEvents.stream().map(MovePlayed::ply).toList());
  assertEquals(3, evaluationService.fens.size());

  EvaluationSwing resumedSwing = moveEvents.get(1).evaluation().orElseThrow();
  assertEquals(-20L, resumedSwing.beforeCentipawns());
  assertEquals(10L, resumedSwing.afterCentipawns());
  assertEquals(30L, resumedSwing.swingCentipawns());
}
```

This test intentionally does **not** require changing the existing event ordering contract. The accepted Phase 1 behavior allows the in-flight turn to finish after stop is requested. The assertion is about correctness of committed state, unique ply publication, and safe baseline reuse.

- [ ] **Step 3: Run focused match lifecycle tests**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchEngineTest test
```

Expected: all `MatchEngineTest` cases pass, including the new stop-during-evaluation test and the existing stop/resume tests.

- [ ] **Step 4: Commit lifecycle hardening**

```powershell
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java
git commit -m "test: cover stop during stockfish evaluation"
```

---

### Task 3: Format, Verify, and Update PR Evidence

**Files:**
- No required production files beyond Tasks 1-2.
- Update the PR description only after verification succeeds.

**Interfaces:**
- Consumes: all changes from Tasks 1-2.
- Produces: formatted code, passing backend verification, passing frontend verification, and fresh CI evidence for re-review.

- [ ] **Step 1: Apply backend formatting**

Run from repository root:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
```

Expected: command exits `0`.

- [ ] **Step 2: Re-run the focused review-fix suite after formatting**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishEngineRecoveryTest,StockfishEngineTest,UciCommandTest,UciResponseTest,StockfishClientIntegrationTest,StockfishEvaluationServiceTest,MatchEngineTest test
```

Expected: `BUILD SUCCESS` with zero failures/errors.

- [ ] **Step 3: Run full backend verification**

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: `BUILD SUCCESS`; Spotless, compilation/Error Prone, all tests, Spring Modulith verification, and SpotBugs pass.

- [ ] **Step 4: Run frontend verification even though no client files changed**

From repository root:

```powershell
cd client
npm.cmd run verify
cd ..
```

Expected: formatting, type checking, linting, tests, and production build all succeed.

- [ ] **Step 5: Inspect the final diff for scope control**

```powershell
git status --short
git diff --stat origin/master...HEAD
git diff origin/master...HEAD -- server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngineRecoveryTest.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java
```

Expected:

- No client/persistence/AI implementation changes.
- No second Stockfish process in production.
- No change to evaluation thresholds, mover-perspective math, or WebSocket schema.
- Search failure recovery is contained in `StockfishEngine`.

- [ ] **Step 6: Push the review-fix commits to the existing PR branch**

```powershell
git push origin agent/issue-39-stockfish-evaluation-swing
```

Expected: PR #51 updates; GitHub CI reruns backend verification and native-image verification.

- [ ] **Step 7: Update PR #51 verification notes only with fresh evidence**

Append a section like this after the commands above have actually passed:

```markdown
## Review fixes

- Added UCI search recovery (`stop` -> `isready` -> drain through `readyok`) so a timed-out evaluation cannot leak stale `bestmove` output into the next move search.
- Added fail-closed engine state when protocol recovery itself fails.
- Applied the same recovery invariant to failed move searches for safe stop/resume reuse.
- Added deterministic protocol-level stale-output regression coverage.
- Added stop-during-post-move-evaluation lifecycle coverage.

### Verification

- `server\\mvnw.cmd -f server\\pom.xml verify` — passed.
- `client\\npm.cmd run verify` — passed.
- GitHub Actions backend verification — confirm after CI finishes.
- GitHub Actions native image verification — confirm after CI finishes.
```

Do not claim the GitHub Actions items passed until those jobs have actually completed successfully.

---

## Final Acceptance Checklist

Before requesting re-review, verify every item explicitly:

- [ ] A timed-out evaluation can no longer leave stale `bestmove` output for the next `bestMove()` call.
- [ ] Recovery uses `stop` followed by `isready` and drains through `readyok`.
- [ ] Recoverable evaluation failure still leaves the committed move valid and emits `MovePlayed` with empty evaluation.
- [ ] If protocol recovery fails, the engine becomes unusable and later commands fail before writing to the dirty UCI stream.
- [ ] Failed `bestMove()` searches receive the same recovery protection.
- [ ] Normal evaluation and normal move-selection paths do not send unnecessary recovery commands.
- [ ] Existing mover-perspective, mate, threshold, and FEN+ply baseline behavior is unchanged.
- [ ] Stop during post-move evaluation cannot duplicate a ply or reuse the evaluation for a different authoritative position after resume.
- [ ] Full backend verification passes.
- [ ] Frontend verification passes.
- [ ] GitHub backend CI passes after push.
- [ ] GitHub native-image CI passes after push.

## Expected Commit Sequence

Luna should finish with two implementation commits after this plan commit:

```text
fix: recover stockfish stream after failed search
test: cover stop during stockfish evaluation
```

Do not squash during implementation; keeping the protocol fix separate from lifecycle test hardening makes re-review easier.
