# Dialogue Persistence and Match Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task with inline execution and checkpoints. Do not use subagent-driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement issue #43 so accepted start/move/end dialogue becomes durable match history, is broadcast only after persistence, survives refresh/reconnect, and can never break chess execution or leak stale dialogue across Stop/Resume or a second match.

**Architecture:** Give each in-memory `Match` a stable UUID and use that UUID as the dialogue persistence key; do **not** introduce a separate persisted match aggregate in this issue. Keep generation/persistence behind a small game-side `MatchDialogueCoordinator` that consumes the existing `ai :: api` contracts synchronously after committed chess events, passes only the last four persisted lines back into #42, persists before broadcasting, and catches dialogue failures so chess continues. Add an execution-generation guard in `MatchEngine` so a provider response that returns after Stop/Resume is discarded before persistence.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring Data JPA, Flyway, PostgreSQL 17, existing Spring AI dialogue workflow, WebSocket, React 19, TypeScript ~6.0, Zustand 5, JUnit 5, Mockito, AssertJ, Vitest.

## Source of Truth

- Issue: `#43 Phase 2: Persist dialogue and integrate it into the match lifecycle`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Dependency already merged: `#42 Implement contextual dialogue generation workflow`
- Approved Phase 2 design: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Existing dialogue contracts: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/**`
- Existing dialogue implementation: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/**`
- Existing match loop: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- Existing snapshot/WebSocket flow: `MatchSnapshot`, `MatchResponse`, `MatchStateMessage`, `MatchStreamMessageMapper`, `MatchWebSocketHandler`
- Governing rules: `AGENTS.md`, `.agents/AGENTS.md`, `docs/AI Chess Rivals - Constitution.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Chess remains authoritative. LLMs never select, validate, or recommend moves.
- Approved turn order for a non-terminal move is: commit move -> evaluate/classify -> broadcast the existing move event -> generate dialogue -> persist accepted dialogue -> broadcast persisted dialogue -> existing random delay -> next move.
- Game-start dialogue runs in the background match execution immediately before the first move, not inside the HTTP Start request.
- Game-end dialogue runs after the final chess state/result is authoritative and before `MATCH_FINISHED` is broadcast.
- Reuse the existing `DialogueGenerator`; do not add provider-specific calls, retries, timeout code, another gateway, chat memory, tools, agents, or prompt changes unless a concrete integration bug requires it.
- Provider waits are already bounded by #38/#42. Do not add another executor or timeout layer around `DialogueGenerator`.
- Persist deterministic fallback output exactly like Groq/Gemini output. `AiResponseSource` is the provider/fallback metadata for #43.
- Persistence failures, disabled AI, provider timeouts, malformed provider output, both-provider failure, WebSocket failure, or other dialogue-layer runtime failures must never terminate the chess match.
- Persist before broadcast. If persistence fails, do not broadcast an unpersisted line.
- Every persisted row is unique by `(match_id, trigger_type, trigger_ply, personality_key)`.
- Use database insertion order (`id`) as the canonical chronological order. `created_at` is metadata, not the ordering key.
- Recent context is exactly the last four **persisted** rows for the current match, returned oldest-to-newest.
- Stop/Resume invalidates in-flight dialogue generation before it may persist. Do not attempt to interrupt provider calls; simply discard their late result.
- A new match gets a new UUID. Never reuse dialogue history from the previous match.
- Do not add a persisted match table in #43. Server-restart recovery is not part of this issue; browser refresh/reconnect against the running backend is.
- #44 owns selectable rivalry setup. Until #44 lands, use one clearly isolated temporary runtime rivalry: White=`blaze`, Black=`vesper`. Keep those two literals in **one place only** (`MatchDialogueCoordinator`) so #44 can replace them with selected match identities mechanically.
- #45 owns dialogue rendering. #43 may add frontend transport/types/store hydration, but must not add dialogue activity rows, styling, speech bubbles, or banter UI.
- No new Maven or npm dependency is required.

## File Map

### Create

- `server/src/main/resources/db/migration/V4__create_dialogue_line.sql`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/DialogueTriggerType.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/PersistedDialogue.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/DialogueHistoryStore.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueLineEntity.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueLineRepository.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/DialoguePlayed.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/DialogueResponse.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/DialoguePlayedMessage.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceServiceTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistencePostgresIT.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`

### Modify

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchSnapshot.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchEvent.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStateMessage.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageType.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlServiceTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchWebSocketIntegrationTest.java`
- `client/src/types/match.ts`
- `client/src/services/matchSocket.ts`
- `client/src/services/matchViewer.messages.ts`
- `client/src/services/matchViewer.messages.test.ts`
- `client/src/store/matchViewerStore.ts`
- `client/src/store/matchViewerStore.test.ts`
- `docs/AI Chess Rivals - Tech Stack.md`
- `docs/BUILD_AND_VERIFY.md`

### Explicitly Do Not Modify

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/**`
- Groq/Gemini provider configuration or timeout values
- `DialogueGenerationService`, prompt templates, output codec, speaking probabilities, or fallback catalog unless a failing integration test demonstrates a concrete #43 contract mismatch
- Stockfish/UCI code
- `server/Dockerfile`, `server/docker-compose.yml`, `.github/workflows/ci.yml`
- personality seed data or roster API
- frontend activity rendering/components/CSS

---

## Task 1: Give Every Match a Stable Identity

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java`

**Interfaces:**
- Produces: `UUID Match.id()`.
- Preserves: existing public constructor signature for current tests/callers.
- Invariant: `recordMove(...)` and `finish(...)` retain the same UUID; a separately created match receives a different UUID.

- [ ] **Step 1: Add failing identity tests**

Add focused tests equivalent to:

```java
@Test
void keepsMatchIdAcrossImmutableTransitions() {
  Match started = Match.newGame();
  AppliedMove e4 = boardService.applyMove(started.currentPosition(), new MoveNotation("e2e4"));

  Match moved = started.recordMove(new MoveNotation("e2e4"), e4.position(), e4.details());
  Match finished = moved.finish(GameResult.DRAW);

  assertThat(moved.id()).isEqualTo(started.id());
  assertThat(finished.id()).isEqualTo(started.id());
}

@Test
void newGamesReceiveDistinctIds() {
  assertThat(Match.newGame().id()).isNotEqualTo(Match.newGame().id());
}
```

Use the existing `MatchTest` helpers rather than creating a second chess-board fixture if one already exists.

- [ ] **Step 2: Run the focused domain test and confirm RED**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchTest test
```

Expected: FAIL because `Match.id()` does not exist.

- [ ] **Step 3: Add UUID identity while preserving existing callers**

Update `Match` around this exact shape:

```java
private final UUID id;
private final PlayerColor sideToMove;
// existing fields...

public Match(
    UUID id,
    PlayerColor sideToMove,
    BoardPosition currentPosition,
    List<Move> moves,
    GameStatus status,
    GameResult result) {
  this.id = Objects.requireNonNull(id, "id must not be null");
  // keep the existing validation/body for the remaining fields
}

public Match(
    PlayerColor sideToMove,
    BoardPosition currentPosition,
    List<Move> moves,
    GameStatus status,
    GameResult result) {
  this(UUID.randomUUID(), sideToMove, currentPosition, moves, status, result);
}

public static Match newGame() {
  return new Match(
      UUID.randomUUID(),
      PlayerColor.WHITE,
      BoardPosition.STARTING_POSITION,
      List.of(),
      GameStatus.IN_PROGRESS,
      null);
}

public UUID id() {
  return id;
}
```

Add `import java.util.UUID;`.

Change both immutable transitions to call the UUID-aware constructor:

```java
return new Match(
    id, sideToMove.opposite(), positionAfterMove, updatedMoves, GameStatus.IN_PROGRESS, null);
```

```java
return new Match(id, sideToMove, currentPosition, moves, GameStatus.FINISHED, result);
```

Do not add `equals/hashCode`, a persisted match entity, or a match repository.

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run the same `MatchTest` command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java
git commit -m "feat: add stable match identity"
```

---

## Task 2: Define the Dialogue Persistence Contract and Flyway Schema

**Files:**
- Create: `server/src/main/resources/db/migration/V4__create_dialogue_line.sql`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/DialogueTriggerType.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/PersistedDialogue.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/DialogueHistoryStore.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ApplicationModulesTest.java` only if the existing named-interface assertion enumerates public `ai :: api` types explicitly.

**Interfaces:**
- Produces: one persistence row per accepted line.
- Produces: `DialogueHistoryStore.persistIfAbsent(...)`, `findAll(...)`, and `lastFour(...)` through the existing `ai :: api` named interface.
- Canonical ordering: row `id ASC`.

- [ ] **Step 1: Add V4 with the exact persistence shape**

Create `V4__create_dialogue_line.sql`:

```sql
CREATE TABLE dialogue_line (
    id bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    match_id uuid NOT NULL,
    trigger_type varchar(32) NOT NULL,
    trigger_ply integer NOT NULL,
    personality_key varchar(64) NOT NULL,
    personality_display_name varchar(80) NOT NULL,
    dialogue_text varchar(280) NOT NULL,
    emotion varchar(32) NOT NULL,
    reaction_type varchar(32) NOT NULL,
    response_source varchar(32) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT dialogue_line_pkey PRIMARY KEY (id),
    CONSTRAINT dialogue_line_personality_fk
        FOREIGN KEY (personality_key) REFERENCES personality(personality_key),
    CONSTRAINT dialogue_line_trigger_ply_check CHECK (trigger_ply >= 0),
    CONSTRAINT dialogue_line_unique_trigger_speaker
        UNIQUE (match_id, trigger_type, trigger_ply, personality_key)
);

CREATE INDEX dialogue_line_match_history_idx
    ON dialogue_line (match_id, id);
```

Do not create PostgreSQL enum types; keep Java enums stored as strings so future additions remain a normal migration/code change.

- [ ] **Step 2: Add the trigger enum**

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

public enum DialogueTriggerType {
  GAME_START,
  MOVE,
  GAME_END
}
```

- [ ] **Step 3: Add the public persisted-line record**

Create `PersistedDialogue.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PersistedDialogue(
    long id,
    UUID matchId,
    DialogueTriggerType triggerType,
    int triggerPly,
    String personalityKey,
    String personalityDisplayName,
    String text,
    DialogueEmotion emotion,
    DialogueReactionType reactionType,
    AiResponseSource source,
    Instant createdAt) {

  public PersistedDialogue {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    if (triggerPly < 0) {
      throw new IllegalArgumentException("triggerPly must not be negative");
    }
    requireText(personalityKey, "personalityKey");
    requireText(personalityDisplayName, "personalityDisplayName");
    requireText(text, "text");
    if (text.length() > 280) {
      throw new IllegalArgumentException("text must be at most 280 characters");
    }
    Objects.requireNonNull(emotion, "emotion must not be null");
    Objects.requireNonNull(reactionType, "reactionType must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
```

- [ ] **Step 4: Add the minimal storage interface**

Create `DialogueHistoryStore.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DialogueHistoryStore {

  Optional<PersistedDialogue> persistIfAbsent(
      UUID matchId,
      DialogueTriggerType triggerType,
      int triggerPly,
      GeneratedDialogue dialogue);

  List<PersistedDialogue> findAll(UUID matchId);

  List<DialogueHistoryLine> lastFour(UUID matchId);
}
```

`Optional.empty()` means the unique trigger/speaker line already exists and therefore must **not** be broadcast again.

- [ ] **Step 5: Compile the backend contract**

```powershell
server\mvnw.cmd -f server\pom.xml -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/resources/db/migration/V4__create_dialogue_line.sql \
        server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api
git commit -m "feat: define persisted dialogue contract"
```

---

## Task 3: Implement Idempotent Dialogue Persistence and Four-Line History

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueLineEntity.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueLineRepository.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceService.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceServiceTest.java`

**Interfaces:**
- Consumes: `GeneratedDialogue`, `PersonalityService.requirePromptProfile(...)`.
- Produces: `DialogueHistoryStore` implementation.
- Preserves: insertion order, source metadata, and exact accepted text.

- [ ] **Step 1: Write RED service tests for idempotency, ordering, and recent context**

Cover all of these behaviors:

```java
@Test
void returnsEmptyAndDoesNotSaveWhenTriggerSpeakerAlreadyExists() {
  when(repository.findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
          MATCH_ID, DialogueTriggerType.MOVE, 7, "blaze"))
      .thenReturn(Optional.of(existingEntity()));

  Optional<PersistedDialogue> saved = service.persistIfAbsent(
      MATCH_ID, DialogueTriggerType.MOVE, 7, generated("blaze", "Still standing."));

  assertThat(saved).isEmpty();
  verify(repository, never()).save(any());
}

@Test
void lastFourReturnsOldestToNewestAfterReadingNewestRows() {
  when(repository.findTop4ByMatchIdOrderByIdDesc(MATCH_ID))
      .thenReturn(List.of(entity(9), entity(8), entity(7), entity(6)));

  assertThat(service.lastFour(MATCH_ID))
      .extracting(DialogueHistoryLine::triggeringPly)
      .containsExactly(6, 7, 8, 9);
}

@Test
void persistsFallbackSourceWithoutSpecialCase() {
  GeneratedDialogue fallback = new GeneratedDialogue(
      "blaze",
      "Fine. We continue.",
      DialogueEmotion.NEUTRAL,
      DialogueReactionType.MOVE_REACTION,
      AiResponseSource.DETERMINISTIC_FALLBACK);

  PersistedDialogue saved = service.persistIfAbsent(
      MATCH_ID, DialogueTriggerType.MOVE, 4, fallback).orElseThrow();

  assertThat(saved.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
  assertThat(saved.text()).isEqualTo("Fine. We continue.");
}
```

Also assert `findAll` preserves repository `id ASC` order and that stored `personalityDisplayName` comes from `PersonalityService.requirePromptProfile(key).displayName()`.

- [ ] **Step 2: Run the focused test and confirm RED**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePersistenceServiceTest test
```

Expected: FAIL because the persistence classes do not exist.

- [ ] **Step 3: Create the JPA entity matching V4 exactly**

Use `@Enumerated(EnumType.STRING)` for the three enum columns and an explicit `Instant createdAt`. Keep the entity package-private.

Required field mapping:

```java
@Entity
@Table(
    name = "dialogue_line",
    uniqueConstraints =
        @UniqueConstraint(
            name = "dialogue_line_unique_trigger_speaker",
            columnNames = {"match_id", "trigger_type", "trigger_ply", "personality_key"}))
class DialogueLineEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 32)
  private DialogueTriggerType triggerType;

  @Column(name = "trigger_ply", nullable = false)
  private int triggerPly;

  @Column(name = "personality_key", nullable = false, length = 64)
  private String personalityKey;

  @Column(name = "personality_display_name", nullable = false, length = 80)
  private String personalityDisplayName;

  @Column(name = "dialogue_text", nullable = false, length = 280)
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DialogueEmotion emotion;

  @Enumerated(EnumType.STRING)
  @Column(name = "reaction_type", nullable = false, length = 32)
  private DialogueReactionType reactionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_source", nullable = false, length = 32)
  private AiResponseSource source;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DialogueLineEntity() {
    this.id = null;
  }

  DialogueLineEntity(
      UUID matchId,
      DialogueTriggerType triggerType,
      int triggerPly,
      String personalityKey,
      String personalityDisplayName,
      String text,
      DialogueEmotion emotion,
      DialogueReactionType reactionType,
      AiResponseSource source,
      Instant createdAt) {
    this.id = null;
    this.matchId = matchId;
    this.triggerType = triggerType;
    this.triggerPly = triggerPly;
    this.personalityKey = personalityKey;
    this.personalityDisplayName = personalityDisplayName;
    this.text = text;
    this.emotion = emotion;
    this.reactionType = reactionType;
    this.source = source;
    this.createdAt = createdAt;
  }

  // Add package-private accessors for every field used by DialoguePersistenceService.
}
```

Do not add a JPA relationship to `PersonalityEntity`; the Flyway foreign key is enough and avoids coupling entity graphs.

- [ ] **Step 4: Create the repository**

```java
interface DialogueLineRepository extends JpaRepository<DialogueLineEntity, Long> {

  Optional<DialogueLineEntity> findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
      UUID matchId,
      DialogueTriggerType triggerType,
      int triggerPly,
      String personalityKey);

  List<DialogueLineEntity> findAllByMatchIdOrderByIdAsc(UUID matchId);

  List<DialogueLineEntity> findTop4ByMatchIdOrderByIdDesc(UUID matchId);
}
```

- [ ] **Step 5: Implement the storage service**

Use this flow, without retries or asynchronous work:

```java
@Service
final class DialoguePersistenceService implements DialogueHistoryStore {

  private final DialogueLineRepository repository;
  private final PersonalityService personalityService;

  DialoguePersistenceService(
      DialogueLineRepository repository, PersonalityService personalityService) {
    this.repository = repository;
    this.personalityService = personalityService;
  }

  @Override
  @Transactional
  public Optional<PersistedDialogue> persistIfAbsent(
      UUID matchId,
      DialogueTriggerType triggerType,
      int triggerPly,
      GeneratedDialogue dialogue) {
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    Objects.requireNonNull(dialogue, "dialogue must not be null");

    if (repository
        .findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
            matchId, triggerType, triggerPly, dialogue.personalityKey())
        .isPresent()) {
      return Optional.empty();
    }

    String displayName =
        personalityService.requirePromptProfile(dialogue.personalityKey()).displayName();
    DialogueLineEntity entity =
        new DialogueLineEntity(
            matchId,
            triggerType,
            triggerPly,
            dialogue.personalityKey(),
            displayName,
            dialogue.text(),
            dialogue.emotion(),
            dialogue.reactionType(),
            dialogue.source(),
            Instant.now());
    return Optional.of(toApi(repository.save(entity)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PersistedDialogue> findAll(UUID matchId) {
    return repository.findAllByMatchIdOrderByIdAsc(matchId).stream()
        .map(DialoguePersistenceService::toApi)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DialogueHistoryLine> lastFour(UUID matchId) {
    List<DialogueLineEntity> newestFirst =
        repository.findTop4ByMatchIdOrderByIdDesc(matchId);
    List<DialogueHistoryLine> chronological = new ArrayList<>(newestFirst.size());
    for (int i = newestFirst.size() - 1; i >= 0; i--) {
      DialogueLineEntity row = newestFirst.get(i);
      chronological.add(
          new DialogueHistoryLine(
              row.triggerPly(),
              row.personalityKey(),
              row.personalityDisplayName(),
              row.text()));
    }
    return List.copyOf(chronological);
  }
}
```

Implement `toApi(...)` as a full field-for-field mapping. Require a non-null generated `id` before creating `PersistedDialogue`; fail fast if JPA returns an unsaved entity unexpectedly.

The database unique constraint remains the final guard against a programming mistake/race. Do not add retry machinery for unique violations in this hobby-project path.

- [ ] **Step 6: Run focused tests**

Run `DialoguePersistenceServiceTest`. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceServiceTest.java
git commit -m "feat: persist accepted dialogue history"
```

---

## Task 4: Add the Synchronous Match Dialogue Coordinator and Persisted Dialogue Event

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/DialoguePlayed.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchEvent.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`

**Interfaces:**
- Consumes: `DialogueGenerator` and `DialogueHistoryStore` from `ai :: api`.
- Produces: synchronous `onGameStart`, `onMove`, `onGameEnd`, and snapshot `history` behavior.
- Broadcast contract: only a newly persisted `PersistedDialogue` becomes `DialoguePlayed`.

- [ ] **Step 1: Write RED coordinator tests**

Cover these cases independently:

1. `onMove` passes exactly `historyStore.lastFour(matchId)` into `DialogueMoveRequest`.
2. mover/opponent keys map by color: White=`blaze` / Black=`vesper`; Black move reverses them.
3. a generated line is persisted before `MatchEventSink.publish(new DialoguePlayed(...))`.
4. `persistIfAbsent` returning empty does not broadcast a duplicate.
5. `AiResponseSource.DETERMINISTIC_FALLBACK` follows exactly the same persistence/broadcast path as provider output.
6. `DialogueGenerator` throwing returns normally and does not publish.
7. persistence throwing returns normally and does not publish.
8. an authority supplier that becomes false while generation is in flight causes the generated result to be discarded before `persistIfAbsent`.
9. `onGameStart`/`onGameEnd` tolerate one already-persisted speaker and only broadcast the newly inserted speaker when generation is retried.

Use Mockito `InOrder` to verify persist-before-publish.

- [ ] **Step 2: Run the focused test and confirm RED**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchDialogueCoordinatorTest test
```

- [ ] **Step 3: Add `DialoguePlayed` to the existing event family**

```java
package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import java.util.Objects;

public record DialoguePlayed(PersistedDialogue dialogue) implements MatchEvent {
  public DialoguePlayed {
    Objects.requireNonNull(dialogue, "dialogue must not be null");
  }
}
```

Update the sealed interface:

```java
public sealed interface MatchEvent
    permits MatchStarted, MovePlayed, DialoguePlayed, MatchStopped, MatchFinished {}
```

- [ ] **Step 4: Implement the coordinator with the temporary #44 seam isolated in one place**

Start the class with exactly these temporary keys:

```java
@Component
@Slf4j
final class MatchDialogueCoordinator {

  private static final String DEFAULT_WHITE_PERSONALITY = "blaze";
  private static final String DEFAULT_BLACK_PERSONALITY = "vesper";

  private final DialogueGenerator dialogueGenerator;
  private final DialogueHistoryStore historyStore;
  private final MatchEventSink matchEventSink;
```

Implement these methods:

```java
void onGameStart(UUID matchId, BooleanSupplier authoritative)
void onMove(UUID matchId, MovePlayed move, BooleanSupplier authoritative)
void onGameEnd(
    UUID matchId, GameResult result, int totalPlies, BooleanSupplier authoritative)
List<PersistedDialogue> history(UUID matchId)
```

`onGameStart`:

```java
List<GeneratedDialogue> generated =
    dialogueGenerator.generateStart(
        new DialogueStartRequest(
            DEFAULT_WHITE_PERSONALITY,
            DEFAULT_BLACK_PERSONALITY,
            historyStore.lastFour(matchId)));
persistAndPublish(
    matchId, DialogueTriggerType.GAME_START, 0, generated, authoritative);
```

`onMove` must build from the existing `MovePlayed` facts:

```java
String mover = personalityFor(move.player());
String opponent = personalityFor(move.player().opposite());
DialogueMoveRequest request =
    new DialogueMoveRequest(
        move.ply(),
        mover,
        opponent,
        move.notation().value(),
        move.capture(),
        move.check(),
        move.checkmate(),
        move.promotion(),
        move.evaluation(),
        historyStore.lastFour(matchId));

dialogueGenerator.generateMove(request)
    .ifPresent(
        generated ->
            persistAndPublish(
                matchId,
                DialogueTriggerType.MOVE,
                move.ply(),
                List.of(generated),
                authoritative));
```

`onGameEnd` maps outcomes without adding new outcome enums:

```java
DialogueOutcome whiteOutcome = switch (result) {
  case WHITE_WINS -> DialogueOutcome.VICTORY;
  case BLACK_WINS -> DialogueOutcome.DEFEAT;
  case DRAW -> DialogueOutcome.DRAW;
};
DialogueOutcome blackOutcome = switch (result) {
  case WHITE_WINS -> DialogueOutcome.DEFEAT;
  case BLACK_WINS -> DialogueOutcome.VICTORY;
  case DRAW -> DialogueOutcome.DRAW;
};
```

Then call `generateEnd(...)` with `historyStore.lastFour(matchId)` and persist with `GAME_END`, `triggerPly=totalPlies`.

`persistAndPublish` must check the supplied authority **after generation and immediately before each persistence attempt**:

```java
private void persistAndPublish(
    UUID matchId,
    DialogueTriggerType triggerType,
    int triggerPly,
    List<GeneratedDialogue> generated,
    BooleanSupplier authoritative) {
  for (GeneratedDialogue line : generated) {
    if (!authoritative.getAsBoolean()) {
      log.debug(
          "Discarding stale dialogue for match {} at {} ply {}",
          matchId,
          triggerType,
          triggerPly);
      return;
    }
    historyStore
        .persistIfAbsent(matchId, triggerType, triggerPly, line)
        .ifPresent(saved -> matchEventSink.publish(new DialoguePlayed(saved)));
  }
}
```

Wrap each public lifecycle method in a private `safeRun(...)`/`try-catch RuntimeException` boundary that logs a concise warning with match/ply and returns normally. Do not log prompts or raw provider responses.

`history(UUID)` should also fail soft for snapshot hydration:

```java
List<PersistedDialogue> history(UUID matchId) {
  try {
    return historyStore.findAll(matchId);
  } catch (RuntimeException exception) {
    log.warn("Dialogue history unavailable for match {}", matchId, exception);
    return List.of();
  }
}
```

- [ ] **Step 5: Run coordinator tests**

Expected: PASS.

- [ ] **Step 6: Run Modulith verification**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ApplicationModulesTest test
```

Expected: PASS. If it reports a game -> ai dependency violation, confirm the dependency targets only `ai :: api`; do not expose `ai.dialogue` implementation classes or bypass the named interface.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java \
        server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java
git commit -m "feat: coordinate persisted match dialogue"
```

---

## Task 5: Integrate Dialogue Into the Match Loop With a Stop/Resume Generation Guard

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`

**Interfaces:**
- Adds: one monotonic execution-generation token inside `MatchEngine`.
- Invariant: Stop invalidates all authority suppliers captured by the old `playUntilFinished()` invocation, even if Resume resets `stopRequested` before a slow provider returns.
- Ordering: move event -> dialogue workflow -> result handling/end dialogue -> pacing/next move.

- [ ] **Step 1: Write RED lifecycle tests before editing the loop**

Add focused tests for:

```java
@Test
void runsMoveDialogueAfterMoveBroadcastAndBeforePacing() {
  // Arrange one legal non-terminal move.
  // Use InOrder over matchEventSink, matchDialogueCoordinator, matchPacing.
  // Verify publish(MovePlayed) -> coordinator.onMove(...) -> waitBeforeNextMove().
}
```

```java
@Test
void stopInvalidatesAuthorityCapturedByRunningDialogue() {
  // Capture the BooleanSupplier passed to coordinator.onMove(...).
  // Assert true before stopCurrentMatch().
  // Call stopCurrentMatch().
  // Assert the same captured supplier is now false.
}
```

```java
@Test
void resumeGetsANewExecutionGeneration() {
  // Stop a running match, invoke playUntilFinished again, capture the new supplier,
  // and prove the old supplier remains false while the new one is true.
}
```

Also verify:

- game-start coordinator runs before the first `chooseMove`.
- terminal result invokes end dialogue before `MatchFinished` publication.
- max-plies draw invokes end dialogue.
- a dialogue coordinator runtime exception (defensive test) is swallowed by an engine-side safety wrapper and the match can still finish.

- [ ] **Step 2: Run the focused test and confirm RED**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchEngineTest test
```

- [ ] **Step 3: Add match identity to `MatchStarted`**

Change it to:

```java
public record MatchStarted(UUID matchId, PlayerColor sideToMove, BoardPosition position)
    implements MatchEvent {

  public MatchStarted {
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    Objects.requireNonNull(position, "position must not be null");
  }
}
```

Update `startNewMatch()` publication to:

```java
matchEventSink.publish(
    new MatchStarted(match.id(), match.sideToMove(), match.currentPosition()));
```

- [ ] **Step 4: Add the execution-generation state**

Add fields/dependency:

```java
private final MatchDialogueCoordinator matchDialogueCoordinator;
private final AtomicLong executionGeneration = new AtomicLong();
```

Inject `MatchDialogueCoordinator` in the constructor.

At the beginning of `playUntilFinished()` after obtaining/creating the match:

```java
long generation = executionGeneration.incrementAndGet();
stopRequested.set(false);
Match executionMatch = match;
```

Create the authority helper:

```java
private boolean isDialogueAuthorityCurrent(
    long generation, UUID matchId, int expectedPly) {
  Match authoritative = currentMatch.get();
  return executionGeneration.get() == generation
      && authoritative != null
      && authoritative.id().equals(matchId)
      && authoritative.moveCount() == expectedPly;
}
```

Do **not** use `stopRequested` alone for stale-response rejection because Resume intentionally resets it.

- [ ] **Step 5: Invalidate the old generation on Stop**

At the start of `stopCurrentMatch()`:

```java
executionGeneration.incrementAndGet();
```

Then keep the existing `stopRequested`/`MatchStopped` behavior.

This generation bump must occur even though provider calls are not interrupted.

- [ ] **Step 6: Run game-start dialogue before the first move**

Immediately before entering the loop, when `match.moveCount() == 0`, call:

```java
Match startMatch = match;
safeDialogue(
    () ->
        matchDialogueCoordinator.onGameStart(
            startMatch.id(),
            () -> isDialogueAuthorityCurrent(generation, startMatch.id(), 0)));
```

`safeDialogue(Runnable)` is a small engine-level final safety net:

```java
private void safeDialogue(Runnable action) {
  try {
    action.run();
  } catch (RuntimeException exception) {
    log.warn("Dialogue workflow failed; chess execution will continue", exception);
  }
}
```

The coordinator already fails soft; keep this wrapper because issue #43 explicitly requires dialogue failures never to escape into the chess loop.

- [ ] **Step 7: Integrate move dialogue at the approved point**

Refactor the existing `MovePlayed` creation into a local variable:

```java
MovePlayed movePlayed =
    new MovePlayed(
        recordedMove.sequenceNumber(),
        player,
        recordedMove.notation(),
        recordedMove.positionAfterMove(),
        recordedMove.details(),
        evaluationSwing);
matchEventSink.publish(movePlayed);
```

Immediately after the move publication and before result handling/pacing:

```java
Match dialogueMatch = match;
int dialoguePly = recordedMove.sequenceNumber();
safeDialogue(
    () ->
        matchDialogueCoordinator.onMove(
            dialogueMatch.id(),
            movePlayed,
            () ->
                isDialogueAuthorityCurrent(
                    generation, dialogueMatch.id(), dialoguePly)));
```

Do not move `currentMatch.set(match)` later; the committed board must remain authoritative before dialogue begins.

- [ ] **Step 8: Generate end dialogue from an authoritative finished state**

Change `finishMatch` to receive `generation` and perform this order:

```java
private Match finishMatch(Match match, GameResult result, long generation) {
  Match finishedMatch = match.finish(result);
  currentMatch.set(finishedMatch);

  safeDialogue(
      () ->
          matchDialogueCoordinator.onGameEnd(
              finishedMatch.id(),
              result,
              finishedMatch.moveCount(),
              () ->
                  isDialogueAuthorityCurrent(
                      generation, finishedMatch.id(), finishedMatch.moveCount())));

  matchEventSink.publish(
      new MatchFinished(result, finishedMatch.currentPosition(), finishedMatch.moveCount()));
  return finishedMatch;
}
```

Update both terminal call sites (normal terminal result and max-plies draw).

- [ ] **Step 9: Run MatchEngine tests and existing stop/resume regressions**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchEngineTest,MatchControlServiceTest test
```

Expected: PASS. Preserve every existing Stockfish stop/resume test.

- [ ] **Step 10: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java \
        server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java
git commit -m "feat: integrate dialogue into match lifecycle"
```

---

## Task 6: Hydrate and Broadcast Persisted Dialogue Through Backend Contracts

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchSnapshot.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/DialogueResponse.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/DialoguePlayedMessage.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStateMessage.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageType.java`
- Modify corresponding mapper/controller/WebSocket tests.

**Interfaces:**
- REST/snapshot exposes `matchId` plus chronological `dialogue`.
- WebSocket adds `DIALOGUE_PLAYED` only after persistence.
- Reconnect `MATCH_STATE` contains the same persisted dialogue rows as REST hydration.

- [ ] **Step 1: Write RED mapper/hydration tests**

Add tests proving:

- `MatchResponseMapper.map(snapshot)` returns the match UUID and dialogue in snapshot order.
- `MatchStateMessage.from(snapshot)` returns the same UUID/history.
- `DialoguePlayed` maps to `DIALOGUE_PLAYED` with every persistence field unchanged.
- `MatchStarted` maps `matchId` into its payload.
- WebSocket initial hydration sends persisted dialogue chronologically.
- no duplicate dialogue rows are introduced during reconnect; the snapshot is authoritative.

- [ ] **Step 2: Extend `MatchSnapshot`**

Change it to:

```java
public record MatchSnapshot(
    Match match,
    boolean running,
    MatchStartAvailability startAvailability,
    List<PersistedDialogue> dialogue) {

  public MatchSnapshot {
    Objects.requireNonNull(match, "match must not be null");
    Objects.requireNonNull(startAvailability, "startAvailability must not be null");
    dialogue = dialogue != null ? List.copyOf(dialogue) : List.of();
  }
}
```

Keep compatibility constructors used by existing tests, defaulting dialogue to `List.of()`.

- [ ] **Step 3: Hydrate dialogue in `MatchControlService.snapshot(...)`**

Inject `MatchDialogueCoordinator` and construct:

```java
return new MatchSnapshot(
    match,
    executionGuard.isRunning(),
    executionGuard.availability(),
    matchDialogueCoordinator.history(match.id()));
```

Because `history(...)` fails soft, a temporary database read failure must not make match state unreadable.

- [ ] **Step 4: Add the REST response DTO**

Create `DialogueResponse` with these exact fields:

```java
public record DialogueResponse(
    long id,
    UUID matchId,
    DialogueTriggerType triggerType,
    int triggerPly,
    String personalityKey,
    String personalityDisplayName,
    String text,
    DialogueEmotion emotion,
    DialogueReactionType reactionType,
    AiResponseSource source,
    Instant createdAt) {

  public static DialogueResponse from(PersistedDialogue dialogue) {
    return new DialogueResponse(
        dialogue.id(),
        dialogue.matchId(),
        dialogue.triggerType(),
        dialogue.triggerPly(),
        dialogue.personalityKey(),
        dialogue.personalityDisplayName(),
        dialogue.text(),
        dialogue.emotion(),
        dialogue.reactionType(),
        dialogue.source(),
        dialogue.createdAt());
  }
}
```

Add `UUID matchId` and `List<DialogueResponse> dialogue` to `MatchResponse`; defensively copy both `moves` and `dialogue`.

Update `MatchResponseMapper` to map `snapshot.dialogue()` with `DialogueResponse::from`.

- [ ] **Step 5: Extend WebSocket state and live message types**

Add:

```java
DIALOGUE_PLAYED,
```

to `MatchStreamMessageType`.

`MatchStartedMessage` gains `UUID matchId`.

`MatchStateMessage` gains `UUID matchId` and `List<DialogueResponse> dialogue`, mapping from `snapshot.dialogue()`.

Create `DialoguePlayedMessage` with the same fields as `DialogueResponse` and a `from(PersistedDialogue)` factory.

Add mapper case:

```java
case DialoguePlayed dialogue ->
    new MatchStreamMessage<>(
        MatchStreamMessageType.DIALOGUE_PLAYED,
        DialoguePlayedMessage.from(dialogue.dialogue()));
```

Update the `MatchStarted` mapping to include `started.matchId()`.

- [ ] **Step 6: Run focused backend contract tests**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchResponseMapperTest,MatchStreamMessageMapperTest,MatchWebSocketIntegrationTest,MatchControlServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application \
        server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web \
        server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/game
git commit -m "feat: expose persisted dialogue in match transport"
```

---

## Task 7: Store Dialogue on the Client Without Rendering It Yet

**Files:**
- Modify: `client/src/types/match.ts`
- Modify: `client/src/services/matchSocket.ts`
- Modify: `client/src/services/matchViewer.messages.ts`
- Modify: `client/src/services/matchViewer.messages.test.ts`
- Modify: `client/src/store/matchViewerStore.ts`
- Modify: `client/src/store/matchViewerStore.test.ts`

**Interfaces:**
- Client stores `currentMatchId` and persisted dialogue rows separately from `activities`.
- `MATCH_STATE` replaces local dialogue with authoritative chronological history.
- `DIALOGUE_PLAYED` deduplicates by persisted row `id` and ignores a payload for another match UUID.
- #45 later decides how to merge/render this data in the activity feed.

- [ ] **Step 1: Write RED parser/store tests**

Add tests proving:

1. parser accepts a valid `DIALOGUE_PLAYED` payload and rejects missing/invalid `matchId`, `id`, `triggerPly`, `text`, or `createdAt` basics.
2. `MATCH_STATE` requires `matchId` and a dialogue array.
3. hydration stores dialogue in ascending `id` order.
4. replaying the same `DIALOGUE_PLAYED` row does not duplicate it.
5. a line whose `matchId` does not equal the current match is ignored.
6. `MATCH_STARTED` sets the new `matchId` and clears old dialogue.
7. `NO_MATCH` clears `matchId` and dialogue.
8. existing `activities` remain move/start/finish-only in #43.

- [ ] **Step 2: Extend TypeScript transport types**

Add:

```ts
export type DialogueTriggerType = "GAME_START" | "MOVE" | "GAME_END";
export type DialogueEmotion =
  | "NEUTRAL"
  | "CONFIDENT"
  | "AMUSED"
  | "ANNOYED"
  | "CALM"
  | "TRIUMPHANT"
  | "DEFIANT";
export type DialogueReactionType =
  | "GAME_START"
  | "MOVE_REACTION"
  | "VICTORY"
  | "DEFEAT"
  | "DRAW";
export type AiResponseSource = "GROQ" | "GEMINI" | "DETERMINISTIC_FALLBACK";

export interface DialogueResponse {
  id: number;
  matchId: string;
  triggerType: DialogueTriggerType;
  triggerPly: number;
  personalityKey: string;
  personalityDisplayName: string;
  text: string;
  emotion: DialogueEmotion;
  reactionType: DialogueReactionType;
  source: AiResponseSource;
  createdAt: string;
}
```

Add `matchId: string` and `dialogue: DialogueResponse[]` to `MatchResponse`.

Add `matchId` to `MatchStartedMessage.payload`.

Add:

```ts
export interface DialoguePlayedMessage extends BaseMessage {
  type: "DIALOGUE_PLAYED";
  payload: DialogueResponse;
}
```

and include it in `MatchStreamMessage`.

Add `"DIALOGUE_PLAYED"` to `MatchStreamMessageType` in `matchSocket.ts`.

- [ ] **Step 3: Validate the new transport shape in `parseMatchMessage`**

Add a helper:

```ts
function isDialogue(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.id === "number" &&
    typeof value.matchId === "string" &&
    typeof value.triggerType === "string" &&
    typeof value.triggerPly === "number" &&
    typeof value.personalityKey === "string" &&
    typeof value.personalityDisplayName === "string" &&
    typeof value.text === "string" &&
    typeof value.emotion === "string" &&
    typeof value.reactionType === "string" &&
    typeof value.source === "string" &&
    typeof value.createdAt === "string"
  );
}
```

Add `DIALOGUE_PLAYED` to the valid type list and validate it with `isDialogue(msg.payload)`.

For `MATCH_STATE`, additionally require `typeof msg.payload.matchId === "string"`, `Array.isArray(msg.payload.dialogue)`, and `msg.payload.dialogue.every(isDialogue)`.

For `MATCH_STARTED`, require `matchId`.

Do not add enum-validation complexity beyond the existing parser style in this issue.

- [ ] **Step 4: Add dialogue state and deterministic deduplication**

Extend store state:

```ts
currentMatchId?: string;
dialogue: DialogueResponse[];
```

Initial state: `dialogue: []`.

`MATCH_STARTED`:

```ts
currentMatchId: msg.payload.matchId,
dialogue: [],
```

`MATCH_STATE`:

```ts
currentMatchId: msg.payload.matchId,
dialogue: [...msg.payload.dialogue].sort((a, b) => a.id - b.id),
```

`DIALOGUE_PLAYED`:

```ts
case "DIALOGUE_PLAYED":
  set((state) => {
    if (
      state.currentMatchId !== undefined &&
      msg.payload.matchId !== state.currentMatchId
    ) {
      return state;
    }

    const withoutDuplicate = state.dialogue.filter(
      (line) => line.id !== msg.payload.id,
    );
    return {
      currentMatchId: state.currentMatchId ?? msg.payload.matchId,
      dialogue: [...withoutDuplicate, msg.payload].sort((a, b) => a.id - b.id),
    };
  });
  break;
```

`NO_MATCH` clears both fields.

Do **not** add `DIALOGUE` to `MatchActivityKind` in #43.

- [ ] **Step 5: Format and run focused frontend tests**

From `client/`:

```text
npm run format
npm run test -- matchViewer.messages.test.ts matchViewerStore.test.ts
npm run typecheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add client/src/types/match.ts \
        client/src/services/matchSocket.ts \
        client/src/services/matchViewer.messages.ts \
        client/src/services/matchViewer.messages.test.ts \
        client/src/store/matchViewerStore.ts \
        client/src/store/matchViewerStore.test.ts
git commit -m "feat: hydrate dialogue transport state"
```

---

## Task 8: Prove PostgreSQL Idempotency, Lifecycle Ordering, and Final Acceptance

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistencePostgresIT.java`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`
- Modify: `docs/BUILD_AND_VERIFY.md`
- Revisit tests from Tasks 3–7 only when an acceptance gap is found.

**Interfaces:**
- PostgreSQL 17 proves V4, the FK/unique constraint, chronological query order, and last-four behavior against the real database.
- Lifecycle tests prove provider/persistence failures and stale responses cannot stop or corrupt chess execution.

- [ ] **Step 1: Add a real-PostgreSQL integration test that is explicit, not part of normal `verify`**

Name the class `DialoguePersistencePostgresIT` so Maven Surefire does not run it during ordinary `verify`; run it explicitly in this task against an isolated disposable PostgreSQL container.

Use a JPA test slice with the real datasource and Flyway migrations. For Spring Boot 4 use the same `...test.autoconfigure...` package convention already used by this repository's Boot 4 test slices.

Test properties must point only at the disposable port/database used below:

```java
@DataJpaTest(
    properties = {
      "spring.datasource.url=jdbc:postgresql://localhost:55433/aichessrivals_it",
      "spring.datasource.username=postgres",
      "spring.datasource.password=secretpassword",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DialoguePersistenceService.class, PersonalityService.class})
class DialoguePersistencePostgresIT {
```

Write two tests:

```java
@Test
void persistsOnlyOneRowForTheSameTriggerAndSpeaker() {
  UUID matchId = UUID.randomUUID();
  GeneratedDialogue line = generatedBlazeLine();

  assertThat(store.persistIfAbsent(matchId, DialogueTriggerType.MOVE, 12, line)).isPresent();
  assertThat(store.persistIfAbsent(matchId, DialogueTriggerType.MOVE, 12, line)).isEmpty();
  assertThat(store.findAll(matchId)).hasSize(1);
}
```

```java
@Test
void returnsChronologicalHistoryAndOnlyLastFourContextLines() {
  UUID matchId = UUID.randomUUID();
  for (int ply = 1; ply <= 6; ply++) {
    String personality = ply % 2 == 0 ? "blaze" : "vesper";
    store.persistIfAbsent(
        matchId,
        DialogueTriggerType.MOVE,
        ply,
        generated(personality, "line-" + ply));
  }

  assertThat(store.findAll(matchId))
      .extracting(PersistedDialogue::triggerPly)
      .containsExactly(1, 2, 3, 4, 5, 6);
  assertThat(store.lastFour(matchId))
      .extracting(DialogueHistoryLine::triggeringPly)
      .containsExactly(3, 4, 5, 6);
}
```

Do not use the normal development PostgreSQL volume/database for this test.

- [ ] **Step 2: Start an isolated PostgreSQL 17 container**

PowerShell:

```powershell
docker rm -f ai-chess-rivals-dialogue-it 2>$null
docker run --name ai-chess-rivals-dialogue-it --rm -d `
  -e POSTGRES_DB=aichessrivals_it `
  -e POSTGRES_PASSWORD=secretpassword `
  -p 55433:5432 postgres:17-alpine

do {
  Start-Sleep -Seconds 1
  docker exec ai-chess-rivals-dialogue-it pg_isready -U postgres -d aichessrivals_it
} until ($LASTEXITCODE -eq 0)
```

- [ ] **Step 3: Run the real-database test**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePersistencePostgresIT test
```

Expected: PASS with Flyway applying V1–V4 and Hibernate `validate` succeeding.

If V4 fails on PostgreSQL, fix V4/entity mapping; do not introduce H2 or Testcontainers as a workaround.

- [ ] **Step 4: Stop the disposable database**

```powershell
docker stop ai-chess-rivals-dialogue-it
```

- [ ] **Step 5: Add/confirm lifecycle resilience coverage**

Before final verification, confirm tests explicitly prove all acceptance boundaries:

- `MatchDialogueCoordinatorTest`: generator exception -> no escape; persistence exception -> no escape; fallback persisted; recent four passed; duplicate not broadcast; stale authority discarded.
- `MatchEngineTest`: move broadcast -> dialogue -> pacing order; game start before first move; end dialogue before `MATCH_FINISHED`; stop invalidates old generation; resume uses new generation; second `Match.newGame()` has a different UUID.
- `MatchWebSocketIntegrationTest`: reconnect state includes chronological persisted dialogue once.
- client store test: duplicate live message deduped and prior-match message ignored.

If one of these assertions is missing, add the smallest focused test to the existing listed test class. Do not create a generic end-to-end harness.

- [ ] **Step 6: Update technical documentation**

In `docs/AI Chess Rivals - Tech Stack.md`, document only the implemented #43 facts:

- V4 `dialogue_line` schema and unique key.
- match UUID is in-memory identity used to partition dialogue history.
- synchronous lifecycle order.
- recent context = last four persisted rows.
- deterministic fallbacks are persisted with `DETERMINISTIC_FALLBACK` source.
- Stop/Resume execution-generation guard drops late results.
- temporary Blaze/Vesper runtime pairing exists only until #44.
- frontend transport/store hydration exists; rendering remains #45.

In `docs/BUILD_AND_VERIFY.md`, add the optional explicit disposable-PostgreSQL command for `DialoguePersistencePostgresIT`; keep the normal root verifier independent of Docker/database availability.

- [ ] **Step 7: Apply formatters before verification**

Backend:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
```

Frontend:

```powershell
cd client
npm run format
cd ..
```

- [ ] **Step 8: Run focused backend suites**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePersistenceServiceTest,MatchDialogueCoordinatorTest,MatchEngineTest,MatchControlServiceTest,MatchResponseMapperTest,MatchStreamMessageMapperTest,MatchWebSocketIntegrationTest,ApplicationModulesTest test
```

Expected: PASS.

- [ ] **Step 9: Run the complete repository verifier**

```powershell
.\scripts\verify.ps1
```

Expected: backend formatting/Error Prone/tests/Modulith/SpotBugs PASS; frontend format/typecheck/lint/tests/build PASS.

- [ ] **Step 10: Review the diff against scope**

```powershell
git diff --check master...HEAD
git status --short
git diff --stat master...HEAD
```

Reject unrelated changes. In particular, no Stockfish/provider/native/CI/UI styling files should have changed.

- [ ] **Step 11: Commit documentation/integration-test evidence**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistencePostgresIT.java \
        docs/AI\ Chess\ Rivals\ -\ Tech\ Stack.md \
        docs/BUILD_AND_VERIFY.md
git commit -m "test: verify dialogue persistence lifecycle"
```

- [ ] **Step 12: Push and use hosted CI as the final build gate**

```bash
git push -u origin feature/issue-43-dialogue-lifecycle
```

Open a PR with `Closes #43` and include:

- focused dialogue/lifecycle test results,
- disposable PostgreSQL integration-test result,
- root verifier result,
- fresh hosted backend/native-image CI result.

Do not mark #43 complete before the fresh PR head is green.

---

## Acceptance Traceability

- **Every accepted dialogue line is persisted once** -> V4 unique constraint + `persistIfAbsent` + persistence unit/PostgreSQL tests.
- **Dialogue tied to correct match/event/ply** -> stable `Match.id`, trigger enum/ply columns, coordinator mapping, transport tests.
- **Next move waits only for bounded dialogue** -> synchronous coordinator; provider timeouts remain #38/#42; no extra unbounded executor/future.
- **Refresh/reconnect restores chronological history without duplication** -> repository `id ASC`, `MatchSnapshot.dialogue`, WebSocket `MATCH_STATE`, client replacement/dedup tests.
- **Stop/Resume and second match cannot leak stale dialogue** -> execution-generation guard + UUID partition + client match-id guard.
- **Both-provider failure still finishes the game** -> existing deterministic fallback + identical persistence path + coordinator/engine fail-soft tests.
- **Database and lifecycle integration tests cover idempotency and ordering** -> `DialoguePersistencePostgresIT` + coordinator/engine/WebSocket tests.

## Mandatory Stop Conditions for Luna

Stop implementation and report concrete evidence instead of broadening architecture if any of these happen:

1. Spring Modulith reports a cycle that cannot be resolved while game depends only on `ai :: api`.
2. The existing `DialogueGenerator` contract cannot express a required #43 event without modifying prompt/provider internals.
3. Stop/Resume exposes a pre-existing general chess-task concurrency bug unrelated to dialogue; capture the exact failing test/sequence rather than redesigning `MatchControlService` speculatively.
4. Native CI reports a new GraalVM/JPA reflection failure; capture the exact type/member/stack before adding runtime hints.
5. PostgreSQL V4 validation contradicts the entity mapping; fix the narrow schema/entity mismatch only.

Do not respond to any of these by adding Kafka, Redis, a workflow engine, another executor pool, a persisted match aggregate, chat memory, Testcontainers, H2, or a generic event-sourcing layer.
