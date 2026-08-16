# Personality Selection and Random Rivalry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the owner choose distinct White and Black system personalities (or randomize a distinct pair), validate those choices server-side, bind them immutably to the match, use them for dialogue, and restore/display them through REST and WebSocket hydration.

**Architecture:** Reuse the existing PostgreSQL-backed personality roster as the source of selectable personalities. Add one small `ai :: api` lookup contract so the `game` module can validate stable personality keys without depending on `ai.personality` internals; store the resolved key/display-name pair as immutable metadata on the in-memory `Match`; pass that metadata to dialogue and every authoritative match snapshot/start payload. The frontend loads the roster only for pre-match setup, sends two keys to `/match/start`, and thereafter treats match payloads as authoritative for the chosen identities.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring WebMVC, Spring Data JPA, Spring Modulith 2.1.0, PostgreSQL 17, React 19.2.7, TypeScript 6.0.2, Vite 8.1.0, Zustand 5.0.14, Axios 1.18.1, Vitest 4.1.10, Testing Library, existing shadcn/Tailwind components, JUnit 5, Mockito, AssertJ, MockMvc.

## Source of Truth

- Issue: `#44 Phase 2: Add personality selection and random rivalry setup`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Dependencies already complete: `#40` personality persistence/read-only roster API and `#41` four seeded system personalities
- Approved design: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Do not add a Maven or npm dependency.
- Do not add a match table or general match-history persistence layer. The current authoritative `Match` is in memory; for this issue, “persist with the match” means the selected identity metadata is part of that aggregate for its complete lifecycle and survives browser refresh/reconnect through the existing snapshot path. Backend-process restart recovery remains out of scope.
- Do not add personality create/edit/delete behavior, favorites, matchup history, filters, generated avatars, or mirror-match support.
- A new match requires exactly two non-blank, distinct personality keys.
- Both keys must resolve to currently active system personalities through the existing personality repository/service path.
- Once a match is created, White/Black identities are immutable through stop/resume and completion.
- Resuming a stopped match uses the identities already stored on that match. Do not re-resolve them against the active roster; a personality becoming inactive later must not silently change or prevent resume of an already-created match.
- `POST /api/v1/match/start` accepts JSON `{ "whitePersonalityKey": "...", "blackPersonalityKey": "..." }` for both new starts and resumes. On resume, requested keys must exactly match the stored match keys or the server returns `400`.
- Unknown, inactive, non-system, blank, duplicate, or resume-mismatched selections return controlled `400 Bad Request`; they must not become `500` errors.
- Keep `game` module dependencies inside its existing `{"chess :: api", "ai :: api"}` boundary. Never import `dev.krishnamurti.ai_chess_rivals.ai.personality.*` from `game`.
- Remove `MatchDialogueCoordinator` hard-coded `blaze`/`vesper` defaults. Every start/move/end dialogue request uses the personality keys stored on the authoritative match.
- REST `MatchResponse`, WebSocket `MATCH_STATE`, and WebSocket `MATCH_STARTED` payloads carry both selected identities as `{ key, displayName }`.
- Frontend pre-match roster loading uses existing `GET /api/v1/personalities` and preserves backend order.
- Randomization always returns two distinct entries when at least two personalities exist. Do not use retry loops; choose the second index from `length - 1` and skip the first index deterministically.
- Use native `<select>` controls styled with existing Tailwind classes. Do not scaffold another shadcn component for two dropdowns.
- Disable rivalry editing while a match is running or stopped. A stopped match resumes with its stored pair.
- Loading, roster API failure, and fewer-than-two-personality states are explicit. Roster failure blocks **new** match creation, but must not block resuming a stopped match whose identities are already known from the authoritative snapshot.
- Apply backend/frontend formatting before repository verification.

## File Map

### Create

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonality.java` — minimal cross-module identity (`key`, `displayName`).
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonalityCatalog.java` — named-interface lookup contract consumed by `game`.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchRivalry.java` — immutable White/Black identity metadata.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/InvalidPersonalitySelectionException.java` — controlled selection validation failure.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/StartMatchRequest.java` — two-key JSON request.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchPersonalityResponse.java` — `{key, displayName}` payload type shared by REST/WebSocket DTOs.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/TestMatchFixtures.java` — one test-only rivalry fixture used while updating existing tests to the new explicit `Match.newGame(rivalry)` contract.
- `client/src/services/personalityApi.ts`
- `client/src/services/personalityApi.test.ts`
- `client/src/features/admin/rivalrySelection.ts`
- `client/src/features/admin/rivalrySelection.test.ts`
- `client/src/features/admin/RivalrySetup.tsx`
- `client/src/features/admin/RivalrySetup.test.tsx`
- `client/src/features/match-viewer/components/PlayerStrip.test.tsx`

### Modify

Backend production:

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerAdvice.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStateMessage.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java`

