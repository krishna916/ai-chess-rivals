# Personality Selection and Random Rivalry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the owner choose distinct White and Black system personalities (or randomize a distinct pair), validate those choices server-side, bind them immutably to the match, use them for dialogue, and restore/display them through REST/WebSocket hydration.

**Architecture:** Reuse the existing PostgreSQL-backed personality roster as the only source for selectable personalities. Add one tiny `ai :: api` lookup contract so the `game` module can validate stable personality keys without depending on `ai.personality` internals; store the resolved key/display-name pair as immutable metadata on the in-memory `Match` aggregate; pass that metadata to dialogue and REST/WebSocket payloads. The frontend loads the existing roster endpoint only for pre-match setup, sends two keys to `/match/start`, and hydrates the authoritative selected identities from match payloads thereafter.

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
- Do not add a match table or general match-history persistence layer. The existing authoritative `Match` is in memory; for this issue, “persist with the match” means the chosen identity metadata remains part of that aggregate for the complete match lifecycle and is restored by the existing snapshot/hydration path. Backend-restart recovery remains out of scope.
- Do not add personality create/edit/delete behavior, favorites, matchup history, filters, generated avatars, or mirror-match support.
- A new match requires exactly two non-blank, distinct personality keys.
- Both keys must resolve to currently active system personalities through the existing personality repository/service path.
- Once a match is created, its White/Black identities are immutable through stop/resume and completion.
- Resuming a stopped match must use the identities already stored on that match. Do not re-resolve them against the active roster; a personality becoming inactive later must not silently change or prevent resuming the already-created match.
- `POST /api/v1/match/start` accepts JSON `{ "whitePersonalityKey": "...", "blackPersonalityKey": "..." }` for both new starts and resumes. On resume, requested keys must exactly match the stored match keys or the server returns `400`.
- Unknown, inactive, non-system, blank, duplicate, or resume-mismatched selections return controlled `400 Bad Request`; they must not become `500` errors.
- Keep `game` module dependencies within its existing `{"chess :: api", "ai :: api"}` boundary. Never import `dev.krishnamurti.ai_chess_rivals.ai.personality.*` from `game`.
- Remove `MatchDialogueCoordinator` hard-coded `blaze`/`vesper` defaults. Every start/move/end dialogue request must use the personality keys stored on the authoritative match.
- REST `MatchResponse` and live `MATCH_STARTED` WebSocket payloads must carry both selected identities as `{ key, displayName }` so the viewer never needs a second roster request to identify an already-running/completed match.
- Frontend pre-match roster loading uses existing `GET /api/v1/personalities` and its existing stable backend order.
- Randomization must always return two distinct entries when at least two personalities exist. Do not use retry loops; choose the second index from `length - 1` and skip the first index deterministically.
- Use native `<select>` controls styled with existing Tailwind classes. Do not scaffold another shadcn component merely for two dropdowns.
- Disable rivalry editing while a match is running or stopped. A stopped match shows its stored identities and resumes with them.
- Loading, roster API failure, and fewer-than-two-personality states must be explicit and must disable starting a new match.
- Apply backend/frontend formatting before repository verification.

## File Map

### Create

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonality.java` — minimal cross-module selectable identity (`key`, `displayName`).
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonalityCatalog.java` — named-interface lookup contract used by `game`.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchRivalry.java` — immutable White/Black key/display-name metadata plus color lookup helpers.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/InvalidPersonalitySelectionException.java` — controlled application validation failure.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/StartMatchRequest.java` — two-key JSON request with bean validation.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchPersonalityResponse.java` — `{key, displayName}` response shape.
- `client/src/services/personalityApi.ts` — GET-only roster client.
- `client/src/services/personalityApi.test.ts` — roster request contract.
- `client/src/features/admin/rivalrySelection.ts` — pure distinct random-pair helper.
- `client/src/features/admin/rivalrySelection.test.ts` — deterministic randomization tests.
- `client/src/features/admin/RivalrySetup.tsx` — focused White/Black selector + randomize UI.
- `client/src/features/admin/RivalrySetup.test.tsx` — selector, duplicate prevention, randomize, disabled-state coverage.
- `client/src/features/match-viewer/components/PlayerStrip.test.tsx` — selected identity rendering coverage.

