# Lightweight Stockfish Evaluation Swing Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add shallow, bounded-cost Stockfish position evaluation after every committed move, normalize before/after scores to the mover's perspective, classify only `STABLE`, `MAJOR_GAIN`, or `MAJOR_MISTAKE`, and expose the optional result on the internal `MovePlayed` event without introducing any AI dependency into chess/game code.

**Architecture:** Extend the existing UCI client with one evaluation operation that captures the latest `info ... score ...` before `bestmove`. Keep score parsing and mate normalization inside the `chess` module behind its existing named `api`; expose one `ChessEvaluationService` that evaluates a FEN and compares two adjacent positions. `MatchEngine` keeps only a tiny FEN/ply-keyed baseline cache so each successful committed position is normally evaluated once: the previous post-move evaluation becomes the next move's before-evaluation. Evaluation is best-effort and never rolls back or invalidates a legal committed move.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Stockfish 17.1 over UCI, JUnit 5, AssertJ, existing Maven/Spotless/Error Prone/SpotBugs verification.

## Source of Truth

- Issue: `#39 Phase 2: Add lightweight Stockfish evaluation swing analysis`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Approved design: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Stockfish remains the only chess evaluator. Do not call an LLM or Spring AI from this issue.
- Do not change move selection strength or `GAME_MOVE_THINK_TIME_MILLIS`.
- Evaluate every committed position with shallow bounded search; do not add deep analysis, PV storage, MultiPV, consumer-chess labels, or UI analysis.
- Normalize the two adjacent evaluations to the **mover's perspective** before calculating the swing. Positive swing always means the mover improved; negative swing always means the mover worsened.
- Classifications are exactly `STABLE`, `MAJOR_GAIN`, and `MAJOR_MISTAKE`.
- Use inclusive threshold boundaries: `swing >= gainThreshold` is `MAJOR_GAIN`; `swing <= -mistakeThreshold` is `MAJOR_MISTAKE`; otherwise `STABLE`.
- Represent mate evaluation with a bounded comparable value of `100_000` centipawns rather than `Integer.MAX_VALUE` or arithmetic on unbounded sentinels.
- For UCI `score mate 0`, treat the side to move as already checkmated, therefore comparable score `-100_000` from that side's perspective.
- Default evaluation settings: depth `8`, movetime `50ms`, major-gain threshold `200cp`, major-mistake threshold `200cp`.
- Evaluation failure must be logged at `WARN` and treated as unavailable; it must not throw out of the turn after the move has been committed.
- The previous post-move evaluation may be reused only when both the cached ply and FEN match the current authoritative match state. Never reuse a score by ply alone.
- Do not persist evaluations in #39. Dialogue persistence belongs to later Phase 2 issues.
- Do not add evaluation fields to WebSocket payloads or frontend TypeScript in #39. The result only needs to be available to the internal game/dialogue event layer for #42.
- Do not add a new dependency, scheduler, executor, queue, database table, retry library, or AI abstraction.
- Run backend formatting before verification.

## File Map