Backend tests directly affected by the new match factory/DTO contracts:

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlServiceTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/StockfishPlayerTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/OwnerTokenInterceptorTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchWebSocketHandlerTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`

Frontend:

- `client/src/types/match.ts`
- `client/src/services/adminMatchApi.ts`
- `client/src/services/adminMatchApi.test.ts`
- `client/src/store/matchViewerStore.ts`
- `client/src/store/matchViewerStore.test.ts`
- `client/src/features/admin/MatchAdminControls.tsx`
- `client/src/features/admin/MatchAdminControls.test.tsx`
- `client/src/features/match-viewer/components/PlayerStrip.tsx`

### Explicitly Do Not Modify

- `server/src/main/resources/db/migration/**`
- `server/pom.xml`
- `client/package.json`
- Stockfish/evaluation behavior.
- AI provider configuration/prompt templates except indirectly through the selected keys already consumed by existing dialogue generation.

---

### Task 1: Expose a Minimal Selectable-Personality Lookup Through `ai :: api`

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonality.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonalityCatalog.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java`

**Interfaces:**
- Produces `public record SelectablePersonality(String key, String displayName)`.
- Produces `public interface SelectablePersonalityCatalog { Optional<SelectablePersonality> findSelectable(String personalityKey); }`.
- `PersonalityService` implements the catalog while preserving `listSelectable()` and `requirePromptProfile()`.

- [ ] **Step 1: Add the failing catalog tests**

Add these imports to `PersonalityServiceTest`:

```java
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonality;
import java.util.Optional;
```

Add these tests:

```java
@Test
void findsSelectablePersonalityByStableKey() {
  PersonalityEntity blaze = personality("blaze", "Blaze", 10, true, true);
  when(personalityRepository.findByPersonalityKeyAndSystemTrueAndActiveTrue("blaze"))
      .thenReturn(Optional.of(blaze));

  PersonalityService service = new PersonalityService(personalityRepository);

  assertThat(service.findSelectable("blaze"))
      .contains(new SelectablePersonality("blaze", "Blaze"));
}

@Test
void returnsEmptyWhenPersonalityIsNotSelectable() {
  when(personalityRepository.findByPersonalityKeyAndSystemTrueAndActiveTrue("retired"))
      .thenReturn(Optional.empty());

  PersonalityService service = new PersonalityService(personalityRepository);

  assertThat(service.findSelectable("retired")).isEmpty();
}

@Test
void returnsEmptyForBlankLookupKey() {
  PersonalityService service = new PersonalityService(personalityRepository);

  assertThat(service.findSelectable(" ")).isEmpty();
  verifyNoInteractions(personalityRepository);
}
```

Add the existing Mockito static import for `verifyNoInteractions` if absent.

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest test
```

Expected: compilation fails because `SelectablePersonality`, `SelectablePersonalityCatalog`, and `findSelectable` do not exist.

- [ ] **Step 3: Create `SelectablePersonality`**

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Objects;

public record SelectablePersonality(String key, String displayName) {
  public SelectablePersonality {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(displayName, "displayName must not be null");
    if (key.isBlank()) {
      throw new IllegalArgumentException("key must not be blank");
    }
    if (displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
  }
}
```

- [ ] **Step 4: Create `SelectablePersonalityCatalog`**

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Optional;

public interface SelectablePersonalityCatalog {
  Optional<SelectablePersonality> findSelectable(String personalityKey);
}
```

- [ ] **Step 5: Implement the catalog in `PersonalityService`**

Add imports:

```java
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonality;
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonalityCatalog;
import java.util.Optional;
```

Change the declaration and add the method:

```java
@Service
public class PersonalityService implements SelectablePersonalityCatalog {

  @Override
  public Optional<SelectablePersonality> findSelectable(String personalityKey) {
    if (personalityKey == null || personalityKey.isBlank()) {
      return Optional.empty();
    }
    return personalityRepository
        .findByPersonalityKeyAndSystemTrueAndActiveTrue(personalityKey)
        .map(entity -> new SelectablePersonality(entity.personalityKey(), entity.displayName()));
  }
```

Keep the existing constructor, `listSelectable()`, and `requirePromptProfile()` unchanged.

- [ ] **Step 6: Run the focused test and confirm GREEN**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonality.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonalityCatalog.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java
git commit -m "feat: expose selectable personality catalog"
```

---

### Task 2: Make Rivalry Identity an Immutable Part of `Match` and Dialogue

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchRivalry.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/TestMatchFixtures.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java`
- Modify tests listed in the File Map that call `Match.newGame()` or `new Match(...)`.

**Interfaces:**
- Produces `MatchRivalry(String whiteKey, String whiteDisplayName, String blackKey, String blackDisplayName)`.
- Produces `Match.newGame(MatchRivalry rivalry)`; remove the no-argument `Match.newGame()` factory.
- Produces `Match.rivalry()`; `recordMove()` and `finish()` retain it unchanged.
- Produces `MatchEngine.startNewMatch(MatchRivalry rivalry)`.
- `MatchStarted` carries the rivalry.
- `MatchDialogueCoordinator` receives the rivalry explicitly for start/move/end.

- [ ] **Step 1: Add failing domain tests with the repository's real `MoveDetails` helper**

In `MatchTest`, add:

```java
private static final MatchRivalry RIVALRY =
    new MatchRivalry("blaze", "Blaze", "vesper", "Vesper");
```

Change the existing test factories in this file from `Match.newGame()` to `Match.newGame(RIVALRY)`.

Add:

```java
@Test
void keepsRivalryAcrossImmutableTransitions() {
  Match started = Match.newGame(RIVALRY);
  BoardPosition positionAfterMove =
      new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1");

  Match moved =
      started.recordMove(
          new MoveNotation("e2e4"), positionAfterMove, quietPawnMoveDetails());
  Match finished = moved.finish(GameResult.DRAW);

  assertEquals(RIVALRY, started.rivalry());
  assertEquals(RIVALRY, moved.rivalry());
  assertEquals(RIVALRY, finished.rivalry());
}

@Test
void rivalryRejectsMirrorMatch() {
  IllegalArgumentException error =
      assertThrows(
          IllegalArgumentException.class,
          () -> new MatchRivalry("blaze", "Blaze", "blaze", "Blaze"));

  assertEquals("White and Black personalities must be distinct", error.getMessage());
}
```

For every direct `new Match(...)` in `MatchTest`, append `RIVALRY` as the final constructor argument after `result`.

- [ ] **Step 2: Run `MatchTest` and confirm RED**

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchTest test
```

Expected: compilation failure because rivalry support does not exist yet.

- [ ] **Step 3: Create `MatchRivalry`**

```java
package dev.krishnamurti.ai_chess_rivals.game.domain;

import java.util.Objects;

public record MatchRivalry(
    String whiteKey,
    String whiteDisplayName,
    String blackKey,
    String blackDisplayName) {

  public MatchRivalry {
    requireText(whiteKey, "whiteKey");
    requireText(whiteDisplayName, "whiteDisplayName");
    requireText(blackKey, "blackKey");
    requireText(blackDisplayName, "blackDisplayName");
    if (whiteKey.equals(blackKey)) {
      throw new IllegalArgumentException("White and Black personalities must be distinct");
    }
  }

  public String personalityKey(PlayerColor color) {
    Objects.requireNonNull(color, "color must not be null");
    return color == PlayerColor.WHITE ? whiteKey : blackKey;
  }

  public String displayName(PlayerColor color) {
    Objects.requireNonNull(color, "color must not be null");
    return color == PlayerColor.WHITE ? whiteDisplayName : blackDisplayName;
  }

  private static void requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
```

- [ ] **Step 4: Thread rivalry through `Match`**

Add the field:

```java
private final MatchRivalry rivalry;
```

Change the UUID constructor to:

```java
public Match(
    UUID id,
    PlayerColor sideToMove,
    BoardPosition currentPosition,
    List<Move> moves,
    GameStatus status,
    GameResult result,
    MatchRivalry rivalry) {
  this.id = Objects.requireNonNull(id, "id must not be null");
  this.sideToMove = Objects.requireNonNull(sideToMove, "sideToMove must not be null");
  this.currentPosition = Objects.requireNonNull(currentPosition, "currentPosition must not be null");
  this.moves = List.copyOf(Objects.requireNonNull(moves, "moves must not be null"));
  this.status = Objects.requireNonNull(status, "status must not be null");
  if (status == GameStatus.FINISHED && result == null) {
    throw new IllegalArgumentException("result is required when status is FINISHED");
  }
  if (status != GameStatus.FINISHED && result != null) {
    throw new IllegalArgumentException("result is only allowed when status is FINISHED");
  }
  this.result = result;
  this.rivalry = Objects.requireNonNull(rivalry, "rivalry must not be null");
}
```

Change the convenience constructor to take `MatchRivalry rivalry` last and delegate it to the UUID constructor.

Replace `newGame()` with:

```java
public static Match newGame(MatchRivalry rivalry) {
  return new Match(
      UUID.randomUUID(),
      PlayerColor.WHITE,
      BoardPosition.STARTING_POSITION,
      List.of(),
      GameStatus.IN_PROGRESS,
      null,
      rivalry);
}
```

Add:

```java
public MatchRivalry rivalry() {
  return rivalry;
}
```

In both `recordMove(...)` and `finish(...)`, append `rivalry` to the `new Match(...)` constructor call. Do not create a copy with different identity data.

- [ ] **Step 5: Create one test fixture for non-personality-focused tests**

Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/TestMatchFixtures.java`:

```java
package dev.krishnamurti.ai_chess_rivals.game;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MatchRivalry;

public final class TestMatchFixtures {

  public static final MatchRivalry TEST_RIVALRY =
      new MatchRivalry("white-test", "White Test", "black-test", "Black Test");

  private TestMatchFixtures() {}

  public static Match newMatch() {
    return Match.newGame(TEST_RIVALRY);
  }
}
```

- [ ] **Step 6: Update all remaining test-only `Match.newGame()` calls mechanically**

Run:

```bash
rg -n "Match\.newGame\(\)|new Match\(" server/src/test/java/dev/krishnamurti/ai_chess_rivals/game
```

For files other than `MatchTest`:

- replace `Match.newGame()` with `TestMatchFixtures.newMatch()`;
- add `import dev.krishnamurti.ai_chess_rivals.game.TestMatchFixtures;`;
- for direct `new Match(...)`, add `TestMatchFixtures.TEST_RIVALRY` as the final constructor argument.

The known affected tests on current `master` are:

```text
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlServiceTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/StockfishPlayerTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/OwnerTokenInterceptorTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchWebSocketHandlerTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java
```

After replacements, rerun the `rg` command. Expected: no no-argument `Match.newGame()` remains under `server/src/test/.../game`.

- [ ] **Step 7: Make `MatchEngine` require a rivalry for new matches**

Add `MatchRivalry` import and change:

```java
public synchronized Match startNewMatch(MatchRivalry rivalry) {
  Objects.requireNonNull(rivalry, "rivalry must not be null");
  Match existingMatch = currentMatch.get();
  if (existingMatch != null && existingMatch.isInProgress()) {
    throw new IllegalStateException(
        "Cannot start a new match while another match is in progress");
  }

  stopRequested.set(false);
  Match match = Match.newGame(rivalry);
```

Keep the rest of the existing Stockfish/evaluation initialization, but change the start event publication to:

```java
matchEventSink.publish(
    new MatchStarted(
        match.id(), match.sideToMove(), match.currentPosition(), match.rivalry()));
```

At the beginning of `playUntilFinished()`, replace the implicit create path:

```java
Match match = currentMatch.get();
if (match == null) {
  throw new IllegalStateException("No match has been started");
}
```

In `buildPositionOccurrences`, replace:

```java
recordPositionOccurrence(positionOccurrences, Match.newGame().currentPosition());
```

with:

```java
recordPositionOccurrence(positionOccurrences, BoardPosition.STARTING_POSITION);
```

Add the `BoardPosition` import if required.

- [ ] **Step 8: Remove hard-coded dialogue personalities**

Delete:

```java
private static final String DEFAULT_WHITE_PERSONALITY = "blaze";
private static final String DEFAULT_BLACK_PERSONALITY = "vesper";
```

Change coordinator signatures to:

```java
void onGameStart(UUID matchId, MatchRivalry rivalry, BooleanSupplier authoritative)

void onMove(
    UUID matchId,
    MatchRivalry rivalry,
    MovePlayed move,
    BooleanSupplier authoritative)

void onGameEnd(
    UUID matchId,
    MatchRivalry rivalry,
    GameResult result,
    int totalPlies,
    BooleanSupplier authoritative)
```

For start generation, replace the two constants with:

```java
new DialogueStartRequest(
    rivalry.whiteKey(),
    rivalry.blackKey(),
    historyStore.lastFour(matchId))
```

For move generation, replace `personalityFor(...)` with:

```java
String mover = rivalry.personalityKey(move.player());
String opponent = rivalry.personalityKey(move.player().opposite());
```

For end generation, replace the constants with:

```java
new DialogueEndRequest(
    rivalry.whiteKey(),
    whiteOutcome,
    rivalry.blackKey(),
    blackOutcome,
    totalPlies,
    historyStore.lastFour(matchId))
```

Delete the now-unused `personalityFor(PlayerColor)` helper and remove its `PlayerColor` import if no longer used.

Update `MatchEngine` calls to pass the authoritative rivalry:

```java
matchDialogueCoordinator.onGameStart(
    startMatch.id(),
    startMatch.rivalry(),
    () -> isDialogueAuthorityCurrent(generation, startMatch.id(), 0));
```

```java
matchDialogueCoordinator.onMove(
    dialogueMatch.id(),
    dialogueMatch.rivalry(),
    movePlayed,
    () -> isDialogueAuthorityCurrent(generation, dialogueMatch.id(), dialoguePly));
```

```java
matchDialogueCoordinator.onGameEnd(
    finishedMatch.id(),
    finishedMatch.rivalry(),
    result,
    finishedMatch.moveCount(),
    () ->
        isDialogueAuthorityCurrent(
            generation, finishedMatch.id(), finishedMatch.moveCount()));
```

- [ ] **Step 9: Extend `MatchStarted` with rivalry**

Replace the record declaration with:

```java
public record MatchStarted(
    UUID matchId,
    PlayerColor sideToMove,
    BoardPosition position,
    MatchRivalry rivalry) implements MatchEvent {

  public MatchStarted {
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(rivalry, "rivalry must not be null");
  }
}
```

Add `MatchRivalry` import.

- [ ] **Step 10: Strengthen dialogue tests around a non-default pair**

In `MatchDialogueCoordinatorTest`, define:

```java
private static final MatchRivalry RIVALRY =
    new MatchRivalry("sage", "Sage", "maverick", "Maverick");
```

Update every coordinator call to pass `RIVALRY`.

For the start test, capture/verify the `DialogueStartRequest` and assert:

```java
assertThat(request.whitePersonalityKey()).isEqualTo("sage");
assertThat(request.blackPersonalityKey()).isEqualTo("maverick");
```

For a White move, assert:

```java
assertThat(request.moverPersonalityKey()).isEqualTo("sage");
assertThat(request.opponentPersonalityKey()).isEqualTo("maverick");
```

For a Black move, assert the inverse. For end generation, assert White=`sage` and Black=`maverick` while preserving the existing outcome assertions.

- [ ] **Step 11: Run focused backend tests and confirm GREEN**

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchTest,MatchEngineTest,MatchDialogueCoordinatorTest,StockfishPlayerTest test
```

Expected: PASS.

- [ ] **Step 12: Commit Task 2**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchRivalry.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/TestMatchFixtures.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game
git commit -m "feat: bind personalities to match lifecycle"
```

`git add .../game` intentionally stages the test-only factory signature updates under the existing game tests together with the product change. Before committing, run `git diff --cached --name-only` and verify no docs/client/unrelated files are staged.

---

### Task 3: Validate Match Creation and Expose Identities in Every Authoritative Payload

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/InvalidPersonalitySelectionException.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/StartMatchRequest.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchPersonalityResponse.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerAdvice.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStateMessage.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java`
- Tests: service/controller/REST mapper/WebSocket mapper and existing WebSocket handler tests.

**Interfaces:**
- Request JSON: `{ "whitePersonalityKey": "blaze", "blackPersonalityKey": "vesper" }`.
- Identity JSON: `{ "key": "blaze", "displayName": "Blaze" }`.
- `MatchControlService.startMatch(String whitePersonalityKey, String blackPersonalityKey)`.

- [ ] **Step 1: Add failing `MatchControlServiceTest` cases**

Add a `@Mock SelectablePersonalityCatalog personalityCatalog` dependency and pass it into the service constructor wherever `MatchControlService` is instantiated.

Define:

```java
private static final SelectablePersonality BLAZE =
    new SelectablePersonality("blaze", "Blaze");
private static final SelectablePersonality VESPER =
    new SelectablePersonality("vesper", "Vesper");
```

Add helper stubbing in the test class:

```java
private void allowBlazeVsVesper() {
  when(personalityCatalog.findSelectable("blaze")).thenReturn(Optional.of(BLAZE));
  when(personalityCatalog.findSelectable("vesper")).thenReturn(Optional.of(VESPER));
}
```

Add/adjust tests to prove all seven cases:

```text
1. no current match -> resolves both keys -> startNewMatch(new MatchRivalry(...))
2. same key twice -> InvalidPersonalitySelectionException before catalog/engine creation
3. unknown White -> InvalidPersonalitySelectionException
4. unknown Black -> InvalidPersonalitySelectionException
5. stopped existing match + same keys -> resumes without catalog lookup/startNewMatch
6. stopped existing match + changed key -> InvalidPersonalitySelectionException
7. finished existing match -> validates pair and creates a fresh match
```

For case 5, explicitly assert:

```java
verifyNoInteractions(personalityCatalog);
verify(matchEngine, never()).startNewMatch(any());
verify(matchEngine).playUntilFinished();
```

Keep every existing guard/cooldown/daily-limit/background-task assertion in this test class.

- [ ] **Step 2: Run service tests and confirm RED**

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchControlServiceTest test
```

Expected: compile/test failure because the keyed start contract does not exist.

- [ ] **Step 3: Add the controlled exception**

```java
package dev.krishnamurti.ai_chess_rivals.game.application;

public final class InvalidPersonalitySelectionException extends RuntimeException {
  public InvalidPersonalitySelectionException(String message) {
    super(message);
  }
}
```

- [ ] **Step 4: Implement new-match validation and stopped-match equality checks**

Inject:

```java
private final SelectablePersonalityCatalog personalityCatalog;
```

Add it to the constructor after `MatchDialogueCoordinator` and before the executor.

Change the public start method to:

```java
public synchronized MatchSnapshot startMatch(
    String whitePersonalityKey, String blackPersonalityKey) {
```

Keep the existing `reserveStart()` / `abortStart(reservation)` / task-submission logic. Only replace the match creation/resume decision with:

```java
if (!hasMatch || match.isFinished()) {
  MatchRivalry rivalry = resolveNewRivalry(whitePersonalityKey, blackPersonalityKey);
  match = matchEngine.startNewMatch(rivalry);
} else {
  requireSameRivalry(match.rivalry(), whitePersonalityKey, blackPersonalityKey);
}
```

Add:

```java
private MatchRivalry resolveNewRivalry(String whiteKey, String blackKey) {
  if (whiteKey == null || whiteKey.isBlank() || blackKey == null || blackKey.isBlank()) {
    throw new InvalidPersonalitySelectionException(
        "White and Black personality selections are required.");
  }
  if (whiteKey.equals(blackKey)) {
    throw new InvalidPersonalitySelectionException(
        "White and Black personalities must be distinct.");
  }

  SelectablePersonality white =
      personalityCatalog
          .findSelectable(whiteKey)
          .orElseThrow(
              () ->
                  new InvalidPersonalitySelectionException(
                      "Unknown or inactive White personality: " + whiteKey));
  SelectablePersonality black =
      personalityCatalog
          .findSelectable(blackKey)
          .orElseThrow(
              () ->
                  new InvalidPersonalitySelectionException(
                      "Unknown or inactive Black personality: " + blackKey));

  return new MatchRivalry(
      white.key(), white.displayName(), black.key(), black.displayName());
}

private void requireSameRivalry(MatchRivalry rivalry, String whiteKey, String blackKey) {
  if (!rivalry.whiteKey().equals(whiteKey) || !rivalry.blackKey().equals(blackKey)) {
    throw new InvalidPersonalitySelectionException(
        "A stopped match must resume with its original personalities.");
  }
}
```

Do not call `personalityCatalog` inside `requireSameRivalry`.

- [ ] **Step 5: Add the request record and controller binding**

Create `StartMatchRequest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.game.web;

import jakarta.validation.constraints.NotBlank;

public record StartMatchRequest(
    @NotBlank(message = "whitePersonalityKey is required") String whitePersonalityKey,
    @NotBlank(message = "blackPersonalityKey is required") String blackPersonalityKey) {}
```

Change `MatchController.startMatch()` to:

```java
@PostMapping("/start")
public ResponseEntity<MatchResponse> startMatch(
    @Valid @RequestBody StartMatchRequest request) {
  MatchSnapshot snapshot =
      matchControlService.startMatch(
          request.whitePersonalityKey(), request.blackPersonalityKey());
  return ResponseEntity.accepted().body(MatchResponseMapper.map(snapshot));
}
```

Add imports:

```java
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
```

- [ ] **Step 6: Map invalid selection to controlled HTTP 400**

Add to `MatchControllerAdvice`:

```java
@ExceptionHandler(InvalidPersonalitySelectionException.class)
public ProblemDetail handleInvalidPersonalitySelection(
    InvalidPersonalitySelectionException ex) {
  ProblemDetail detail =
      ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  detail.setTitle("Invalid Personality Selection");
  detail.setProperty("code", "INVALID_PERSONALITY_SELECTION");
  detail.setProperty("message", ex.getMessage());
  return detail;
}
```

Do not add a global validation handler. Spring MVC already turns malformed/missing validated request content into `400`.

- [ ] **Step 7: Create the shared response identity DTO**

```java
package dev.krishnamurti.ai_chess_rivals.game.web;

import java.util.Objects;

public record MatchPersonalityResponse(String key, String displayName) {
  public MatchPersonalityResponse {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(displayName, "displayName must not be null");
  }
}
```

- [ ] **Step 8: Add identities to REST `MatchResponse`**

Change its record header to begin:

```java
public record MatchResponse(
    UUID matchId,
    MatchPersonalityResponse whitePersonality,
    MatchPersonalityResponse blackPersonality,
    PlayerColor sideToMove,
```

Keep all remaining existing fields in their current order.

In `MatchResponseMapper.map(...)`, after `Match match = snapshot.match();`, create:

```java
MatchPersonalityResponse whitePersonality =
    new MatchPersonalityResponse(
        match.rivalry().whiteKey(), match.rivalry().whiteDisplayName());
MatchPersonalityResponse blackPersonality =
    new MatchPersonalityResponse(
        match.rivalry().blackKey(), match.rivalry().blackDisplayName());
```

Pass them immediately after `match.id()` when constructing `MatchResponse`.

Update `MatchResponseMapperTest` to assert:

```java
assertThat(response.whitePersonality())
    .isEqualTo(new MatchPersonalityResponse("white-test", "White Test"));
assertThat(response.blackPersonality())
    .isEqualTo(new MatchPersonalityResponse("black-test", "Black Test"));
```

Use the test fixture's keys/names if that test overrides its rivalry.

- [ ] **Step 9: Add identities to WebSocket `MATCH_STATE` hydration**

Change `MatchStateMessage` header to begin:

```java
record MatchStateMessage(
    UUID matchId,
    MatchPersonalityResponse whitePersonality,
    MatchPersonalityResponse blackPersonality,
    PlayerColor sideToMove,
```

Add import:

```java
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
```

Inside `from(MatchSnapshot snapshot)`, create the same two response records from `match.rivalry()` and pass them after `match.id()`.

This step is mandatory: browser refresh/reconnect is hydrated by `MATCH_STATE`, not only by REST.

- [ ] **Step 10: Add identities to live `MATCH_STARTED`**

Replace `MatchStartedMessage` with:

```java
package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
import java.util.UUID;

public record MatchStartedMessage(
    UUID matchId,
    MatchPersonalityResponse whitePersonality,
    MatchPersonalityResponse blackPersonality,
    PlayerColor sideToMove,
    String fen) {}
```

Change the `MatchStarted` branch in `MatchStreamMessageMapper` to:

```java
case MatchStarted started ->
    new MatchStreamMessage<>(
        MatchStreamMessageType.MATCH_STARTED,
        new MatchStartedMessage(
            started.matchId(),
            new MatchPersonalityResponse(
                started.rivalry().whiteKey(), started.rivalry().whiteDisplayName()),
            new MatchPersonalityResponse(
                started.rivalry().blackKey(), started.rivalry().blackDisplayName()),
            started.sideToMove(),
            started.position().fen()));
```

Add the `MatchPersonalityResponse` import.

- [ ] **Step 11: Update controller and WebSocket mapper tests**

In `MatchControllerTest`, successful start requests must send:

```json
{
  "whitePersonalityKey": "blaze",
  "blackPersonalityKey": "vesper"
}
```

and verify:

```java
verify(matchControlService).startMatch("blaze", "vesper");
```

Add tests for:

```text
POST /api/v1/match/start with no body -> 400
blank whitePersonalityKey -> 400
blank blackPersonalityKey -> 400
InvalidPersonalitySelectionException -> 400 + code INVALID_PERSONALITY_SELECTION
```

In `MatchStreamMessageMapperTest`, construct `MatchStarted` with `TestMatchFixtures.TEST_RIVALRY` and assert its mapped payload has:

```java
assertThat(payload.whitePersonality())
    .isEqualTo(new MatchPersonalityResponse("white-test", "White Test"));
assertThat(payload.blackPersonality())
    .isEqualTo(new MatchPersonalityResponse("black-test", "Black Test"));
```

Update any existing `MatchStateMessage` assertions in `MatchWebSocketHandlerTest` to include the two identities.

- [ ] **Step 12: Run the full backend slice**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest,MatchTest,MatchEngineTest,MatchDialogueCoordinatorTest,MatchControlServiceTest,MatchControllerTest,MatchResponseMapperTest,MatchStreamMessageMapperTest,MatchWebSocketHandlerTest,OwnerTokenInterceptorTest test
```

Expected: PASS.

Then run:

```bash
./server/mvnw -f server/pom.xml verify
```

Expected: PASS including Spring Modulith boundary verification. If Modulith reports `game -> ai.personality`, treat that as a defect: only `ai :: api` is permitted.

- [ ] **Step 13: Commit Task 3**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game
git commit -m "feat: validate and expose match personalities"
```

Before committing, verify staged paths are only under `server/.../game`; Task 1 already committed the `ai` changes.

---

### Task 4: Add Frontend Roster/Start Contracts and Authoritative Identity Hydration

**Files:**
- Create: `client/src/services/personalityApi.ts`
- Create: `client/src/services/personalityApi.test.ts`
- Modify: `client/src/types/match.ts`
- Modify: `client/src/services/adminMatchApi.ts`
- Modify: `client/src/services/adminMatchApi.test.ts`
- Modify: `client/src/store/matchViewerStore.ts`
- Modify: `client/src/store/matchViewerStore.test.ts`

**Interfaces:**
- `PersonalityRosterItem { key, displayName, description, avatarRef }`.
- `MatchPersonality { key, displayName }`.
- `StartMatchRequest { whitePersonalityKey, blackPersonalityKey }`.
- `personalityApi.listSelectable()` GETs `/personalities`.
- `adminMatchApi.startMatch(token, request)` POSTs the two-key body.
- Zustand stores selected identities from both `MATCH_STATE` and `MATCH_STARTED`.

- [ ] **Step 1: Add the frontend types**

Add to `client/src/types/match.ts`:

```ts
export interface PersonalityRosterItem {
  key: string;
  displayName: string;
  description: string;
  avatarRef: string | null;
}

export interface MatchPersonality {
  key: string;
  displayName: string;
}

export interface StartMatchRequest {
  whitePersonalityKey: string;
  blackPersonalityKey: string;
}
```

Add to `MatchResponse` immediately after `matchId`:

```ts
whitePersonality: MatchPersonality;
blackPersonality: MatchPersonality;
```

Add to `MatchStartedMessage.payload` immediately after `matchId`:

```ts
whitePersonality: MatchPersonality;
blackPersonality: MatchPersonality;
```

Because `MATCH_STATE` payload is `MatchResponse`, it automatically carries the same identity shape.

- [ ] **Step 2: Create the failing roster API test using the repository's Axios test pattern**

Create `client/src/services/personalityApi.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => ({ get })),
  },
}));

import { personalityApi } from "./personalityApi";

describe("personalityApi", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the selectable roster", async () => {
    const roster = [
      {
        key: "blaze",
        displayName: "Blaze",
        description: "Explosive confidence.",
        avatarRef: null,
      },
      {
        key: "vesper",
        displayName: "Vesper",
        description: "Cold precision.",
        avatarRef: null,
      },
    ];
    get.mockResolvedValue({ data: roster });

    await expect(personalityApi.listSelectable()).resolves.toEqual(roster);
    expect(get).toHaveBeenCalledWith("/personalities");
  });
});
```

- [ ] **Step 3: Run the new test and confirm RED**

```bash
cd client
npm test -- personalityApi.test.ts
```

Expected: FAIL because `personalityApi.ts` does not exist.

- [ ] **Step 4: Create `personalityApi.ts`**

```ts
import axios from "axios";
import type { PersonalityRosterItem } from "../types/match";
import { API_BASE_URL } from "./matchApi";

const personalityApiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  headers: {
    "Content-Type": "application/json",
  },
});

export const personalityApi = {
  listSelectable: async (): Promise<PersonalityRosterItem[]> => {
    const response =
      await personalityApiClient.get<PersonalityRosterItem[]>("/personalities");
    return response.data;
  },
};
```

- [ ] **Step 5: Change the admin start request contract and its test**

In `adminMatchApi.ts`, import `StartMatchRequest` and replace `startMatch` with:

```ts
startMatch: async (
  token: string,
  request: StartMatchRequest,
): Promise<MatchResponse> => {
  const response = await adminApiClient.post<MatchResponse>(
    "/match/start",
    request,
    authorization(token),
  );
  return response.data;
},
```

In `adminMatchApi.test.ts`, change the Start test to:

```ts
it("sends selected personalities and bearer token to Start", async () => {
  const request = {
    whitePersonalityKey: "blaze",
    blackPersonalityKey: "vesper",
  };

  await adminMatchApi.startMatch("owner-token", request);

  expect(post).toHaveBeenCalledWith("/match/start", request, {
    headers: { Authorization: "Bearer owner-token" },
  });
});
```

Leave the Stop and public GET tests unchanged.

- [ ] **Step 6: Store authoritative identities in Zustand**

Import `MatchPersonality` in `matchViewerStore.ts` and add to `MatchViewerState`:

```ts
whitePersonality?: MatchPersonality;
blackPersonality?: MatchPersonality;
```

Initialize both to `undefined`.

In `NO_MATCH`, add:

```ts
whitePersonality: undefined,
blackPersonality: undefined,
```

In `MATCH_STARTED`, add:

```ts
whitePersonality: msg.payload.whitePersonality,
blackPersonality: msg.payload.blackPersonality,
```

In `MATCH_STATE`, add:

```ts
whitePersonality: msg.payload.whitePersonality,
blackPersonality: msg.payload.blackPersonality,
```

Do not modify these fields in MOVE, DIALOGUE, STOP, or FINISH handlers.

- [ ] **Step 7: Extend store tests**

Update every `MATCH_STATE`/`MATCH_STARTED` fixture in `matchViewerStore.test.ts` to contain:

```ts
whitePersonality: { key: "blaze", displayName: "Blaze" },
blackPersonality: { key: "vesper", displayName: "Vesper" },
```

Add assertions proving:

```ts
expect(useMatchViewerStore.getState().whitePersonality).toEqual({
  key: "blaze",
  displayName: "Blaze",
});
expect(useMatchViewerStore.getState().blackPersonality).toEqual({
  key: "vesper",
  displayName: "Vesper",
});
```

Assert identities remain after `MATCH_STOPPED` and `MATCH_FINISHED`, and become `undefined` after `NO_MATCH`.

- [ ] **Step 8: Run focused frontend tests**

```bash
cd client
npm test -- personalityApi.test.ts adminMatchApi.test.ts matchViewerStore.test.ts
```

Expected: PASS.

- [ ] **Step 9: Commit Task 4**

```bash
git add client/src/types/match.ts \
  client/src/services/personalityApi.ts client/src/services/personalityApi.test.ts \
  client/src/services/adminMatchApi.ts client/src/services/adminMatchApi.test.ts \
  client/src/store/matchViewerStore.ts client/src/store/matchViewerStore.test.ts
git commit -m "feat: add personality selection client contracts"
```

---

### Task 5: Build Pre-Match Selectors, Randomization, and Start/Resume UX

**Files:**
- Create: `client/src/features/admin/rivalrySelection.ts`
- Create: `client/src/features/admin/rivalrySelection.test.ts`
- Create: `client/src/features/admin/RivalrySetup.tsx`
- Create: `client/src/features/admin/RivalrySetup.test.tsx`
- Modify: `client/src/features/admin/MatchAdminControls.tsx`
- Modify: `client/src/features/admin/MatchAdminControls.test.tsx`

**Interfaces:**
- `randomizeRivalry(roster, random?) -> StartMatchRequest`.
- `RivalrySetup` is presentation-only.
- `MatchAdminControls` owns roster loading and local pre-match selections; Zustand remains authoritative only for identities of an existing match.

- [ ] **Step 1: Write the failing randomizer tests**

Create `rivalrySelection.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import type { PersonalityRosterItem } from "@/types/match";
import { randomizeRivalry } from "./rivalrySelection";

const roster: PersonalityRosterItem[] = [
  { key: "a", displayName: "A", description: "A", avatarRef: null },
  { key: "b", displayName: "B", description: "B", avatarRef: null },
  { key: "c", displayName: "C", description: "C", avatarRef: null },
  { key: "d", displayName: "D", description: "D", avatarRef: null },
];

describe("randomizeRivalry", () => {
  it("returns two distinct roster entries without retrying", () => {
    const randomValues = [0, 0];
    let index = 0;

    const result = randomizeRivalry(roster, () => randomValues[index++]);

    expect(result).toEqual({
      whitePersonalityKey: "a",
      blackPersonalityKey: "b",
    });
    expect(index).toBe(2);
  });

  it("can choose the last item for both compressed index paths", () => {
    const randomValues = [0.99, 0.99];
    let index = 0;

    const result = randomizeRivalry(roster, () => randomValues[index++]);

    expect(result.whitePersonalityKey).toBe("d");
    expect(result.blackPersonalityKey).toBe("c");
    expect(result.whitePersonalityKey).not.toBe(result.blackPersonalityKey);
  });

  it("rejects a roster with fewer than two personalities", () => {
    expect(() => randomizeRivalry(roster.slice(0, 1), () => 0)).toThrow(
      "At least two personalities are required",
    );
  });
});
```

- [ ] **Step 2: Implement loop-free distinct randomization**

Create `rivalrySelection.ts`:

```ts
import type { PersonalityRosterItem, StartMatchRequest } from "@/types/match";

export function randomizeRivalry(
  roster: PersonalityRosterItem[],
  random: () => number = Math.random,
): StartMatchRequest {
  if (roster.length < 2) {
    throw new Error("At least two personalities are required");
  }

  const whiteIndex = Math.floor(random() * roster.length);
  const compressedBlackIndex = Math.floor(random() * (roster.length - 1));
  const blackIndex =
    compressedBlackIndex >= whiteIndex
      ? compressedBlackIndex + 1
      : compressedBlackIndex;

  return {
    whitePersonalityKey: roster[whiteIndex].key,
    blackPersonalityKey: roster[blackIndex].key,
  };
}
```

- [ ] **Step 3: Write `RivalrySetup` tests before the component**

Create component tests with a three-item roster and assert these exact behaviors:

```text
getByLabelText("White personality") contains all three names
a Black option whose value equals the current White key is disabled
a White option whose value equals the current Black key is disabled
changing White calls onWhiteChange with the selected key
changing Black calls onBlackChange with the selected key
clicking "Randomize Rivalry" calls onRandomize once
disabled=true disables both selects and the randomize button
selected White and Black descriptions are rendered
```

Use `fireEvent.change` and `fireEvent.click` from Testing Library; do not install `user-event`.

- [ ] **Step 4: Create `RivalrySetup.tsx`**

Use this public prop shape:

```tsx
interface RivalrySetupProps {
  roster: PersonalityRosterItem[];
  whitePersonalityKey: string;
  blackPersonalityKey: string;
  disabled: boolean;
  onWhiteChange: (key: string) => void;
  onBlackChange: (key: string) => void;
  onRandomize: () => void;
}
```

Resolve selected roster items once:

```tsx
const white = roster.find((item) => item.key === whitePersonalityKey);
const black = roster.find((item) => item.key === blackPersonalityKey);
```

Render a responsive container:

```tsx
<div className="grid gap-4 sm:grid-cols-2">
```

Each side uses a `<label>` and native `<select>` with these classes:

```tsx
className="w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
```

White option mapping:

```tsx
{roster.map((personality) => (
  <option
    key={personality.key}
    value={personality.key}
    disabled={personality.key === blackPersonalityKey}
  >
    {personality.displayName}
  </option>
))}
```

Black mirrors it and disables `whitePersonalityKey`.

Below each select, render the selected item's `description` in `text-sm text-muted-foreground` when present.

Below the grid, render existing `Button`:

```tsx
<Button type="button" variant="outline" disabled={disabled} onClick={onRandomize}>
  Randomize Rivalry
</Button>
```

- [ ] **Step 5: Add roster/setup state to `MatchAdminControls`**

Add imports for `RivalrySetup`, `randomizeRivalry`, `personalityApi`, and `PersonalityRosterItem`.

Extend the Zustand selection to read `whitePersonality` and `blackPersonality`.

Add state:

```tsx
const [roster, setRoster] = useState<PersonalityRosterItem[]>([]);
const [rosterLoading, setRosterLoading] = useState(true);
const [rosterError, setRosterError] = useState<string>();
const [whitePersonalityKey, setWhitePersonalityKey] = useState("");
const [blackPersonalityKey, setBlackPersonalityKey] = useState("");
```

Add:

```tsx
const loadRoster = useCallback(async () => {
  setRosterLoading(true);
  setRosterError(undefined);
  try {
    const loaded = await personalityApi.listSelectable();
    setRoster(loaded);
    if (loaded.length < 2) {
      setRosterError(
        "At least two active personalities are required to start a rivalry.",
      );
    }
  } catch {
    setRoster([]);
    setRosterError("Unable to load personalities. Please try again.");
  } finally {
    setRosterLoading(false);
  }
}, []);

useEffect(() => {
  void loadRoster();
}, [loadRoster]);
```

- [ ] **Step 6: Synchronize setup state without overwriting user choices**

Add this effect after roster loading:

```tsx
useEffect(() => {
  if (
    (matchStatus === "IN_PROGRESS" || matchStatus === "STOPPED") &&
    whitePersonality &&
    blackPersonality
  ) {
    setWhitePersonalityKey(whitePersonality.key);
    setBlackPersonalityKey(blackPersonality.key);
    return;
  }

  if (roster.length < 2) return;

  const keys = new Set(roster.map((item) => item.key));
  const currentPairIsUsable =
    keys.has(whitePersonalityKey) &&
    keys.has(blackPersonalityKey) &&
    whitePersonalityKey !== blackPersonalityKey;

  if (!currentPairIsUsable) {
    setWhitePersonalityKey(roster[0].key);
    setBlackPersonalityKey(roster[1].key);
  }
}, [
  matchStatus,
  whitePersonality,
  blackPersonality,
  roster,
  whitePersonalityKey,
  blackPersonalityKey,
]);
```

This deliberately locks to authoritative keys only for an existing active/stopped match; a completed match may start a new active-roster pair.

- [ ] **Step 7: Add deterministic UI guards and randomize action**

Add:

```tsx
const running = matchStatus === "IN_PROGRESS";
const stopped = matchStatus === "STOPPED";
const rivalryLocked = running || stopped;
const validSelection =
  whitePersonalityKey !== "" &&
  blackPersonalityKey !== "" &&
  whitePersonalityKey !== blackPersonalityKey;
const canCreateNewMatch =
  !rosterLoading &&
  rosterError === undefined &&
  roster.length >= 2 &&
  validSelection;
const canStartOrResume = stopped || canCreateNewMatch;

const randomize = () => {
  const next = randomizeRivalry(roster);
  setWhitePersonalityKey(next.whitePersonalityKey);
  setBlackPersonalityKey(next.blackPersonalityKey);
};
```

Keep the existing `showStart` guard logic, but use `stopped` when choosing the label and `canStartOrResume` when disabling the start button.

- [ ] **Step 8: Render loading/error/setup states**

Before the Start/Stop button row, render:

```tsx
{rosterLoading && (
  <p className="text-sm text-muted-foreground">Loading personalities…</p>
)}

{rosterError && !stopped && (
  <div className="space-y-2" role="alert">
    <p className="text-sm text-destructive">{rosterError}</p>
    <Button type="button" variant="outline" size="sm" onClick={() => void loadRoster()}>
      Retry
    </Button>
  </div>
)}

{stopped && whitePersonality && blackPersonality && roster.length < 2 && (
  <p className="text-sm text-muted-foreground">
    Current rivalry: {whitePersonality.displayName} vs {blackPersonality.displayName}
  </p>
)}

{roster.length >= 2 && (
  <RivalrySetup
    roster={roster}
    whitePersonalityKey={whitePersonalityKey}
    blackPersonalityKey={blackPersonalityKey}
    disabled={rivalryLocked || isPending}
    onWhiteChange={setWhitePersonalityKey}
    onBlackChange={setBlackPersonalityKey}
    onRandomize={randomize}
  />
)}
```

A stopped match may still show a roster error/retry elsewhere if desired, but **Resume Match remains enabled** when its authoritative keys exist.

- [ ] **Step 9: Send the selected keys on Start and Resume**

Replace the current direct `adminMatchApi.startMatch` callback with:

```tsx
onClick={() =>
  void runOperation("start", (ownerToken) =>
    adminMatchApi.startMatch(ownerToken, {
      whitePersonalityKey,
      blackPersonalityKey,
    }),
  )
}
```

Set the button label:

```tsx
{pendingAction === "start"
  ? stopped
    ? "Resuming…"
    : "Starting…"
  : stopped
    ? "Resume Match"
    : "Start Match"}
```

Set its disabled expression to:

```tsx
disabled={isPending || !canStartOrResume}
```

Keep Stop, cooldown, daily-limit, lock, unauthorized, and refresh logic unchanged.

- [ ] **Step 10: Expand `MatchAdminControls.test.tsx`**

Mock `personalityApi.listSelectable` to return four items by default. Update any `MATCH_STATE` fixture used by the test with White/Black identities.

Add tests proving:

```text
loading text appears before roster resolves
successful roster renders both selectors and all four names
first two roster entries initialize the new-match pair
changing White/Black changes the exact POST body
Randomize produces two distinct keys (stub Math.random with two values)
<2 roster entries shows the explicit error and disables Start
roster rejection shows Retry and disables a new Start
Retry calls listSelectable again
STOPPED state locks the pair, labels action Resume Match, and sends authoritative stored keys
STOPPED resume remains possible if the roster request fails
existing Stop/cooldown/daily-limit/401 behavior remains unchanged
```

For the start payload assertion use:

```ts
expect(adminMatchApi.startMatch).toHaveBeenCalledWith("owner-token", {
  whitePersonalityKey: "chosen-white",
  blackPersonalityKey: "chosen-black",
});
```

- [ ] **Step 11: Run the admin UI tests**

```bash
cd client
npm test -- rivalrySelection.test.ts RivalrySetup.test.tsx MatchAdminControls.test.tsx
```

Expected: PASS.

- [ ] **Step 12: Commit Task 5**

```bash
git add client/src/features/admin/rivalrySelection.ts \
  client/src/features/admin/rivalrySelection.test.ts \
  client/src/features/admin/RivalrySetup.tsx \
  client/src/features/admin/RivalrySetup.test.tsx \
  client/src/features/admin/MatchAdminControls.tsx \
  client/src/features/admin/MatchAdminControls.test.tsx
git commit -m "feat: add rivalry personality setup"
```

---

### Task 6: Show Selected Identities in the Viewer and Verify the Whole Slice

**Files:**
- Modify: `client/src/features/match-viewer/components/PlayerStrip.tsx`
- Create: `client/src/features/match-viewer/components/PlayerStrip.test.tsx`
- Verify: all files from Tasks 1–5.

**Interfaces:**
- Consumes authoritative `whitePersonality` / `blackPersonality` from Zustand.
- Viewer does not fetch the roster to identify an existing match.

- [ ] **Step 1: Write failing `PlayerStrip` tests**

Initialize the store directly in each test and cover these cases:

```text
White strip + whitePersonality {displayName:"Blaze"} -> renders Blaze
Black strip + blackPersonality {displayName:"Vesper"} -> renders Vesper
no identity before a match -> renders Stockfish White / Stockfish Black fallback
IN_PROGRESS + active side -> still renders Thinking...
```

Use the same store-reset pattern already used by `matchViewerStore.test.ts`; do not add another state library or provider.

- [ ] **Step 2: Make `PlayerStrip` identity-aware**

Replace the current store read with:

```tsx
const {
  activeTurn,
  matchStatus,
  whitePersonality,
  blackPersonality,
} = useMatchViewerStore();
const isActive = matchStatus === "IN_PROGRESS" && activeTurn === side;
const personality = side === "WHITE" ? whitePersonality : blackPersonality;
const displayName = personality?.displayName ?? MATCH_PLAYERS[side].name;
```

Replace:

```tsx
<span className="font-semibold">{MATCH_PLAYERS[side].name}</span>
```

with:

```tsx
<span className="min-w-0 break-words font-semibold">{displayName}</span>
```

Keep the existing active styling and `Thinking...` badge.

- [ ] **Step 3: Run every frontend test touched by issue #44**

```bash
cd client
npm test -- personalityApi.test.ts adminMatchApi.test.ts matchViewerStore.test.ts rivalrySelection.test.ts RivalrySetup.test.tsx MatchAdminControls.test.tsx PlayerStrip.test.tsx
```

Expected: PASS.

- [ ] **Step 4: Run frontend formatting and verification**

```bash
cd client
npm run format
npm run verify
```

Expected: PASS for formatting, typecheck, lint, and production build.

- [ ] **Step 5: Run backend formatting and verification**

From repository root:

```bash
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml verify
```

Expected: PASS for formatting, Error Prone compilation, tests, Modulith verification, and SpotBugs.

- [ ] **Step 6: Run whole-repository verification**

POSIX:

```bash
./scripts/verify.sh
```

Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

Expected: PASS.

- [ ] **Step 7: Perform manual issue #44 acceptance with zero move delay**

Start the application using `docs/BUILD_AND_VERIFY.md`, setting both move delays to `0s`.

Verify in this exact order:

1. Open the owner/admin page; all four seeded active personalities load in stable roster order.
2. White and Black selectors show display names/descriptions and cannot select the same personality.
3. Click `Randomize Rivalry` at least 20 times; every pair is distinct.
4. Start `Blaze` against a non-Vesper opponent. Confirm the request succeeds and the live viewer shows the selected names.
5. Confirm game-start and move dialogue uses the selected pair; no line is attributed to an unselected hard-coded character.
6. Refresh the viewer during the match. Confirm the same identities return via `MATCH_STATE` hydration.
7. Disconnect/reconnect the WebSocket (or briefly stop/restart the frontend dev server without restarting backend). Confirm the same pair restores.
8. Stop the match. Confirm setup is locked to the stored pair and action is `Resume Match`.
9. Resume. Confirm the same identities remain and dialogue continues with them.
10. Let the match finish. Refresh and confirm selected identities remain on the completed viewer.
11. Choose a different valid pair and start a new match. Confirm the new match uses the new pair.
12. Directly call `/api/v1/match/start` with duplicate keys and then a nonexistent key. Confirm both return `400`, not `500`.
13. Block/fail `GET /api/v1/personalities`. Confirm the admin UI shows retryable error and blocks a **new** match start.
14. Stop an already-running match, then block the roster request and refresh admin state. Confirm `Resume Match` can still use the stored pair.
15. Check a 360px viewport: selectors stack without horizontal overflow and existing viewer layout remains usable.

- [ ] **Step 8: Commit the viewer slice**

```bash
git add client/src/features/match-viewer/components/PlayerStrip.tsx \
  client/src/features/match-viewer/components/PlayerStrip.test.tsx
git commit -m "feat: show selected match personalities"
```

- [ ] **Step 9: Run the final diff discipline check**

```bash
git status --short
git diff master...HEAD --stat
```

Expected:

```text
no migration changes
no pom.xml/package.json changes
no unrelated refactors
all task tests and repository verifiers green
```

## Completion Checklist

- [ ] Viewer/owner can choose White and Black from all active system personalities.
- [ ] Randomize always chooses two distinct active personalities.
- [ ] Missing/blank/duplicate/unknown/inactive keys fail server-side with `400`.
- [ ] New match stores immutable resolved identities.
- [ ] Stop/resume preserves identities and does not re-resolve the roster.
- [ ] Dialogue uses selected identities instead of hard-coded Blaze/Vesper.
- [ ] REST snapshot carries identities.
- [ ] WebSocket `MATCH_STATE` restores identities after refresh/reconnect.
- [ ] WebSocket `MATCH_STARTED` carries identities to already-open viewers.
- [ ] Player strips render selected display names.
- [ ] Loading, too-small roster, and roster API-error states are handled.
- [ ] Existing start/stop/resume/cooldown/daily-limit/owner-token behavior remains green.
- [ ] Backend `verify`, frontend `verify`, and root verifier all pass.

## Execution Handoff

Luna executes **inline only** with `superpowers:executing-plans`, one task at a time, running each task's focused tests before committing. Do not dispatch subagents. If current code differs only by formatting or line movement, adapt mechanically while preserving these interfaces and behaviors. If a named method/type no longer exists or a module boundary has materially changed, stop execution at that task and report the mismatch before inventing a new architecture.