### Modify

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlServiceTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerAdvice.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`
- `client/src/types/match.ts`
- `client/src/services/adminMatchApi.ts`
- `client/src/services/adminMatchApi.test.ts`
- `client/src/store/matchViewerStore.ts`
- `client/src/store/matchViewerStore.test.ts`
- `client/src/features/admin/MatchAdminControls.tsx`
- `client/src/features/admin/MatchAdminControls.test.tsx`
- `client/src/features/match-viewer/components/PlayerStrip.tsx`

### Explicitly Do Not Modify

- `server/src/main/resources/db/migration/**` — no schema change is needed for issue #44.
- `server/pom.xml`
- `client/package.json`
- Stockfish/chess evaluation code.
- AI provider configuration/prompt templates except through the selected keys already consumed by existing dialogue generation.

---

### Task 1: Expose a Minimal Selectable-Personality Lookup Through `ai :: api`

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonality.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/SelectablePersonalityCatalog.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java`

**Interfaces:**
- Produces: `public record SelectablePersonality(String key, String displayName)`.
- Produces: `public interface SelectablePersonalityCatalog { Optional<SelectablePersonality> findSelectable(String personalityKey); }`.
- `PersonalityService` implements the catalog while preserving existing `listSelectable()` and `requirePromptProfile()` behavior.
- Lookup source remains `PersonalityRepository.findByPersonalityKeyAndSystemTrueAndActiveTrue(...)`.

- [ ] **Step 1: Add failing catalog tests to `PersonalityServiceTest`**

Add tests equivalent to:

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
```

Add imports for `SelectablePersonality` and `java.util.Optional` if not already present.

- [ ] **Step 2: Run the focused test and verify it fails**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest test
```

Expected: FAIL/compile failure because `SelectablePersonality`, `SelectablePersonalityCatalog`, and `findSelectable` do not exist yet.

- [ ] **Step 3: Create the public identity record**

Create `SelectablePersonality.java`:

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

- [ ] **Step 4: Create the catalog interface**

Create `SelectablePersonalityCatalog.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Optional;

public interface SelectablePersonalityCatalog {
  Optional<SelectablePersonality> findSelectable(String personalityKey);
}
```

This interface exists because Spring Modulith exposes `ai :: api` to `game`; do not move personality JPA internals into the public API.

- [ ] **Step 5: Implement the contract in `PersonalityService`**

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

Keep `requirePromptProfile()` intact. Add only required imports.

- [ ] **Step 6: Re-run the test**

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

### Task 2: Bind Immutable Rivalry Metadata to the Match and Dialogue Flow

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchRivalry.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`

**Interfaces:**
- Produces: `MatchRivalry(String whiteKey, String whiteDisplayName, String blackKey, String blackDisplayName)`.
- Produces: `String MatchRivalry.personalityKey(PlayerColor color)` and `String MatchRivalry.displayName(PlayerColor color)`.
- `Match.newGame(MatchRivalry rivalry)` becomes the production factory.
- `Match.rivalry()` returns immutable metadata and every derived `Match` keeps the same instance/value.
- `MatchEngine.startNewMatch(MatchRivalry rivalry)` requires explicit rivalry data.
- `MatchStarted` carries `MatchRivalry rivalry`.
- `MatchDialogueCoordinator` receives `MatchRivalry` for start/move/end and never uses constants for personalities.

- [ ] **Step 1: Write failing domain tests**

Add to `MatchTest`:

```java
private static final MatchRivalry RIVALRY =
    new MatchRivalry("blaze", "Blaze", "vesper", "Vesper");

@Test
void keepsRivalryAcrossMoveAndFinishTransitions() {
  Match started = Match.newGame(RIVALRY);

  Match afterMove =
      started.recordMove(
          new MoveNotation("e2e4"),
          new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
          MoveDetails.quiet(/* use the existing MatchTest helper/constructor form */));
  Match finished = afterMove.finish(GameResult.WHITE_WINS);

  assertThat(started.rivalry()).isEqualTo(RIVALRY);
  assertThat(afterMove.rivalry()).isEqualTo(RIVALRY);
  assertThat(finished.rivalry()).isEqualTo(RIVALRY);
}