**Create:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/PositionEvaluation.java` — raw Stockfish score for one FEN from the FEN side-to-move perspective plus bounded comparable conversion.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/EvaluationSwing.java` — mover-perspective before/after comparable scores, signed swing, and broad classification.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/EvaluationSwingClassification.java` — exactly `STABLE`, `MAJOR_GAIN`, `MAJOR_MISTAKE`.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/ChessEvaluationService.java` — named-interface boundary consumed by the game module.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationService.java` — package-private implementation using existing `StockfishClient` and `ChessProperties`.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/UciResponseTest.java` — exact score parsing coverage.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationServiceTest.java` — mover normalization, mate handling, and threshold-boundary tests with a fake client.

**Modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/StockfishClient.java` — add bounded evaluation operation for the currently configured position.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java` — add evaluation `go` command with depth + movetime.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciResponse.java` — parse `score cp` and `score mate` tokens.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java` — collect the latest score until `bestmove`.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishConfiguration.java` — register the evaluation service bean alongside the existing client.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java` — add validated nested evaluation settings.
- `server/src/main/resources/application.yaml` — add environment-backed evaluation defaults.
- `server/.env.example` — document the four evaluation variables.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessPropertiesValidationTest.java` — update constructors and validate evaluation limits.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishClientIntegrationTest.java` — prove the real engine emits a usable bounded evaluation.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MovePlayed.java` — add `Optional<EvaluationSwing>` to the internal event.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java` — evaluate committed positions, maintain a FEN/ply-keyed baseline, and publish optional swing analysis.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java` — evaluation success/failure/cache/stop-resume/stale-result behavior.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java` — update `MovePlayed` fixture construction only; assert WebSocket payload shape remains unchanged.
- `docs/AI Chess Rivals - Tech Stack.md` — document the shallow evaluation settings and mover-perspective semantics.

**Do not modify for #39:**

- `client/**`
- Flyway migrations or persistence entities
- `ai/**`
- Spring AI provider configuration
- `MovePlayedMessage.java` or public WebSocket payload schema
- match pacing defaults

---

### Task 1: Add UCI Score Types and Parsing

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/PositionEvaluation.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/UciResponseTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciResponse.java`

**Interfaces:**
- Produces: `PositionEvaluation(PositionEvaluation.ScoreType type, int value)` where `ScoreType` is `CENTIPAWNS` or `MATE`.
- Produces: `long PositionEvaluation.comparableCentipawns()` with values bounded to `[-100_000, 100_000]` for mate scores.
- Produces: `Optional<PositionEvaluation> UciResponse.extractScore()`.

- [ ] **Step 1: Write failing parser tests**

Create `UciResponseTest.java` with these cases:

```java
package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import org.junit.jupiter.api.Test;

class UciResponseTest {

  @Test
  void extractsCentipawnScoreFromInfoLine() {
    PositionEvaluation score =
        new UciResponse("info depth 8 seldepth 10 score cp 37 nodes 1234 pv e2e4")
            .extractScore()
            .orElseThrow();

    assertThat(score.type()).isEqualTo(PositionEvaluation.ScoreType.CENTIPAWNS);
    assertThat(score.value()).isEqualTo(37);
    assertThat(score.comparableCentipawns()).isEqualTo(37L);
  }

  @Test
  void extractsNegativeCentipawnScore() {
    PositionEvaluation score =
        new UciResponse("info depth 8 score cp -215 nodes 99").extractScore().orElseThrow();

    assertThat(score.value()).isEqualTo(-215);
    assertThat(score.comparableCentipawns()).isEqualTo(-215L);
  }

  @Test
  void extractsPositiveMateScoreAsBoundedWinningValue() {
    PositionEvaluation score =
        new UciResponse("info depth 8 score mate 3 nodes 99").extractScore().orElseThrow();

    assertThat(score.type()).isEqualTo(PositionEvaluation.ScoreType.MATE);
    assertThat(score.value()).isEqualTo(3);
    assertThat(score.comparableCentipawns()).isEqualTo(100_000L);
  }

  @Test
  void extractsNegativeMateScoreAsBoundedLosingValue() {
    PositionEvaluation score =
        new UciResponse("info depth 8 score mate -2 nodes 99").extractScore().orElseThrow();

    assertThat(score.comparableCentipawns()).isEqualTo(-100_000L);
  }

  @Test
  void treatsMateZeroAsAlreadyCheckmatedForSideToMove() {
    PositionEvaluation score =
        new UciResponse("info depth 0 score mate 0").extractScore().orElseThrow();

    assertThat(score.comparableCentipawns()).isEqualTo(-100_000L);
  }

  @Test
  void returnsEmptyWhenLineDoesNotContainAScore() {
    assertThat(new UciResponse("info depth 8 nodes 99 pv e2e4").extractScore()).isEmpty();
    assertThat(new UciResponse("bestmove e2e4").extractScore()).isEmpty();
  }
}
```

- [ ] **Step 2: Run the parser test and verify it fails**

```bash
./server/mvnw -f server/pom.xml -Dtest=UciResponseTest test
```

Expected: compilation fails because `PositionEvaluation` and/or `extractScore()` do not exist.

- [ ] **Step 3: Implement the bounded position score**

Create `PositionEvaluation.java`:

```java
package dev.krishnamurti.ai_chess_rivals.chess.api;

import java.util.Objects;

public record PositionEvaluation(ScoreType type, int value) {

  public static final long MATE_COMPARABLE_CENTIPAWNS = 100_000L;

  public PositionEvaluation {
    Objects.requireNonNull(type, "type must not be null");
  }

  public long comparableCentipawns() {
    if (type == ScoreType.CENTIPAWNS) {
      return value;
    }
    if (value > 0) {
      return MATE_COMPARABLE_CENTIPAWNS;
    }
    return -MATE_COMPARABLE_CENTIPAWNS;
  }

  public enum ScoreType {
    CENTIPAWNS,
    MATE
  }
}
```

Do not encode mate as `Integer.MAX_VALUE`, and do not multiply the mate distance into the comparable score.

- [ ] **Step 4: Add score parsing to `UciResponse`**

Add:

```java
Optional<PositionEvaluation> extractScore() {
  String[] parts = raw.split("\\s+");
  for (int i = 0; i <= parts.length - 3; i++) {
    if (!"score".equals(parts[i])) {
      continue;
    }
    PositionEvaluation.ScoreType type =
        switch (parts[i + 1]) {
          case "cp" -> PositionEvaluation.ScoreType.CENTIPAWNS;
          case "mate" -> PositionEvaluation.ScoreType.MATE;
          default -> null;
        };
    if (type == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new PositionEvaluation(type, Integer.parseInt(parts[i + 2])));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }
  return Optional.empty();
}
```

Import `PositionEvaluation`. Keep all existing `bestmove` parsing unchanged.

- [ ] **Step 5: Run focused tests**

```bash
./server/mvnw -f server/pom.xml -Dtest=UciResponseTest test
```

Expected: `BUILD SUCCESS`, six tests pass.

- [ ] **Step 6: Commit the parser/model slice**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/PositionEvaluation.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciResponse.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/UciResponseTest.java
git commit -m "feat: parse stockfish evaluation scores"
```

---

### Task 2: Add Bounded Stockfish Evaluation Command and Configuration

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/StockfishClient.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/UciCommand.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEngine.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java`
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/.env.example`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessPropertiesValidationTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishClientIntegrationTest.java`

**Interfaces:**
- Produces: `PositionEvaluation StockfishClient.evaluate(int depth, Duration moveTime)` for the current position.
- Produces: `ChessProperties.Stockfish.Evaluation evaluation()` with depth, movetime, gain threshold, and mistake threshold.

- [ ] **Step 1: Extend `ChessProperties` with nested evaluation settings**

Change `ChessProperties.Stockfish` to include:

```java
@NotNull @Valid Evaluation evaluation
```

and add inside `Stockfish`:

```java
public record Evaluation(
    @Min(1) @Max(30) int depth,
    @Min(1) @Max(5_000) int moveTimeMillis,
    @Min(1) @Max(100_000) int majorGainThresholdCentipawns,
    @Min(1) @Max(100_000) int majorMistakeThresholdCentipawns) {}
```

Keep all existing Stockfish validation unchanged.

- [ ] **Step 2: Update property construction tests before compiling**

In `ChessPropertiesValidationTest`, create one helper and use it in every valid `Stockfish` construction:

```java
private static ChessProperties.Stockfish.Evaluation evaluation() {
  return new ChessProperties.Stockfish.Evaluation(8, 50, 200, 200);
}
```

Each constructor becomes:

```java
new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 16, 10, 30, evaluation())
```

Add validation cases for zero depth, zero movetime, zero gain threshold, zero mistake threshold, and null evaluation. Each test must assert the corresponding property path under `stockfish.evaluation` (or `stockfish.evaluation` for null).

- [ ] **Step 3: Add environment-backed defaults**

Under `app.chess.stockfish` in `application.yaml`, add:

```yaml
      evaluation:
        depth: ${STOCKFISH_EVALUATION_DEPTH:8}
        move-time-millis: ${STOCKFISH_EVALUATION_MOVE_TIME_MILLIS:50}
        major-gain-threshold-centipawns: ${STOCKFISH_MAJOR_GAIN_THRESHOLD_CP:200}
        major-mistake-threshold-centipawns: ${STOCKFISH_MAJOR_MISTAKE_THRESHOLD_CP:200}
```

Under `# Stockfish runtime configuration` in `server/.env.example`, add:

```text
STOCKFISH_EVALUATION_DEPTH=8
STOCKFISH_EVALUATION_MOVE_TIME_MILLIS=50
STOCKFISH_MAJOR_GAIN_THRESHOLD_CP=200
STOCKFISH_MAJOR_MISTAKE_THRESHOLD_CP=200
```

- [ ] **Step 4: Add the dedicated UCI command**

In `UciCommand`, add:

```java
static UciCommand evaluate(int depth, long moveTimeMs) {
  return new UciCommand("go depth " + depth + " movetime " + moveTimeMs);
}
```

Do not replace the existing `go(long moveTimeMs)` used for move selection.

- [ ] **Step 5: Extend `StockfishClient`**

Add:

```java
PositionEvaluation evaluate(int depth, Duration moveTime);
```

Document that it evaluates the position most recently supplied by `setPosition`, returns the latest UCI score seen before `bestmove`, and throws `StockfishException` if no score is produced before the bounded search ends.

- [ ] **Step 6: Implement `StockfishEngine.evaluate`**

Add this method next to `bestMove`:

```java
@Override
public PositionEvaluation evaluate(int depth, Duration moveTime) {
  if (depth <= 0) {
    throw new IllegalArgumentException("depth must be positive");
  }
  if (moveTime == null || moveTime.isZero() || moveTime.isNegative()) {
    throw new IllegalArgumentException("moveTime must be positive");
  }

  try {
    sendCommand(UciCommand.evaluate(depth, moveTime.toMillis()));
    long deadlineSeconds = Math.max(1L, moveTime.toSeconds()) + moveTimeoutSeconds;
    PositionEvaluation latestScore = null;

    while (true) {
      String rawLine = readLineWithTimeout(deadlineSeconds, "evaluation bestmove");
      if (rawLine == null) {
        throw new StockfishException("Stockfish process ended during position evaluation");
      }
      UciResponse response = new UciResponse(rawLine);
      log.debug("<<< {}", rawLine);
      if (response.startsWith("info ")) {
        latestScore = response.extractScore().orElse(latestScore);
      }
      if (response.startsWith("bestmove")) {
        if (latestScore == null) {
          throw new StockfishException("Stockfish returned bestmove without an evaluation score");
        }
        return latestScore;
      }
    }
  } catch (IOException e) {
    throw new StockfishException("Failed to evaluate position with Stockfish", e);
  }
}
```

Import `PositionEvaluation`. Do not call `bestMove()` internally because that discards the `info` lines containing the score.

- [ ] **Step 7: Add a real-engine smoke test**

Update `StockfishClientIntegrationTest` constructor with the new nested evaluation config, then add:

```java
@Test
void evaluateReturnsBoundedScoreFromStartingPosition() {
  client.newGame();
  client.setPosition("startpos");

  PositionEvaluation evaluation = client.evaluate(8, Duration.ofMillis(50));

  assertThat(evaluation).isNotNull();
  assertThat(evaluation.comparableCentipawns())
      .isBetween(-PositionEvaluation.MATE_COMPARABLE_CENTIPAWNS,
          PositionEvaluation.MATE_COMPARABLE_CENTIPAWNS);
}
```

- [ ] **Step 8: Run configuration/unit tests**

```bash
./server/mvnw -f server/pom.xml -Dtest=ChessPropertiesValidationTest,UciResponseTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Run the Stockfish integration test when the binary is available**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=StockfishClientIntegrationTest test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=StockfishClientIntegrationTest test
```

Expected: existing move tests plus the evaluation smoke test pass. If the binary is absent, acquire it using the repository's existing Windows/Linux Stockfish Maven profile; do not weaken or skip the integration assertion in code.

- [ ] **Step 10: Commit bounded evaluation transport/config**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess \
  server/src/main/resources/application.yaml server/.env.example \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess
git commit -m "feat: add bounded stockfish position evaluation"
```

---

### Task 3: Add Mover-Perspective Swing Classification Behind the Chess API

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/EvaluationSwing.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/EvaluationSwingClassification.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/api/ChessEvaluationService.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationService.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationServiceTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishConfiguration.java`

**Interfaces:**
- Produces: `PositionEvaluation ChessEvaluationService.evaluate(String fen)`.
- Produces: `EvaluationSwing ChessEvaluationService.compare(PositionEvaluation before, PositionEvaluation after)`.
- Contract: `before` is evaluated with the mover to move; `after` is evaluated with the opponent to move. `compare` negates the post-move comparable score so both values are from the mover's perspective.

- [ ] **Step 1: Create the broad classification enum**

```java
package dev.krishnamurti.ai_chess_rivals.chess.api;

public enum EvaluationSwingClassification {
  STABLE,
  MAJOR_GAIN,
  MAJOR_MISTAKE
}
```

Do not add `GOOD`, `GREAT`, `BRILLIANT`, `BLUNDER`, or `UNAVAILABLE`.

- [ ] **Step 2: Create the swing record**

```java
package dev.krishnamurti.ai_chess_rivals.chess.api;

import java.util.Objects;

public record EvaluationSwing(
    long beforeCentipawns,
    long afterCentipawns,
    long swingCentipawns,
    EvaluationSwingClassification classification) {

  public EvaluationSwing {
    Objects.requireNonNull(classification, "classification must not be null");
  }
}
```

The `beforeCentipawns` and `afterCentipawns` fields are comparable values normalized to the mover's perspective, not raw side-to-move Stockfish values.

- [ ] **Step 3: Create the public chess-module boundary**

```java
package dev.krishnamurti.ai_chess_rivals.chess.api;

public interface ChessEvaluationService {

  PositionEvaluation evaluate(String fen);

  EvaluationSwing compare(PositionEvaluation before, PositionEvaluation after);
}
```

Add Javadoc explaining the adjacent-position perspective contract from the Interfaces block above.

- [ ] **Step 4: Write failing service tests with a fake client**

Create `StockfishEvaluationServiceTest` and cover at minimum:

```java
@Test
void compareNormalizesPostMoveScoreToMoverPerspective() {
  EvaluationSwing swing =
      service.compare(
          new PositionEvaluation(PositionEvaluation.ScoreType.CENTIPAWNS, 20),
          new PositionEvaluation(PositionEvaluation.ScoreType.CENTIPAWNS, -260));

  assertThat(swing.beforeCentipawns()).isEqualTo(20);
  assertThat(swing.afterCentipawns()).isEqualTo(260);
  assertThat(swing.swingCentipawns()).isEqualTo(240);
  assertThat(swing.classification()).isEqualTo(EvaluationSwingClassification.MAJOR_GAIN);
}

@Test
void gainThresholdIsInclusive() {
  EvaluationSwing swing = service.compare(cp(0), cp(-200));
  assertThat(swing.classification()).isEqualTo(EvaluationSwingClassification.MAJOR_GAIN);
}

@Test
void mistakeThresholdIsInclusive() {
  EvaluationSwing swing = service.compare(cp(0), cp(200));
  assertThat(swing.classification()).isEqualTo(EvaluationSwingClassification.MAJOR_MISTAKE);
}

@Test
void oneCentipawnInsideThresholdRemainsStable() {
  assertThat(service.compare(cp(0), cp(-199)).classification())
      .isEqualTo(EvaluationSwingClassification.STABLE);
  assertThat(service.compare(cp(0), cp(199)).classification())
      .isEqualTo(EvaluationSwingClassification.STABLE);
}

@Test
void mateScoresDoNotOverflowOrInvertClassification() {
  EvaluationSwing swing = service.compare(cp(0), mate(0));
  assertThat(swing.afterCentipawns()).isEqualTo(100_000L);
  assertThat(swing.swingCentipawns()).isEqualTo(100_000L);
  assertThat(swing.classification()).isEqualTo(EvaluationSwingClassification.MAJOR_GAIN);
}
```

Also test `evaluate(fen)` sends `setPosition(fen)` and calls `client.evaluate(8, Duration.ofMillis(50))` exactly once.

- [ ] **Step 5: Implement `StockfishEvaluationService`**

Use this shape:

```java
package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationService;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.time.Duration;
import java.util.Objects;

final class StockfishEvaluationService implements ChessEvaluationService {

  private final StockfishClient client;
  private final int depth;
  private final Duration moveTime;
  private final int gainThreshold;
  private final int mistakeThreshold;

  StockfishEvaluationService(StockfishClient client, ChessProperties properties) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    ChessProperties.Stockfish.Evaluation config = properties.stockfish().evaluation();
    this.depth = config.depth();
    this.moveTime = Duration.ofMillis(config.moveTimeMillis());
    this.gainThreshold = config.majorGainThresholdCentipawns();
    this.mistakeThreshold = config.majorMistakeThresholdCentipawns();
  }

  @Override
  public PositionEvaluation evaluate(String fen) {
    if (fen == null || fen.isBlank()) {
      throw new IllegalArgumentException("fen must not be blank");
    }
    client.setPosition(fen);
    return client.evaluate(depth, moveTime);
  }

  @Override
  public EvaluationSwing compare(PositionEvaluation before, PositionEvaluation after) {
    Objects.requireNonNull(before, "before must not be null");
    Objects.requireNonNull(after, "after must not be null");

    long normalizedBefore = before.comparableCentipawns();
    long normalizedAfter = -after.comparableCentipawns();
    long swing = normalizedAfter - normalizedBefore;

    EvaluationSwingClassification classification;
    if (swing >= gainThreshold) {
      classification = EvaluationSwingClassification.MAJOR_GAIN;
    } else if (swing <= -mistakeThreshold) {
      classification = EvaluationSwingClassification.MAJOR_MISTAKE;
    } else {
      classification = EvaluationSwingClassification.STABLE;
    }

    return new EvaluationSwing(normalizedBefore, normalizedAfter, swing, classification);
  }
}
```

- [ ] **Step 6: Register the service bean**

In `StockfishConfiguration`, add:

```java
@Bean
ChessEvaluationService chessEvaluationService(
    StockfishClient stockfishClient, ChessProperties chessProperties) {
  return new StockfishEvaluationService(stockfishClient, chessProperties);
}
```

The game module already allows only `chess :: api`, so do not inject `ChessProperties` or `StockfishEvaluationService` directly into game code.

- [ ] **Step 7: Run service tests**

```bash
./server/mvnw -f server/pom.xml -Dtest=StockfishEvaluationServiceTest test
```

Expected: `BUILD SUCCESS`, including exact threshold boundaries and mate-zero behavior.

- [ ] **Step 8: Run Modulith structure verification early**

```bash
./server/mvnw -f server/pom.xml -Dtest=AiChessRivalsApplicationTests test
```

Expected: module verification remains green; `game` consumes only types under `chess.api`.

- [ ] **Step 9: Commit the chess evaluation API**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/StockfishEvaluationServiceTest.java
git commit -m "feat: classify stockfish evaluation swings"
```

---

### Task 4: Integrate Best-Effort Evaluation Into the Match Turn Flow

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MovePlayed.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`

**Interfaces:**
- Consumes: `ChessEvaluationService.evaluate(fen)` and `.compare(before, after)` from Task 3.
- Produces: `MovePlayed.evaluation()` as `Optional<EvaluationSwing>` for later dialogue-policy code.
- WebSocket output remains unchanged because `MatchStreamMessageMapper` continues mapping only the existing move fields.

- [ ] **Step 1: Extend the internal event first**

Change the record component list to:

```java
public record MovePlayed(
    int ply,
    PlayerColor player,
    MoveNotation notation,
    BoardPosition position,
    MoveDetails details,
    Optional<EvaluationSwing> evaluation)
    implements MatchEvent {
```

In the compact constructor add:

```java
Objects.requireNonNull(evaluation, "evaluation must not be null");
```

Import `EvaluationSwing` and `Optional`. Do not change the convenience chess-metadata accessors.

Update every existing `new MovePlayed(...)` test fixture outside `MatchEngine` to pass `Optional.empty()` unless that test is specifically asserting evaluation behavior.

- [ ] **Step 2: Add an evaluation baseline type inside `MatchEngine`**

Add fields:

```java
private final ChessEvaluationService chessEvaluationService;
private final AtomicReference<EvaluationBaseline> evaluationBaseline = new AtomicReference<>();
```

Add the small nested record at the bottom of the class:

```java
private record EvaluationBaseline(int ply, String fen, PositionEvaluation evaluation) {}
```

Import `ChessEvaluationService`, `EvaluationSwing`, `PositionEvaluation`, and `Optional`.

- [ ] **Step 3: Inject the evaluation service**

Add `ChessEvaluationService chessEvaluationService` to the constructor and assign it with `Objects.requireNonNull`.

Update `MatchEngineTest.matchEngine(...)` helpers to pass a deterministic fake `ChessEvaluationService`. Do not mock Spring; use a tiny in-test fake that records FENs and returns queued `PositionEvaluation` values.

- [ ] **Step 4: Add safe evaluation helpers before changing the loop**

Add:

```java
private Optional<PositionEvaluation> safeEvaluate(int ply, String fen) {
  try {
    return Optional.of(chessEvaluationService.evaluate(fen));
  } catch (RuntimeException exception) {
    log.warn("Stockfish evaluation unavailable for ply {} and position {}", ply, fen, exception);
    return Optional.empty();
  }
}
```

Add `@Slf4j` to `MatchEngine` if it is not already present.

Add:

```java
private Optional<PositionEvaluation> evaluationBefore(Match match) {
  EvaluationBaseline baseline = evaluationBaseline.get();
  String fen = match.currentPosition().fen();
  if (baseline != null && baseline.ply() == match.moveCount() && baseline.fen().equals(fen)) {
    return Optional.of(baseline.evaluation());
  }
  return safeEvaluate(match.moveCount(), fen);
}
```

Add:

```java
private void storeBaselineIfAuthoritative(int ply, String fen, PositionEvaluation evaluation) {
  Match authoritative = currentMatch.get();
  if (authoritative != null
      && authoritative.moveCount() == ply
      && authoritative.currentPosition().fen().equals(fen)) {
    evaluationBaseline.set(new EvaluationBaseline(ply, fen, evaluation));
  }
}
```

The FEN + ply check is the stale-result guard. Do not store an evaluation from an older execution against a newer ply.

- [ ] **Step 5: Initialize the starting-position baseline best-effort**

In `startNewMatch`, after `chessPlayer.startNewGame()` succeeds and before publishing `MatchStarted`:

```java
evaluationBaseline.set(null);
safeEvaluate(0, match.currentPosition().fen())
    .ifPresent(evaluation ->
        evaluationBaseline.set(
            new EvaluationBaseline(0, match.currentPosition().fen(), evaluation)));
```

An evaluation exception here must not fail `startNewMatch`.

- [ ] **Step 6: Change the per-ply order without changing move authority**

Inside the loop, before `chooseMove`, capture:

```java
Optional<PositionEvaluation> beforeEvaluation = evaluationBefore(match);
```

Keep move selection, `applyMove`, `recordMove`, and `currentMatch.set(match)` in their current order. The legal move must be committed before post-move evaluation.

Immediately after `currentMatch.set(match)`, evaluate the committed position:

```java
Optional<PositionEvaluation> afterEvaluation =
    safeEvaluate(recordedMove.sequenceNumber(), match.currentPosition().fen());

afterEvaluation.ifPresent(
    evaluation ->
        storeBaselineIfAuthoritative(
            recordedMove.sequenceNumber(), match.currentPosition().fen(), evaluation));

Optional<EvaluationSwing> evaluationSwing =
    beforeEvaluation.flatMap(
        before -> afterEvaluation.map(after -> chessEvaluationService.compare(before, after)));
```

Then publish `MovePlayed` with `evaluationSwing` as the final record component.

Do not wrap evaluation failure back into `MatchEngineException`. Only move selection, move application, state mutation, and event publication remain match-fatal.

- [ ] **Step 7: Add the successful turn-flow test**

In `MatchEngineTest`, add a test for one move where the fake evaluator returns `cp 10` for the starting FEN and `cp -250` after `e2e4`. Assert:

```java
MovePlayed event = (MovePlayed) eventSink.events.get(1);
EvaluationSwing swing = event.evaluation().orElseThrow();
assertEquals(10L, swing.beforeCentipawns());
assertEquals(250L, swing.afterCentipawns());
assertEquals(240L, swing.swingCentipawns());
assertEquals(EvaluationSwingClassification.MAJOR_GAIN, swing.classification());
```

Also assert the evaluator saw the initial FEN once and the committed post-move FEN once; it must not evaluate the same successful pre-move position twice.

- [ ] **Step 8: Add evaluation-failure resilience tests**

Add one test where initial evaluation throws and post-move evaluation succeeds. Assert the move is committed/published with `Optional.empty()` evaluation.

Add another where post-move evaluation throws. Assert:

```java
assertEquals(1, finalMatch.moveCount());
MovePlayed event = (MovePlayed) eventSink.events.get(1);
assertTrue(event.evaluation().isEmpty());
```

The match may still finish because of the test's max-plies fallback; the important assertion is that evaluation failure did not invalidate `e2e4`.

- [ ] **Step 9: Add cache/stale protection coverage**

Add a two-ply test where evaluations are queued as:

1. start position `cp 0`
2. after White move `cp -20`
3. after Black move `cp -10`

Assert exactly three evaluation calls for two committed moves, proving the first move's after-score is reused as the second move's before-score.

Add a focused helper-level or match-flow test that places a baseline with the wrong FEN for the same ply and verifies `evaluationBefore` does not reuse it and instead evaluates the authoritative FEN.

If direct helper testing is impossible because helpers remain private, drive it by stopping after one move, altering the fake evaluator queue, resuming, and asserting the resumed evaluation calls are keyed to `currentMatch().currentPosition().fen()` rather than blindly consuming the old baseline.

- [ ] **Step 10: Preserve stop/resume behavior**

Extend the existing stop/resume test so the first committed move has an evaluation and the resumed move also has one. Assert:

- exactly one `MatchStarted` event across stop/resume;
- no duplicate `MovePlayed` event for the stopped ply;
- resumed ply number is the next ply;
- its before-evaluation comes from a baseline whose FEN equals the stopped match's current FEN;
- no evaluation result is attached to a different ply than the FEN that produced it.

Do not change `MatchControlService` or its executor policy unless this test exposes an existing race. If a race appears, stop and diagnose it rather than introducing a second execution framework inside #39.

- [ ] **Step 11: Confirm WebSocket schema is unchanged**

Update `MatchStreamMessageMapperTest` constructors with `Optional.empty()` or a real `EvaluationSwing`, then assert the mapped `MovePlayedMessage` still contains only the pre-existing fields. Do not add evaluation to `MovePlayedMessage`.

Run:

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchEngineTest,MatchStreamMessageMapperTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 12: Commit match integration**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game
git commit -m "feat: attach evaluation swings to move events"
```

---

### Task 5: Document Semantics and Run Full Verification

**Files:**
- Modify: `docs/AI Chess Rivals - Tech Stack.md`
- Verify: whole backend/repository

**Interfaces:**
- No new runtime interface. This task locks down documentation and acceptance evidence.

- [ ] **Step 1: Update the Stockfish section of the Tech Stack**

Add a concise subsection describing exactly:

```markdown
### Lightweight Position Evaluation

Phase 2 reuses the existing Stockfish 17.1 process for shallow post-move evaluation.

- Evaluation depth: `8` by default (`STOCKFISH_EVALUATION_DEPTH`)
- Evaluation movetime cap: `50ms` by default (`STOCKFISH_EVALUATION_MOVE_TIME_MILLIS`)
- Major-gain threshold: `200cp` by default (`STOCKFISH_MAJOR_GAIN_THRESHOLD_CP`)
- Major-mistake threshold: `200cp` by default (`STOCKFISH_MAJOR_MISTAKE_THRESHOLD_CP`)
- Before/after scores are normalized to the player who made the move, so positive swing means improvement for the mover and negative swing means deterioration.
- Mate scores use a bounded comparable value for classification; the feature does not expose consumer-chess labels or deep analysis.
```

Do not describe evaluation as changing move selection.

- [ ] **Step 2: Apply backend formatting**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
```

POSIX:

```bash
./server/mvnw -f server/pom.xml spotless:apply
```

Expected: command exits `0`.

- [ ] **Step 3: Run focused evaluation tests once more**

```bash
./server/mvnw -f server/pom.xml -Dtest=UciResponseTest,StockfishEvaluationServiceTest,MatchEngineTest,MatchStreamMessageMapperTest test
```

Expected: all focused tests pass.

- [ ] **Step 4: Run the complete backend verification gate**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

POSIX:

```bash
./server/mvnw -f server/pom.xml verify
```

Expected: formatting check, Error Prone compilation, tests including Spring Modulith verification, and SpotBugs all pass.

- [ ] **Step 5: Run the repository verifier**

Windows:

```powershell
.\scripts\verify.ps1
```

POSIX:

```bash
./scripts/verify.sh
```

Expected: backend and frontend verification both pass. Frontend should be unchanged.

- [ ] **Step 6: Review the issue acceptance criteria mechanically**

Confirm each item with evidence:

- Every committed move: post-move evaluator is invoked with bounded depth/movetime.
- Consistent perspective: `compare()` negates post-move side-to-move evaluation and tests both positive/negative swings.
- Mate safety: bounded `100_000cp` comparable representation plus `mate 0` test.
- Threshold boundaries: exact `+200`, `-200`, `+199`, and `-199` tests.
- Evaluation failure: committed move remains in `currentMatch` and event carries `Optional.empty()`.
- Stop/resume stale protection: baseline reuse requires exact ply + FEN and lifecycle test remains green.
- Existing lifecycle: full `verify` passes.

- [ ] **Step 7: Commit documentation/verification cleanup**

```bash
git add docs/AI\ Chess\ Rivals\ -\ Tech\ Stack.md server

git commit -m "docs: document stockfish evaluation analysis"
```

If formatting changed files already committed in earlier tasks, include those exact formatting-only files in this final commit rather than rewriting commit history.

---

## Final Review Checklist

Before opening the PR, verify all of the following:

- [ ] No `ai` or Spring AI type is imported by `chess` or `game` changes.
- [ ] No new dependency was added.
- [ ] `StockfishPlayer.chooseMove()` still uses only the existing move-think-time path.
- [ ] Evaluation uses `go depth 8 movetime 50` by default and waits only until its `bestmove`.
- [ ] The latest parsable score before `bestmove` is used.
- [ ] Mate values are bounded and `mate 0` is treated as losing for the side to move.
- [ ] Positive `EvaluationSwing.swingCentipawns()` always means improvement for the mover, regardless of White/Black.
- [ ] Classification has exactly three values.
- [ ] Boundary tests cover exactly `+200`, `-200`, `+199`, and `-199` with the default thresholds.
- [ ] A failed evaluation cannot remove or roll back a legal committed move.
- [ ] Baseline cache reuse requires both matching ply and matching FEN.
- [ ] `MovePlayed` exposes `Optional<EvaluationSwing>` internally.
- [ ] `MovePlayedMessage` and frontend files are unchanged.
- [ ] Stop/resume and existing match lifecycle tests pass.
- [ ] Spotless was applied before `verify`.
- [ ] `./server/mvnw -f server/pom.xml verify` (or Windows equivalent) passes.
- [ ] Root repository verification passes.

## Suggested PR Scope

One PR for issue #39 only. Do not combine personality persistence (#40), character seeds (#41), dialogue generation (#42), or observability (#46) into this branch.