@Test
void rejectsMirrorRivalry() {
  assertThatThrownBy(() -> new MatchRivalry("blaze", "Blaze", "blaze", "Blaze"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("White and Black personalities must be distinct");
}
```

For the first test, reuse the exact existing `MoveDetails` construction already used in `MatchTest`; do not invent a second helper API if `MoveDetails.quiet(...)` is not present.

- [ ] **Step 2: Run domain test and verify failure**

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchTest test
```

Expected: FAIL because `MatchRivalry`/`Match.rivalry()`/`Match.newGame(MatchRivalry)` do not exist.

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
    return color == PlayerColor.WHITE ? whiteKey : blackKey;
  }

  public String displayName(PlayerColor color) {
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

Add `private final MatchRivalry rivalry;` to `Match`.

Update its main constructor to require `MatchRivalry rivalry`, assign it with `Objects.requireNonNull`, and make all constructor/factory transitions pass the same rivalry.

The factory becomes:

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

public MatchRivalry rivalry() {
  return rivalry;
}
```

Update `recordMove(...)` and `finish(...)` constructor calls to pass `rivalry` unchanged.

Update tests/call sites that directly construct `Match` to pass a test rivalry. Use one local constant such as `new MatchRivalry("white-test", "White Test", "black-test", "Black Test")`; do not add production defaults.

- [ ] **Step 5: Make `MatchEngine` require a rivalry for new matches**

Change:

```java
public synchronized Match startNewMatch(MatchRivalry rivalry) {
  Objects.requireNonNull(rivalry, "rivalry must not be null");
  // existing in-progress guard remains
  stopRequested.set(false);
  Match match = Match.newGame(rivalry);
  // existing Stockfish initialization/evaluation logic remains
  matchEventSink.publish(
      new MatchStarted(match.id(), match.sideToMove(), match.currentPosition(), match.rivalry()));
  currentMatch.set(match);
  return match;
}
```

In `playUntilFinished()`, remove the implicit `startNewMatch()` fallback. If `currentMatch` is `null`, throw `IllegalStateException("No match has been started")`; `MatchControlService` is the only path that creates a match now.

In `buildPositionOccurrences`, replace the no-argument `Match.newGame().currentPosition()` usage with `BoardPosition.STARTING_POSITION` so repetition bookkeeping does not need a fake rivalry.

- [ ] **Step 6: Remove hard-coded dialogue personalities**

Delete these constants from `MatchDialogueCoordinator`:

```java
private static final String DEFAULT_WHITE_PERSONALITY = "blaze";
private static final String DEFAULT_BLACK_PERSONALITY = "vesper";
```

Change signatures and request creation:

```java
void onGameStart(UUID matchId, MatchRivalry rivalry, BooleanSupplier authoritative) {
  // generateStart(new DialogueStartRequest(
  //     rivalry.whiteKey(), rivalry.blackKey(), historyStore.lastFour(matchId)))
}

void onMove(
    UUID matchId, MatchRivalry rivalry, MovePlayed move, BooleanSupplier authoritative) {
  String mover = rivalry.personalityKey(move.player());
  String opponent = rivalry.personalityKey(move.player().opposite());
  // existing DialogueMoveRequest fields remain unchanged
}

void onGameEnd(
    UUID matchId,
    MatchRivalry rivalry,
    GameResult result,
    int totalPlies,
    BooleanSupplier authoritative) {
  // existing outcome calculation remains
  // generateEnd uses rivalry.whiteKey() and rivalry.blackKey()
}
```

Delete `personalityFor(PlayerColor)` because the rivalry owns that lookup.

Update the three `MatchEngine` call sites to pass the authoritative match rivalry:

```java
matchDialogueCoordinator.onGameStart(
    startMatch.id(), startMatch.rivalry(), authoritySupplier);

matchDialogueCoordinator.onMove(
    dialogueMatch.id(), dialogueMatch.rivalry(), movePlayed, authoritySupplier);

matchDialogueCoordinator.onGameEnd(
    finishedMatch.id(), finishedMatch.rivalry(), result, finishedMatch.moveCount(), authoritySupplier);
```

Preserve all existing stale-authority, fallback, history, and exception-isolation behavior.

- [ ] **Step 7: Extend `MatchStarted`**

Change it to:

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

- [ ] **Step 8: Update focused tests for explicit rivalry and selected dialogue keys**

In `MatchDialogueCoordinatorTest`, replace assumptions about default `blaze`/`vesper` with a local rivalry such as:

```java
private static final MatchRivalry RIVALRY =
    new MatchRivalry("sage", "Sage", "maverick", "Maverick");
```

Verify `DialogueStartRequest`, mover/opponent `DialogueMoveRequest`, and `DialogueEndRequest` receive `sage`/`maverick` according to color. This test must fail if the coordinator silently reintroduces hard-coded characters.

Update `MatchEngineTest` fixtures and `Match.newGame()` call sites to pass explicit test rivalry; add one assertion that the published `MatchStarted` event carries it.

- [ ] **Step 9: Run focused backend tests**

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchTest,MatchEngineTest,MatchDialogueCoordinatorTest test
```

Expected: PASS.

- [ ] **Step 10: Commit Task 2**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchRivalry.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/domain/Match.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngine.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/event/MatchStarted.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/domain/MatchTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java
git commit -m "feat: bind personalities to match lifecycle"
```

---

### Task 3: Validate Match Creation and Expose Identities Through REST/WebSocket

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/InvalidPersonalitySelectionException.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/StartMatchRequest.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchPersonalityResponse.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerAdvice.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlServiceTest.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`

**Interfaces:**
- Consumes: `SelectablePersonalityCatalog.findSelectable(String)` from Task 1.
- Consumes: `MatchRivalry` and `MatchEngine.startNewMatch(MatchRivalry)` from Task 2.
- Produces request JSON: `{ "whitePersonalityKey": "blaze", "blackPersonalityKey": "vesper" }`.
- Produces REST/WebSocket identities: `{ "key": "blaze", "displayName": "Blaze" }`.

- [ ] **Step 1: Add failing `MatchControlServiceTest` cases**

Add a mock `SelectablePersonalityCatalog` constructor dependency and cover these exact cases:

1. New match with `blaze` + `vesper` resolves both, creates `MatchRivalry("blaze", "Blaze", "vesper", "Vesper")`, and passes it to `matchEngine.startNewMatch(...)`.
2. Duplicate keys throw `InvalidPersonalitySelectionException` before engine creation.
3. Unknown/inactive White or Black key (catalog returns empty) throws `InvalidPersonalitySelectionException`.
4. Stopped existing match with matching keys resumes the existing `Match` and does **not** call the catalog or `startNewMatch`.
5. Stopped existing match with changed keys throws `InvalidPersonalitySelectionException` and does not replace the match.
6. Finished existing match validates a fresh pair and creates a new match.
7. Existing start guard/cooldown/daily-limit behavior remains unchanged.

Use catalog stubs such as:

```java
when(personalityCatalog.findSelectable("blaze"))
    .thenReturn(Optional.of(new SelectablePersonality("blaze", "Blaze")));
when(personalityCatalog.findSelectable("vesper"))
    .thenReturn(Optional.of(new SelectablePersonality("vesper", "Vesper")));
```

- [ ] **Step 2: Run the service test and verify it fails**

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchControlServiceTest test
```

Expected: FAIL because the service has no catalog dependency or keyed start method.

- [ ] **Step 3: Add the controlled exception**

```java
package dev.krishnamurti.ai_chess_rivals.game.application;

public final class InvalidPersonalitySelectionException extends RuntimeException {
  public InvalidPersonalitySelectionException(String message) {
    super(message);
  }
}
```

- [ ] **Step 4: Inject the catalog and implement selection semantics in `MatchControlService`**

Add `SelectablePersonalityCatalog personalityCatalog` to the constructor.

Change the public start signature to:

```java
public synchronized MatchSnapshot startMatch(
    String whitePersonalityKey, String blackPersonalityKey) {
```

Keep the existing `MatchExecutionGuard.reserveStart()`/abort logic around the complete operation.

Inside the existing match-resolution section use this rule:

```java
if (!hasMatch || match.isFinished()) {
  MatchRivalry rivalry = resolveNewRivalry(whitePersonalityKey, blackPersonalityKey);
  match = matchEngine.startNewMatch(rivalry);
} else {
  requireSameRivalry(match.rivalry(), whitePersonalityKey, blackPersonalityKey);
}
```

Add helpers with exact behavior:

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

  return new MatchRivalry(white.key(), white.displayName(), black.key(), black.displayName());
}

private void requireSameRivalry(MatchRivalry rivalry, String whiteKey, String blackKey) {
  if (!rivalry.whiteKey().equals(whiteKey) || !rivalry.blackKey().equals(blackKey)) {
    throw new InvalidPersonalitySelectionException(
        "A stopped match must resume with its original personalities.");
  }
}
```

Do not call `personalityCatalog` from `requireSameRivalry`.

- [ ] **Step 5: Add and validate the HTTP request record**

Create `StartMatchRequest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.game.web;

import jakarta.validation.constraints.NotBlank;

public record StartMatchRequest(
    @NotBlank(message = "whitePersonalityKey is required") String whitePersonalityKey,
    @NotBlank(message = "blackPersonalityKey is required") String blackPersonalityKey) {}
```

Modify `MatchController.startMatch`:

```java
@PostMapping("/start")
public ResponseEntity<MatchResponse> startMatch(@Valid @RequestBody StartMatchRequest request) {
  MatchSnapshot snapshot =
      matchControlService.startMatch(
          request.whitePersonalityKey(), request.blackPersonalityKey());
  return ResponseEntity.accepted().body(MatchResponseMapper.map(snapshot));
}
```

Add `jakarta.validation.Valid` and `RequestBody` imports.

- [ ] **Step 6: Map invalid selections to HTTP 400**

Add to `MatchControllerAdvice`:

```java
@ExceptionHandler(InvalidPersonalitySelectionException.class)
public ProblemDetail handleInvalidPersonalitySelection(InvalidPersonalitySelectionException ex) {
  ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  detail.setTitle("Invalid Personality Selection");
  detail.setProperty("code", "INVALID_PERSONALITY_SELECTION");
  detail.setProperty("message", ex.getMessage());
  return detail;
}
```

Bean-validation failures from missing/blank request fields should remain Spring MVC's normal `400`; do not add a global exception handler unrelated to this controller.

- [ ] **Step 7: Add REST response identity records**

Create:

```java
package dev.krishnamurti.ai_chess_rivals.game.web;

public record MatchPersonalityResponse(String key, String displayName) {}
```

Add `MatchPersonalityResponse whitePersonality` and `MatchPersonalityResponse blackPersonality` to `MatchResponse` immediately after `matchId`.

In `MatchResponseMapper.map(...)`, construct:

```java
MatchPersonalityResponse whitePersonality =
    new MatchPersonalityResponse(
        match.rivalry().whiteKey(), match.rivalry().whiteDisplayName());
MatchPersonalityResponse blackPersonality =
    new MatchPersonalityResponse(
        match.rivalry().blackKey(), match.rivalry().blackDisplayName());
```

Pass both into the `MatchResponse` constructor. Update `MatchResponseMapperTest` to assert exact keys/display names in active and completed snapshots.

- [ ] **Step 8: Carry identities on live `MATCH_STARTED`**

Change `MatchStartedMessage` to include the same two response records (or its existing nested payload equivalent):

```java
public record MatchStartedMessage(
    String type,
    UUID matchId,
    PlayerColor sideToMove,
    String fen,
    MatchPersonalityResponse whitePersonality,
    MatchPersonalityResponse blackPersonality) implements MatchStreamMessage {
  // preserve the repository's existing constructor/type convention
}
```

In `MatchStreamMessageMapper`, map `MatchStarted.rivalry()` to the two `{key, displayName}` values exactly as REST does.

Update `MatchStreamMessageMapperTest` to assert both identities. Do not add personality fields to MOVE/STOP/FINISH events; the store already retains identity from start/state.

- [ ] **Step 9: Add controller contract tests**

Update `MatchControllerTest` so successful start sends:

```json
{
  "whitePersonalityKey": "blaze",
  "blackPersonalityKey": "vesper"
}
```

Verify the controller calls:

```java
verify(matchControlService).startMatch("blaze", "vesper");
```

Add tests for:
- missing request body -> `400`;
- blank key -> `400`;
- `InvalidPersonalitySelectionException` -> `400` with code `INVALID_PERSONALITY_SELECTION` and the exception message.

Keep owner-token authorization assertions unchanged.

- [ ] **Step 10: Run the complete backend slice**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest,MatchTest,MatchEngineTest,MatchDialogueCoordinatorTest,MatchControlServiceTest,MatchControllerTest,MatchResponseMapperTest,MatchStreamMessageMapperTest test
```

Expected: PASS.

Then run Modulith structure verification through the normal lifecycle:

```bash
./server/mvnw -f server/pom.xml verify
```

Expected: PASS, including the `game -> ai :: api` boundary.

- [ ] **Step 11: Commit Task 3**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/InvalidPersonalitySelectionException.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlService.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/StartMatchRequest.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchPersonalityResponse.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerAdvice.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponse.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapper.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStartedMessage.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapper.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchControlServiceTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java
git commit -m "feat: validate and expose match personalities"
```

---

### Task 4: Add Frontend Roster/Start Contracts and Hydrate Match Identities

**Files:**
- Create: `client/src/services/personalityApi.ts`
- Create: `client/src/services/personalityApi.test.ts`
- Modify: `client/src/types/match.ts`
- Modify: `client/src/services/adminMatchApi.ts`
- Modify: `client/src/services/adminMatchApi.test.ts`
- Modify: `client/src/store/matchViewerStore.ts`
- Modify: `client/src/store/matchViewerStore.test.ts`

**Interfaces:**
- Produces `PersonalityRosterItem { key, displayName, description, avatarRef }`.
- Produces `MatchPersonality { key, displayName }`.
- Produces `StartMatchRequest { whitePersonalityKey, blackPersonalityKey }`.
- `personalityApi.listSelectable()` GETs `/personalities`.
- `adminMatchApi.startMatch(token, request)` POSTs `/match/start` with the request body.
- Match viewer store holds authoritative `whitePersonality?` and `blackPersonality?` from `MATCH_STATE` and `MATCH_STARTED`.

- [ ] **Step 1: Add frontend types**

In `client/src/types/match.ts`, add:

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

Add to `MatchResponse`:

```ts
whitePersonality: MatchPersonality;
blackPersonality: MatchPersonality;
```

Add the same fields inside `MatchStartedMessage.payload`.

- [ ] **Step 2: Write failing roster API test**

Create `personalityApi.test.ts` using the same Axios mocking style already present in `adminMatchApi.test.ts`. Assert `listSelectable()` sends `GET /personalities` and returns the response body unchanged.

Use a response fixture:

```ts
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
```

- [ ] **Step 3: Add `personalityApi`**

```ts
import axios from "axios";
import type { PersonalityRosterItem } from "../types/match";
import { API_BASE_URL } from "./matchApi";

const personalityApiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  headers: { "Content-Type": "application/json" },
});

export const personalityApi = {
  listSelectable: async (): Promise<PersonalityRosterItem[]> => {
    const response =
      await personalityApiClient.get<PersonalityRosterItem[]>("/personalities");
    return response.data;
  },
};
```

Do not add caching/global state for a four-record setup roster.

- [ ] **Step 4: Change the admin start API contract**

Change `adminMatchApi.startMatch` to:

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

Update `adminMatchApi.test.ts` to assert the exact payload plus Authorization header.

- [ ] **Step 5: Hydrate identities in the Zustand store**

Add to `MatchViewerState`:

```ts
whitePersonality?: MatchPersonality;
blackPersonality?: MatchPersonality;
```

Initialize them to `undefined`.

For `NO_MATCH`, clear both.

For `MATCH_STARTED`, set:

```ts
whitePersonality: msg.payload.whitePersonality,
blackPersonality: msg.payload.blackPersonality,
```

For `MATCH_STATE`, set the same fields from the snapshot.

Do not clear them on MOVE, STOP, or FINISH.

- [ ] **Step 6: Add store hydration tests**

In `matchViewerStore.test.ts`, update existing `MATCH_STATE`/`MATCH_STARTED` fixtures with the two identities and assert:
- live start stores them;
- snapshot hydration restores them;
- stop and finish preserve them;
- `NO_MATCH` clears them.

- [ ] **Step 7: Run focused frontend tests**

```bash
cd client
npm test -- personalityApi.test.ts adminMatchApi.test.ts matchViewerStore.test.ts
```

Expected: PASS.

- [ ] **Step 8: Commit Task 4**

```bash
git add client/src/types/match.ts \
  client/src/services/personalityApi.ts client/src/services/personalityApi.test.ts \
  client/src/services/adminMatchApi.ts client/src/services/adminMatchApi.test.ts \
  client/src/store/matchViewerStore.ts client/src/store/matchViewerStore.test.ts
git commit -m "feat: add personality selection client contracts"
```

---

### Task 5: Build the Pre-Match Selectors and Distinct Randomizer

**Files:**
- Create: `client/src/features/admin/rivalrySelection.ts`
- Create: `client/src/features/admin/rivalrySelection.test.ts`
- Create: `client/src/features/admin/RivalrySetup.tsx`
- Create: `client/src/features/admin/RivalrySetup.test.tsx`
- Modify: `client/src/features/admin/MatchAdminControls.tsx`
- Modify: `client/src/features/admin/MatchAdminControls.test.tsx`

**Interfaces:**
- `randomizeRivalry(roster, random?) -> { whitePersonalityKey, blackPersonalityKey }`.
- `RivalrySetup` is presentation-only and receives roster/current keys/change callbacks/disabled state.
- `MatchAdminControls` owns roster fetching and current setup state because that state only exists on the owner pre-match page.

- [ ] **Step 1: Write failing randomization tests**

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
  it("always selects two distinct personalities", () => {
    const values = [0, 0, 0.99, 0.99, 0.5, 0.5];
    let index = 0;
    const random = () => values[index++ % values.length];

    for (let attempt = 0; attempt < 3; attempt += 1) {
      const result = randomizeRivalry(roster, random);
      expect(result.whitePersonalityKey).not.toBe(result.blackPersonalityKey);
      expect(roster.map((item) => item.key)).toContain(result.whitePersonalityKey);
      expect(roster.map((item) => item.key)).toContain(result.blackPersonalityKey);
    }
  });

  it("rejects a roster with fewer than two personalities", () => {
    expect(() => randomizeRivalry(roster.slice(0, 1), () => 0)).toThrow(
      "At least two personalities are required",
    );
  });
});
```

- [ ] **Step 2: Implement loop-free distinct randomization**

Create:

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

- [ ] **Step 3: Write failing `RivalrySetup` component tests**

Cover:
- renders White and Black selects with all roster display names;
- the currently selected White key is disabled in Black options and vice versa;
- changing a selector calls the correct callback;
- `Randomize Rivalry` calls `onRandomize`;
- `disabled` disables both selects and randomize button;
- descriptions for the currently selected characters are visible below their selects (keep this concise; use the existing `description` field rather than adding a detail modal).

- [ ] **Step 4: Create `RivalrySetup.tsx`**

Use a simple two-column responsive layout and native selects:

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

Each `<select>` must:
- have an accessible `<label>` (`White personality`, `Black personality`);
- bind the current key;
- render every roster item;
- disable only the option currently selected by the opposing side;
- use existing border/background/focus Tailwind utilities; do not add CSS files.

Add a `Button` labeled `Randomize Rivalry` using the existing Button component.

- [ ] **Step 5: Add roster/setup state to `MatchAdminControls`**

Add state:

```ts
const [roster, setRoster] = useState<PersonalityRosterItem[]>([]);
const [rosterLoading, setRosterLoading] = useState(true);
const [rosterError, setRosterError] = useState<string>();
const [whitePersonalityKey, setWhitePersonalityKey] = useState("");
const [blackPersonalityKey, setBlackPersonalityKey] = useState("");
```

Read `whitePersonality` and `blackPersonality` from `useMatchViewerStore()` in addition to current fields.

Add `loadRoster` with exact outcomes:

```ts
const loadRoster = useCallback(async () => {
  setRosterLoading(true);
  setRosterError(undefined);
  try {
    const loaded = await personalityApi.listSelectable();
    setRoster(loaded);
    if (loaded.length < 2) {
      setRosterError("At least two active personalities are required to start a rivalry.");
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

Add a synchronization effect with these rules:
- if the store has authoritative `whitePersonality` + `blackPersonality` for an active/stopped/completed match, copy their keys into local selection;
- otherwise, after a roster of at least two loads and either local key is missing, initialize to `roster[0].key` and `roster[1].key`;
- never overwrite a user's already-complete distinct pre-match selection merely because the roster effect runs again.

Implement randomize:

```ts
const randomize = () => {
  const next = randomizeRivalry(roster);
  setWhitePersonalityKey(next.whitePersonalityKey);
  setBlackPersonalityKey(next.blackPersonalityKey);
};
```

- [ ] **Step 6: Render loading/error/setup states**

Above the Start/Stop buttons:

- while `rosterLoading`, render `Loading personalities…`;
- when `rosterError` is set, render it with `role="alert"` and a `Retry` outline button calling `loadRoster`;
- when roster has at least two entries, render `RivalrySetup`.

Set:

```ts
const rivalryLocked = running || matchStatus === "STOPPED";
const validSelection =
  whitePersonalityKey !== "" &&
  blackPersonalityKey !== "" &&
  whitePersonalityKey !== blackPersonalityKey;
const canStartOrResume =
  matchStatus === "STOPPED" ||
  (!rosterLoading && rosterError === undefined && roster.length >= 2 && validSelection);
```

Disable the start/resume button when `isPending || !canStartOrResume`.

For STOPPED, keep setup disabled and label the action `Resume Match`; otherwise use `Start Match`.

- [ ] **Step 7: Send the selected keys**

Replace the current direct method reference with:

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

A stopped match sends the keys copied from the authoritative snapshot, satisfying the backend resume equality check.

- [ ] **Step 8: Expand `MatchAdminControls.test.tsx`**

Mock both `personalityApi.listSelectable` and `adminMatchApi.startMatch`.

Cover:
- loading state before roster resolves;
- four roster choices render after success;
- first two roster entries become initial White/Black defaults;
- selecting new distinct characters changes the start payload;
- randomization produces distinct keys (mock `Math.random` or mock the helper);
- start request contains exact selected keys;
- empty/one-item roster disables Start and shows explicit message;
- roster API rejection shows error and Retry;
- stopped-match hydration locks selectors, shows `Resume Match`, and sends stored keys;
- existing stop/cooldown/daily-limit/401 behavior still passes.

- [ ] **Step 9: Run admin UI tests**

```bash
cd client
npm test -- rivalrySelection.test.ts RivalrySetup.test.tsx MatchAdminControls.test.tsx
```

Expected: PASS.

- [ ] **Step 10: Commit Task 5**

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

### Task 6: Show Selected Identities in the Live Viewer and Verify End-to-End

**Files:**
- Modify: `client/src/features/match-viewer/components/PlayerStrip.tsx`
- Create/Test: `client/src/features/match-viewer/components/PlayerStrip.test.tsx`
- Verify only: existing backend/frontend files from Tasks 1–5

**Interfaces:**
- Consumes `whitePersonality`/`blackPersonality` already stored by `matchViewerStore`.
- The viewer does not fetch `/personalities`; identity comes from authoritative match state/start payloads.

- [ ] **Step 1: Write failing `PlayerStrip` tests**

Cover:
- White strip renders `Blaze` when `whitePersonality = { key: "blaze", displayName: "Blaze" }`;
- Black strip renders `Vesper` from `blackPersonality`;
- before any match identity exists, fallback remains `Stockfish White` / `Stockfish Black` so the empty viewer does not render blank labels;
- active-turn `Thinking...` behavior remains unchanged.

- [ ] **Step 2: Make `PlayerStrip` identity-aware**

Replace the fixed-name selection with:

```tsx
const { activeTurn, matchStatus, whitePersonality, blackPersonality } =
  useMatchViewerStore();
const personality = side === "WHITE" ? whitePersonality : blackPersonality;
const displayName = personality?.displayName ?? MATCH_PLAYERS[side].name;
```

Render `displayName` in the existing name span. Do not alter layout/styling except as required for long names to wrap safely.

- [ ] **Step 3: Run all frontend tests touched by #44**

```bash
cd client
npm test -- personalityApi.test.ts adminMatchApi.test.ts matchViewerStore.test.ts rivalrySelection.test.ts RivalrySetup.test.tsx MatchAdminControls.test.tsx PlayerStrip.test.tsx
```

Expected: PASS.

- [ ] **Step 4: Run frontend verification**

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

- [ ] **Step 7: Perform manual #44 acceptance with zero move delay**

Start the application using `docs/BUILD_AND_VERIFY.md`, setting both move delays to `0s` for practical testing.

Verify in this order:

1. Open the owner/admin page; four seeded active personalities load in stable roster order.
2. White and Black selectors show display names/descriptions and cannot choose the same key.
3. Click `Randomize Rivalry` repeatedly (at least 20 clicks); every pair is distinct.
4. Start `Blaze` vs a non-Vesper opponent. Confirm request succeeds and the live viewer immediately shows the selected names.
5. Confirm game-start/move dialogue uses the selected pair; no line comes from an unselected hard-coded character.
6. Refresh the viewer during the match. Confirm the same two names return from `MATCH_STATE` hydration.
7. Stop the match. Confirm selectors are locked to the stored pair and action label is `Resume Match`.
8. Resume. Confirm the same identities remain and dialogue continues with them.
9. Let the match complete. Refresh and confirm selected identities still display with the completed board/history.
10. Change both selectors and start another match. Confirm the new match has the new pair while the old match was never mutated.
11. Directly call start with duplicate keys and with a nonexistent key. Confirm both return `400`, not `500`.
12. Temporarily make the roster request fail in browser dev tools/network blocking. Confirm the admin UI shows a retryable error and does not permit a new match start.
13. Check the 360px viewport: selectors stack/wrap without horizontal overflow and existing viewer responsiveness remains intact.

- [ ] **Step 8: Commit the viewer/verification slice**

```bash
git add client/src/features/match-viewer/components/PlayerStrip.tsx \
  client/src/features/match-viewer/components/PlayerStrip.test.tsx
git commit -m "feat: show selected match personalities"
```

- [ ] **Step 9: Final diff discipline check**

```bash
git status --short
git diff master...HEAD --stat
```

Expected:
- no migration or dependency-file changes;
- no unrelated refactors;
- only files listed in this plan (plus formatting-only changes required by existing tools);
- all six task commits present.

## Completion Checklist

- [ ] All four active system personalities are selectable for both colors.
- [ ] Randomize always returns distinct personalities.
- [ ] Missing/blank/duplicate/unknown/inactive keys fail server-side with `400`.
- [ ] New match stores immutable resolved identities.
- [ ] Stop/resume preserves those identities without roster revalidation.
- [ ] Dialogue uses the selected identities instead of hard-coded defaults.
- [ ] REST snapshot restores identities after refresh.
- [ ] `MATCH_STARTED` carries identities for already-open live viewers.
- [ ] Player strips show the selected display names.
- [ ] Roster loading, too-small roster, and API-error states are handled.
- [ ] Existing start/stop/resume/cooldown/daily-limit/owner-token behavior remains green.
- [ ] Backend `verify`, frontend `verify`, and root verifier all pass.

## Execution Handoff

Luna should execute **inline only** with `superpowers:executing-plans`, one task at a time, running each task's focused tests before committing. Do not dispatch subagents. If the current code differs from this plan, stop only for a material interface/architecture mismatch; for trivial line movement or formatting differences, adapt mechanically while preserving the exact behavior and constraints above.
