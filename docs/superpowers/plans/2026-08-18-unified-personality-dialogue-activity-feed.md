# Unified Personality Dialogue Activity Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render persisted personality dialogue and existing chess events as one deterministic, accessible chronological activity feed that behaves identically for live WebSocket events and hydrated snapshots.

**Architecture:** Keep issue #45 frontend-only. The backend already provides authoritative persisted dialogue (`triggerPly`, personality identity, emotion, reaction type, persisted ID/timestamp) and authoritative White/Black personality identities, so the client can derive speaker side without changing persistence or API contracts. Introduce one small activity-mapping utility that converts moves/dialogue into a single `MatchActivityItem` union, defines deterministic ordering, and deduplicates by stable activity ID; make the Zustand store use that utility for both live events and `MATCH_STATE` hydration; render dialogue inside the existing activity panel with a text monogram, explicit character name/side, emotion, and reaction labels.

**Tech Stack:** React 19.2.7, TypeScript 6.0.2, Vite 8.1.0, Zustand 5.0.14, Tailwind CSS 4.3.1, Lucide React 1.22.0, Vitest 4.1.10, Testing Library, existing WebSocket event contracts.

**Spec:** `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`

## Source of Truth

- Issue: `#45 Phase 2: Render personality dialogue in the unified activity feed`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Completed dependency: `#43 Phase 2: Persist dialogue and integrate it into the match lifecycle`
- Completed dependency: `#44 Phase 2: Add personality selection and random rivalry setup`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Do not add a Maven or npm dependency.
- Do not modify backend Java code, database migrations, WebSocket message contracts, or REST DTOs for this issue.
- Do not add a second banter panel, board speech bubbles, animation framework, audio, or text-to-speech.
- The existing activity feed remains the only move/dialogue timeline.
- Retire the frontend-only standalone `dialogue` Zustand array when dialogue becomes a first-class `MatchActivityItem`; do not keep two independent client histories that can drift apart.
- Use persisted `DialogueResponse.id` as the stable dialogue deduplication key: activity ID is `dialogue-${id}`.
- Derive speaker side by comparing `DialogueResponse.personalityKey` with the authoritative `whitePersonality.key` and `blackPersonality.key` already stored from `MATCH_STARTED` / `MATCH_STATE`.
- If a malformed/stale dialogue line does not match either current personality key, render it with a neutral `Character` side label instead of crashing or guessing.
- Do not fetch the personality roster from the match viewer solely to obtain `avatarRef`. Use a lightweight monogram avatar/reference derived from `personalityDisplayName`; the persisted display name remains authoritative for the line.
- Preserve current move rendering, capture/check/checkmate/promotion annotations, result rendering, stop/resume behavior, connection state, and player strips.
- Reconstruct snapshot order to match the backend's live publication order: match start -> game-start dialogue -> move -> move dialogue -> game-end dialogue -> match finished.
- For multiple persisted dialogue lines with the same trigger type and ply, ascending persisted dialogue ID is the tie-breaker.
- Continue ignoring `DIALOGUE_PLAYED` events whose `matchId` differs from a known `currentMatchId`.
- Keep the activity panel internally scrollable and bounded; long dialogue must not expand it indefinitely.
- Speaker identity must not rely on color alone: always show character display name and a visible `White`, `Black`, or `Character` label.
- Apply frontend formatting before repository verification.

## File Map

### Create

- `client/src/features/match-viewer/lib/matchActivity.ts` — converts snapshot/live move and dialogue payloads into unified activities, derives speaker side, orders activities, and deduplicates stable IDs.
- `client/src/features/match-viewer/lib/matchActivity.test.ts` — focused tests for snapshot reconstruction, same-ply ordering, side derivation, and idempotent merge behavior.

### Modify

- `client/src/types/match.ts` — add `DIALOGUE` to the activity union and define the dialogue activity shape.
- `client/src/store/matchViewerStore.ts` — use unified activity mapping for `MATCH_STATE`, `MOVE_PLAYED`, `DIALOGUE_PLAYED`, and `MATCH_FINISHED`; remove standalone dialogue state.
- `client/src/store/matchViewerStore.test.ts` — cover live append, hydration, ordering, deduplication, stale-match filtering, and no-match reset through the unified feed.
- `client/src/features/match-viewer/components/MatchActivityItem.tsx` — render accessible personality dialogue rows without regressing move/result rows.
- `client/src/features/match-viewer/components/MatchActivityPanel.tsx` — expose stable test hooks for the bounded scroll container/feed; retain existing scrolling behavior.
- `client/src/features/match-viewer/components/MatchActivityPanel.test.tsx` — cover White/Black dialogue rendering, accessible identity labels, metadata labels, feed ordering, and long-history scrolling constraints.

### Explicitly Do Not Modify

- `server/**`
- `client/package.json`
- `client/src/features/admin/**`
- `client/src/features/match-viewer/components/PlayerStrip.tsx`
- `client/src/pages/MatchViewerPage.tsx`
- Stockfish, evaluation, dialogue generation, prompt, provider, persistence, or roster behavior.

---

### Task 1: Define Unified Dialogue Activities and Deterministic Ordering

**Files:**
- Modify: `client/src/types/match.ts`
- Create: `client/src/features/match-viewer/lib/matchActivity.ts`
- Create: `client/src/features/match-viewer/lib/matchActivity.test.ts`

**Interfaces:**
- Produces `DialogueActivityItem` as part of `MatchActivityItem`.
- Produces `buildSnapshotActivities(snapshot: MatchResponse): MatchActivityItem[]`.
- Produces `toLiveMoveActivity(payload: MovePlayedMessage["payload"]): MoveActivityItem`.
- Produces `toDialogueActivity(dialogue, whitePersonality, blackPersonality): DialogueActivityItem`.
- Produces `mergeMatchActivity(activities, next): MatchActivityItem[]`.
- Produces `compareMatchActivities(a, b): number` with stable same-ply ordering.

- [ ] **Step 1: Extend the activity model with a first-class dialogue item**

In `client/src/types/match.ts`, replace the current `MatchActivityKind` declaration with:

```ts
export type MatchActivityKind =
  | "MATCH_STARTED"
  | "MOVE"
  | "DIALOGUE"
  | "MATCH_FINISHED";
```

Add this interface after `MoveActivityItem`:

```ts
export interface DialogueActivityItem {
  id: string;
  kind: "DIALOGUE";
  sequence: number;
  dialogueId: number;
  triggerType: DialogueTriggerType;
  personalityKey: string;
  personalityDisplayName: string;
  side: PlayerColor | null;
  text: string;
  emotion: DialogueEmotion;
  reactionType: DialogueReactionType;
  createdAt: string;
}
```

Replace the `MatchActivityItem` union with:

```ts
export type MatchActivityItem =
  | MatchStartedActivityItem
  | MoveActivityItem
  | DialogueActivityItem
  | MatchFinishedActivityItem;
```

- [ ] **Step 2: Write the failing activity-ordering tests**

Create `client/src/features/match-viewer/lib/matchActivity.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import type {
  DialogueResponse,
  MatchActivityItem,
  MatchResponse,
  MoveResponse,
} from "@/types/match";
import {
  buildSnapshotActivities,
  mergeMatchActivity,
  toDialogueActivity,
} from "./matchActivity";

const whitePersonality = { key: "blaze", displayName: "Blaze" };
const blackPersonality = { key: "vesper", displayName: "Vesper" };

function move(
  sequenceNumber: number,
  player: "WHITE" | "BLACK",
): MoveResponse {
  const white = player === "WHITE";
  return {
    sequenceNumber,
    player,
    notation: white ? "e2e4" : "e7e5",
    fenAfterMove: `fen-${sequenceNumber}`,
    movingPiece: "PAWN",
    movingPieceColor: player,
    sourceSquare: white ? "e2" : "e7",
    destinationSquare: white ? "e4" : "e5",
    capturedPiece: null,
    capturedPieceColor: null,
    promotedPiece: null,
    castlingSide: null,
    capture: false,
    check: false,
    checkmate: false,
    promotion: false,
  };
}

function dialogue(
  id: number,
  triggerType: DialogueResponse["triggerType"],
  triggerPly: number,
  personalityKey: string,
  personalityDisplayName: string,
): DialogueResponse {
  return {
    id,
    matchId: "match-1",
    triggerType,
    triggerPly,
    personalityKey,
    personalityDisplayName,
    text: `line-${id}`,
    emotion: "CONFIDENT",
    reactionType:
      triggerType === "GAME_START"
        ? "GAME_START"
        : triggerType === "GAME_END"
          ? "VICTORY"
          : "MOVE_REACTION",
    source: "DETERMINISTIC_FALLBACK",
    createdAt: `2026-08-18T00:00:${id.toString().padStart(2, "0")}Z`,
  };
}

function snapshot(): MatchResponse {
  return {
    matchId: "match-1",
    whitePersonality,
    blackPersonality,
    sideToMove: "WHITE",
    fen: "final-fen",
    moves: [move(2, "BLACK"), move(1, "WHITE")],
    status: "FINISHED",
    result: "WHITE_WINS",
    running: false,
    startAvailability: {
      allowed: true,
      blockedBy: null,
      retryAfterSeconds: 0,
      dailyStartsAccepted: 1,
      dailyStartLimit: 12,
    },
    dialogue: [
      dialogue(14, "GAME_END", 2, "vesper", "Vesper"),
      dialogue(12, "MOVE", 2, "blaze", "Blaze"),
      dialogue(10, "GAME_START", 0, "blaze", "Blaze"),
      dialogue(13, "GAME_END", 2, "blaze", "Blaze"),
      dialogue(11, "MOVE", 1, "vesper", "Vesper"),
    ],
  };
}

describe("matchActivity", () => {
  it("rebuilds snapshots in the same order as live backend publication", () => {
    const activities = buildSnapshotActivities(snapshot());

    expect(activities.map((activity) => activity.id)).toEqual([
      "match-started",
      "dialogue-10",
      "move-1",
      "dialogue-11",
      "move-2",
      "dialogue-12",
      "dialogue-13",
      "dialogue-14",
      "match-finished",
    ]);
  });

  it("derives White and Black from the authoritative rivalry keys", () => {
    expect(
      toDialogueActivity(
        dialogue(1, "MOVE", 1, "blaze", "Blaze"),
        whitePersonality,
        blackPersonality,
      ).side,
    ).toBe("WHITE");
    expect(
      toDialogueActivity(
        dialogue(2, "MOVE", 1, "vesper", "Vesper"),
        whitePersonality,
        blackPersonality,
      ).side,
    ).toBe("BLACK");
    expect(
      toDialogueActivity(
        dialogue(3, "MOVE", 1, "unknown", "Unknown"),
        whitePersonality,
        blackPersonality,
      ).side,
    ).toBeNull();
  });

  it("deduplicates by stable activity id and keeps same-ply dialogue after the move", () => {
    const moveActivity = buildSnapshotActivities({
      ...snapshot(),
      status: "IN_PROGRESS",
      result: null,
      moves: [move(1, "WHITE")],
      dialogue: [],
    }).find((activity) => activity.id === "move-1") as MatchActivityItem;
    const dialogueActivity = toDialogueActivity(
      dialogue(11, "MOVE", 1, "vesper", "Vesper"),
      whitePersonality,
      blackPersonality,
    );

    const once = mergeMatchActivity([moveActivity], dialogueActivity);
    const twice = mergeMatchActivity(once, dialogueActivity);

    expect(twice.map((activity) => activity.id)).toEqual([
      "move-1",
      "dialogue-11",
    ]);
  });
});
```

- [ ] **Step 3: Run the focused test and confirm RED**

From `client/` run:

```bash
npm test -- src/features/match-viewer/lib/matchActivity.test.ts
```

Expected: FAIL because `DialogueActivityItem` and `matchActivity.ts` do not exist yet.

- [ ] **Step 4: Create the activity mapping and ordering utility**

Create `client/src/features/match-viewer/lib/matchActivity.ts`:

```ts
import type {
  DialogueActivityItem,
  DialogueResponse,
  MatchActivityItem,
  MatchPersonality,
  MatchResponse,
  MoveActivityItem,
  MovePlayedMessage,
  MoveResponse,
  PlayerColor,
} from "@/types/match";

function dialogueSide(
  dialogue: DialogueResponse,
  whitePersonality?: MatchPersonality,
  blackPersonality?: MatchPersonality,
): PlayerColor | null {
  if (dialogue.personalityKey === whitePersonality?.key) {
    return "WHITE";
  }
  if (dialogue.personalityKey === blackPersonality?.key) {
    return "BLACK";
  }
  return null;
}

function toSnapshotMoveActivity(move: MoveResponse): MoveActivityItem {
  return {
    id: `move-${move.sequenceNumber}`,
    kind: "MOVE",
    sequence: move.sequenceNumber,
    player: move.player,
    notation: move.notation,
    movingPiece: move.movingPiece,
    movingPieceColor: move.movingPieceColor,
    sourceSquare: move.sourceSquare,
    destinationSquare: move.destinationSquare,
    capturedPiece: move.capturedPiece ?? undefined,
    capturedPieceColor: move.capturedPieceColor ?? undefined,
    promotedPiece: move.promotedPiece ?? undefined,
    castlingSide: move.castlingSide ?? undefined,
    capture: move.capture,
    check: move.check,
    checkmate: move.checkmate,
    promotion: move.promotion,
    isNew: false,
  };
}

export function toLiveMoveActivity(
  move: MovePlayedMessage["payload"],
): MoveActivityItem {
  return {
    id: `move-${move.ply}`,
    kind: "MOVE",
    sequence: move.ply,
    player: move.player,
    notation: move.notation,
    movingPiece: move.movingPiece,
    movingPieceColor: move.movingPieceColor,
    sourceSquare: move.sourceSquare,
    destinationSquare: move.destinationSquare,
    capturedPiece: move.capturedPiece ?? undefined,
    capturedPieceColor: move.capturedPieceColor ?? undefined,
    promotedPiece: move.promotedPiece ?? undefined,
    castlingSide: move.castlingSide ?? undefined,
    capture: move.capture,
    check: move.check,
    checkmate: move.checkmate,
    promotion: move.promotion,
    isNew: true,
  };
}

export function toDialogueActivity(
  dialogue: DialogueResponse,
  whitePersonality?: MatchPersonality,
  blackPersonality?: MatchPersonality,
): DialogueActivityItem {
  return {
    id: `dialogue-${dialogue.id}`,
    kind: "DIALOGUE",
    sequence: dialogue.triggerPly,
    dialogueId: dialogue.id,
    triggerType: dialogue.triggerType,
    personalityKey: dialogue.personalityKey,
    personalityDisplayName: dialogue.personalityDisplayName,
    side: dialogueSide(dialogue, whitePersonality, blackPersonality),
    text: dialogue.text,
    emotion: dialogue.emotion,
    reactionType: dialogue.reactionType,
    createdAt: dialogue.createdAt,
  };
}

function activityRank(activity: MatchActivityItem): number {
  switch (activity.kind) {
    case "MATCH_STARTED":
      return 0;
    case "MOVE":
      return 10;
    case "DIALOGUE":
      switch (activity.triggerType) {
        case "GAME_START":
          return 5;
        case "MOVE":
          return 20;
        case "GAME_END":
          return 30;
      }
    case "MATCH_FINISHED":
      return 40;
  }
}

export function compareMatchActivities(
  left: MatchActivityItem,
  right: MatchActivityItem,
): number {
  const sequenceDifference = left.sequence - right.sequence;
  if (sequenceDifference !== 0) {
    return sequenceDifference;
  }

  const rankDifference = activityRank(left) - activityRank(right);
  if (rankDifference !== 0) {
    return rankDifference;
  }

  if (left.kind === "DIALOGUE" && right.kind === "DIALOGUE") {
    return left.dialogueId - right.dialogueId;
  }

  return left.id.localeCompare(right.id);
}

export function mergeMatchActivity(
  activities: MatchActivityItem[],
  next: MatchActivityItem,
): MatchActivityItem[] {
  return [...activities.filter((activity) => activity.id !== next.id), next].sort(
    compareMatchActivities,
  );
}

export function buildSnapshotActivities(
  snapshot: MatchResponse,
): MatchActivityItem[] {
  if (snapshot.status === "IDLE" || snapshot.status === "NOT_STARTED") {
    return [];
  }

  const lastPly = snapshot.moves.reduce(
    (highest, move) => Math.max(highest, move.sequenceNumber),
    0,
  );
  const activities: MatchActivityItem[] = [
    { id: "match-started", kind: "MATCH_STARTED", sequence: 0 },
    ...snapshot.moves.map(toSnapshotMoveActivity),
    ...snapshot.dialogue.map((dialogue) =>
      toDialogueActivity(
        dialogue,
        snapshot.whitePersonality,
        snapshot.blackPersonality,
      ),
    ),
  ];

  if (snapshot.status === "FINISHED" && snapshot.result) {
    activities.push({
      id: "match-finished",
      kind: "MATCH_FINISHED",
      sequence: lastPly + 1,
      result: snapshot.result,
    });
  }

  return activities.sort(compareMatchActivities);
}
```

- [ ] **Step 5: Run the focused test and confirm GREEN**

```bash
npm test -- src/features/match-viewer/lib/matchActivity.test.ts
```

Expected: PASS with all three activity mapping/order tests green.

- [ ] **Step 6: Format the task files and rerun the focused test**

```bash
npx prettier --write src/types/match.ts src/features/match-viewer/lib/matchActivity.ts src/features/match-viewer/lib/matchActivity.test.ts
npm test -- src/features/match-viewer/lib/matchActivity.test.ts
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```bash
git add client/src/types/match.ts \
  client/src/features/match-viewer/lib/matchActivity.ts \
  client/src/features/match-viewer/lib/matchActivity.test.ts
git commit -m "feat: model unified match dialogue activities"
```

---

### Task 2: Make Zustand Use One Authoritative Activity History for Live and Hydrated Data

**Files:**
- Modify: `client/src/store/matchViewerStore.ts`
- Modify: `client/src/store/matchViewerStore.test.ts`

**Interfaces:**
- Consumes the Task 1 mapping/ordering functions.
- `MatchViewerState.activities` becomes the only frontend match-history collection.
- `DIALOGUE_PLAYED` produces `DialogueActivityItem` entries directly in `activities`.
- `MATCH_STATE` replaces the whole client activity history with `buildSnapshotActivities(msg.payload)`, making refresh/reconnect idempotent.

- [ ] **Step 1: Update the store test fixture so dialogue overrides any persisted field**

In `client/src/store/matchViewerStore.test.ts`, add `DialogueResponse` to the type imports:

```ts
import type {
  DialogueResponse,
  MovePlayedMessage,
  MoveResponse,
  StartAvailability,
} from "../types/match";
```

Replace the bottom `dialogue` fixture with:

```ts
function dialogue(overrides: Partial<DialogueResponse> = {}): DialogueResponse {
  return {
    id: 1,
    matchId: "match-1",
    triggerType: "MOVE",
    triggerPly: 1,
    personalityKey: "blaze",
    personalityDisplayName: "Blaze",
    text: "hello",
    emotion: "CONFIDENT",
    reactionType: "MOVE_REACTION",
    source: "DETERMINISTIC_FALLBACK",
    createdAt: "2026-08-16T00:00:00Z",
    ...overrides,
  };
}
```

- [ ] **Step 2: Replace the old standalone-dialogue tests with failing unified-feed tests**

Remove the existing tests named:

```text
hydrates dialogue in ascending persisted id order
deduplicates live dialogue and ignores another match
```

Add these tests inside `describe("matchViewerStore", ...)`:

```ts
it("hydrates moves and persisted dialogue in authoritative chronological order", () => {
  const message = {
    type: "MATCH_STATE" as const,
    payload: {
      matchId: "match-1",
      ...matchPersonalities,
      status: "FINISHED" as const,
      fen: "finished",
      sideToMove: "WHITE" as const,
      result: "WHITE_WINS",
      running: false,
      startAvailability,
      moves: [
        snapshotMove({ sequenceNumber: 2, player: "BLACK" }),
        snapshotMove({ sequenceNumber: 1, player: "WHITE" }),
      ],
      dialogue: [
        dialogue({
          id: 14,
          triggerType: "GAME_END",
          triggerPly: 2,
          personalityKey: "vesper",
          personalityDisplayName: "Vesper",
          reactionType: "DEFEAT",
        }),
        dialogue({ id: 12, triggerType: "MOVE", triggerPly: 2 }),
        dialogue({ id: 10, triggerType: "GAME_START", triggerPly: 0 }),
        dialogue({
          id: 13,
          triggerType: "GAME_END",
          triggerPly: 2,
          reactionType: "VICTORY",
        }),
        dialogue({
          id: 11,
          triggerType: "MOVE",
          triggerPly: 1,
          personalityKey: "vesper",
          personalityDisplayName: "Vesper",
        }),
      ],
    },
  };

  useMatchViewerStore.getState().processMessage(message);
  useMatchViewerStore.getState().processMessage(message);

  const activities = useMatchViewerStore.getState().activities;
  expect(activities.map((activity) => activity.id)).toEqual([
    "match-started",
    "dialogue-10",
    "move-1",
    "dialogue-11",
    "move-2",
    "dialogue-12",
    "dialogue-13",
    "dialogue-14",
    "match-finished",
  ]);
  expect(activities.find((activity) => activity.id === "dialogue-10")).toMatchObject({
    kind: "DIALOGUE",
    side: "WHITE",
  });
  expect(activities.find((activity) => activity.id === "dialogue-11")).toMatchObject({
    kind: "DIALOGUE",
    side: "BLACK",
  });
});

it("appends live dialogue after its triggering move, deduplicates it, and ignores another match", () => {
  useMatchViewerStore.getState().processMessage({
    type: "MATCH_STARTED",
    payload: {
      matchId: "match-1",
      ...matchPersonalities,
      fen: "start",
      sideToMove: "WHITE",
    },
  });
  useMatchViewerStore.getState().processMessage({
    type: "DIALOGUE_PLAYED",
    payload: dialogue({ id: 1, triggerType: "GAME_START", triggerPly: 0 }),
  });
  useMatchViewerStore.getState().processMessage({
    type: "MOVE_PLAYED",
    payload: liveMove(),
  });
  const moveDialogue = {
    type: "DIALOGUE_PLAYED" as const,
    payload: dialogue({
      id: 2,
      triggerType: "MOVE",
      triggerPly: 1,
      personalityKey: "vesper",
      personalityDisplayName: "Vesper",
    }),
  };

  useMatchViewerStore.getState().processMessage(moveDialogue);
  useMatchViewerStore.getState().processMessage(moveDialogue);
  useMatchViewerStore.getState().processMessage({
    type: "DIALOGUE_PLAYED",
    payload: dialogue({ id: 3, matchId: "other-match" }),
  });

  expect(useMatchViewerStore.getState().activities.map((activity) => activity.id)).toEqual([
    "match-started",
    "dialogue-1",
    "move-1",
    "dialogue-2",
  ]);
});

it("keeps terminal move dialogue and game-end dialogue before the final result", () => {
  useMatchViewerStore.getState().processMessage({
    type: "MATCH_STARTED",
    payload: {
      matchId: "match-1",
      ...matchPersonalities,
      fen: "start",
      sideToMove: "WHITE",
    },
  });
  useMatchViewerStore.getState().processMessage({
    type: "MOVE_PLAYED",
    payload: liveMove({ check: true, checkmate: true }),
  });
  useMatchViewerStore.getState().processMessage({
    type: "DIALOGUE_PLAYED",
    payload: dialogue({ id: 20, triggerType: "MOVE", triggerPly: 1 }),
  });
  useMatchViewerStore.getState().processMessage({
    type: "DIALOGUE_PLAYED",
    payload: dialogue({
      id: 21,
      triggerType: "GAME_END",
      triggerPly: 1,
      reactionType: "VICTORY",
    }),
  });
  useMatchViewerStore.getState().processMessage({
    type: "DIALOGUE_PLAYED",
    payload: dialogue({
      id: 22,
      triggerType: "GAME_END",
      triggerPly: 1,
      personalityKey: "vesper",
      personalityDisplayName: "Vesper",
      reactionType: "DEFEAT",
    }),
  });
  useMatchViewerStore.getState().processMessage({
    type: "MATCH_FINISHED",
    payload: { result: "WHITE_WINS", fen: "mate", totalPlies: 1 },
  });

  expect(useMatchViewerStore.getState().activities.map((activity) => activity.id)).toEqual([
    "match-started",
    "move-1",
    "dialogue-20",
    "dialogue-21",
    "dialogue-22",
    "match-finished",
  ]);
});
```

- [ ] **Step 3: Update existing store setup/assertions to remove standalone dialogue state**

In the `beforeEach` state reset and in tests for `MATCH_STARTED` / `NO_MATCH`, remove all `dialogue: []` and `dialogue: [dialogue()]` state fields/assertions. Keep `activities`, identities, result, error, and `startAvailability` assertions unchanged.

In the existing `hydrates ordered structured moves and a final result` test, change:

```ts
dialogue: [dialogue({ id: 2 }), dialogue({ id: 1 })],
```

to:

```ts
dialogue: [],
```

so that test remains focused on structured move/result hydration; the new test above owns dialogue ordering coverage.

- [ ] **Step 4: Run the store tests and confirm RED**

```bash
npm test -- src/store/matchViewerStore.test.ts
```

Expected: FAIL because `DIALOGUE_PLAYED` still updates the standalone `dialogue` array and `MATCH_STATE` does not merge persisted dialogue into `activities`.

- [ ] **Step 5: Replace local activity reconstruction with Task 1 helpers**

In `client/src/store/matchViewerStore.ts`, remove `DialogueResponse` from imports and delete the entire local `reconstructMoveActivities` function.

Add:

```ts
import {
  buildSnapshotActivities,
  mergeMatchActivity,
  toDialogueActivity,
  toLiveMoveActivity,
} from "@/features/match-viewer/lib/matchActivity";
```

Remove this state field from `MatchViewerState`:

```ts
dialogue: DialogueResponse[];
```

Remove this initial state field:

```ts
dialogue: [],
```

- [ ] **Step 6: Make `NO_MATCH` and `MATCH_STARTED` reset only the unified activity history**

In the `NO_MATCH` case, remove:

```ts
dialogue: [],
```

In the `MATCH_STARTED` case, remove:

```ts
dialogue: [],
```

Keep the existing `activities: [{ id: "match-started", kind: "MATCH_STARTED", sequence: 0 }]` reset exactly as the new-match boundary.

- [ ] **Step 7: Rebuild `MATCH_STATE` activities from moves + persisted dialogue in one call**

Inside `MATCH_STATE`, replace the local `activities` construction block with:

```ts
const activities = buildSnapshotActivities(msg.payload);
```

Keep the existing `lastPly` and status normalization logic because board/status semantics do not belong in the activity helper.

In the final `set`, replace the current `activities` and `dialogue` assignments with only:

```ts
activities,
```

The rest of the hydrated state remains:

```ts
set({
  matchStatus: status,
  boardFen: msg.payload.fen,
  activeTurn: msg.payload.sideToMove,
  moveCount: lastPly,
  result: msg.payload.result || undefined,
  activities,
  currentMatchId: msg.payload.matchId,
  whitePersonality: msg.payload.whitePersonality,
  blackPersonality: msg.payload.blackPersonality,
  startAvailability: msg.payload.startAvailability,
});
```

- [ ] **Step 8: Route live moves through the common merge function**

Replace the current `MOVE_PLAYED` case body with:

```ts
case "MOVE_PLAYED":
  set((state) => ({
    boardFen: msg.payload.fen,
    activeTurn: msg.payload.player === "WHITE" ? "BLACK" : "WHITE",
    moveCount: msg.payload.ply,
    activities: mergeMatchActivity(
      state.activities,
      toLiveMoveActivity(msg.payload),
    ),
  }));
  break;
```

- [ ] **Step 9: Route live dialogue directly into the unified feed**

Replace the current `DIALOGUE_PLAYED` case with:

```ts
case "DIALOGUE_PLAYED":
  set((state) => {
    if (
      state.currentMatchId !== undefined &&
      msg.payload.matchId !== state.currentMatchId
    ) {
      return state;
    }

    return {
      currentMatchId: state.currentMatchId ?? msg.payload.matchId,
      activities: mergeMatchActivity(
        state.activities,
        toDialogueActivity(
          msg.payload,
          state.whitePersonality,
          state.blackPersonality,
        ),
      ),
    };
  });
  break;
```

- [ ] **Step 10: Route the final result through the same deduplicating merge**

Replace the current `MATCH_FINISHED` activity-filter/sort block with:

```ts
case "MATCH_FINISHED":
  set((state) => ({
    matchStatus: "FINISHED",
    boardFen: msg.payload.fen,
    result: msg.payload.result,
    activities: mergeMatchActivity(state.activities, {
      id: "match-finished",
      kind: "MATCH_FINISHED",
      sequence: msg.payload.totalPlies + 1,
      result: msg.payload.result,
    }),
  }));
  break;
```

- [ ] **Step 11: Run store + activity tests and confirm GREEN**

```bash
npm test -- src/features/match-viewer/lib/matchActivity.test.ts src/store/matchViewerStore.test.ts
```

Expected: PASS. Repeated `MATCH_STATE`, repeated `DIALOGUE_PLAYED`, repeated `MOVE_PLAYED`, and repeated `MATCH_FINISHED` inputs all leave one activity per stable ID.

- [ ] **Step 12: Format and commit Task 2**

```bash
npx prettier --write src/store/matchViewerStore.ts src/store/matchViewerStore.test.ts
npm test -- src/features/match-viewer/lib/matchActivity.test.ts src/store/matchViewerStore.test.ts
git add client/src/store/matchViewerStore.ts client/src/store/matchViewerStore.test.ts
git commit -m "feat: unify live and hydrated match activity"
```

---

### Task 3: Render Distinct, Accessible Personality Dialogue in the Existing Activity Panel

**Files:**
- Modify: `client/src/features/match-viewer/components/MatchActivityItem.tsx`
- Modify: `client/src/features/match-viewer/components/MatchActivityPanel.tsx`
- Modify: `client/src/features/match-viewer/components/MatchActivityPanel.test.tsx`

**Interfaces:**
- Consumes `DialogueActivityItem` from the existing `MatchActivityItem` union.
- Dialogue identity uses a monogram + persisted display name + explicit side label.
- Dialogue metadata displays human-readable emotion and reaction type.
- The activity panel remains bounded and `overflow-y-auto` regardless of dialogue volume.

- [ ] **Step 1: Add a dialogue fixture to `MatchActivityPanel.test.tsx`**

Add `within` to Testing Library imports:

```ts
import { cleanup, render, screen, within } from "@testing-library/react";
```

Add this helper after `moveActivity`:

```ts
function dialogueActivity(
  overrides: Partial<Extract<MatchActivityItem, { kind: "DIALOGUE" }>> = {},
): Extract<MatchActivityItem, { kind: "DIALOGUE" }> {
  return {
    id: "dialogue-1",
    kind: "DIALOGUE",
    sequence: 1,
    dialogueId: 1,
    triggerType: "MOVE",
    personalityKey: "blaze",
    personalityDisplayName: "Blaze",
    side: "WHITE",
    text: "That pawn move already has you worried.",
    emotion: "CONFIDENT",
    reactionType: "MOVE_REACTION",
    createdAt: "2026-08-18T00:00:00Z",
    ...overrides,
  };
}
```

- [ ] **Step 2: Add failing rendering/accessibility tests**

Add:

```ts
it("renders personality dialogue with visible identity, side, emotion, and reaction labels", () => {
  useMatchViewerStore.setState({
    activities: [
      dialogueActivity(),
      dialogueActivity({
        id: "dialogue-2",
        dialogueId: 2,
        personalityKey: "vesper",
        personalityDisplayName: "Vesper",
        side: "BLACK",
        text: "Worried? I call that optimism.",
        emotion: "DEFIANT",
        reactionType: "MOVE_REACTION",
      }),
    ],
  });

  render(<MatchActivityPanel />);

  const blaze = screen.getByTestId("activity-dialogue-1");
  expect(within(blaze).getByText("Blaze")).toBeInTheDocument();
  expect(within(blaze).getByText("White")).toBeInTheDocument();
  expect(within(blaze).getByText("Confident")).toBeInTheDocument();
  expect(within(blaze).getByText("Move reaction")).toBeInTheDocument();
  expect(
    within(blaze).getByText("That pawn move already has you worried."),
  ).toBeInTheDocument();
  expect(blaze).toHaveAccessibleName(
    "Blaze dialogue, White, Confident, Move reaction",
  );

  const vesper = screen.getByTestId("activity-dialogue-2");
  expect(within(vesper).getByText("Vesper")).toBeInTheDocument();
  expect(within(vesper).getByText("Black")).toBeInTheDocument();
  expect(within(vesper).getByText("Defiant")).toBeInTheDocument();
  expect(vesper).toHaveAccessibleName(
    "Vesper dialogue, Black, Defiant, Move reaction",
  );
});

it("renders an unmatched speaker neutrally instead of guessing a side", () => {
  useMatchViewerStore.setState({
    activities: [
      dialogueActivity({
        personalityKey: "unknown",
        personalityDisplayName: "Unknown",
        side: null,
      }),
    ],
  });

  render(<MatchActivityPanel />);

  const row = screen.getByTestId("activity-dialogue-1");
  expect(within(row).getByText("Character")).toBeInTheDocument();
  expect(row).toHaveAccessibleName(
    "Unknown dialogue, Character, Confident, Move reaction",
  );
});

it("keeps long dialogue histories inside the bounded scroll feed", () => {
  useMatchViewerStore.setState({
    activities: Array.from({ length: 50 }, (_, index) =>
      dialogueActivity({
        id: `dialogue-${index + 1}`,
        dialogueId: index + 1,
        sequence: index + 1,
        text: `Banter line ${index + 1}`,
      }),
    ),
  });

  render(<MatchActivityPanel />);

  expect(screen.getByTestId("match-activity-panel")).toHaveClass(
    "max-h-[600px]",
    "lg:max-h-[calc(100vh-200px)]",
  );
  expect(screen.getByTestId("match-activity-feed")).toHaveClass(
    "overflow-y-auto",
  );
  expect(screen.getByText("50 events")).toBeInTheDocument();
  expect(screen.getByText("Banter line 50")).toBeInTheDocument();
});
```

- [ ] **Step 3: Run the component test and confirm RED**

```bash
npm test -- src/features/match-viewer/components/MatchActivityPanel.test.tsx
```

Expected: FAIL because `MatchActivityItem` has no `DIALOGUE` rendering branch and the panel does not yet expose the two test IDs.

- [ ] **Step 4: Add stable test hooks without changing panel layout behavior**

In `MatchActivityPanel.tsx`, add `data-testid="match-activity-panel"` to the outer container:

```tsx
<div
  data-testid="match-activity-panel"
  className="bg-card shadow rounded-xl border flex flex-col h-full min-h-[400px] max-h-[600px] lg:max-h-[calc(100vh-200px)]"
>
```

Add `data-testid="match-activity-feed"` to the existing scroll element while preserving `id="match-activity-feed"`:

```tsx
<div
  ref={feedRef}
  data-testid="match-activity-feed"
  className="flex-1 overflow-y-auto p-4 space-y-3"
  id="match-activity-feed"
>
```

Do not change the existing auto-scroll effect.

- [ ] **Step 5: Add small formatting helpers for dialogue metadata and monograms**

In `MatchActivityItem.tsx`, add these functions above the component:

```ts
function formatDialogueLabel(value: string): string {
  const normalized = value.replace(/_/g, " ").toLowerCase();
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function dialogueMonogram(displayName: string): string {
  const initials = displayName
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
  return initials || "AI";
}
```

- [ ] **Step 6: Render the `DIALOGUE` branch inside `MatchActivityItem`**

Add this switch case before `MATCH_FINISHED`:

```tsx
case "DIALOGUE": {
  const sideLabel =
    activity.side === "WHITE"
      ? "White"
      : activity.side === "BLACK"
        ? "Black"
        : "Character";
  const emotionLabel = formatDialogueLabel(activity.emotion);
  const reactionLabel = formatDialogueLabel(activity.reactionType);

  return (
    <article
      data-testid={`activity-${activity.id}`}
      aria-label={`${activity.personalityDisplayName} dialogue, ${sideLabel}, ${emotionLabel}, ${reactionLabel}`}
      className={cn(
        "rounded-lg border px-3 py-2.5 text-sm",
        activity.side === "WHITE" &&
          "border-slate-200 bg-slate-50/70 dark:border-slate-700 dark:bg-slate-900/40",
        activity.side === "BLACK" &&
          "border-neutral-300 bg-neutral-100/70 dark:border-neutral-700 dark:bg-neutral-800/60",
        activity.side === null && "border-border bg-muted/40",
      )}
    >
      <div className="flex items-start gap-2.5">
        <span
          aria-hidden="true"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border bg-background text-xs font-bold text-foreground"
        >
          {dialogueMonogram(activity.personalityDisplayName)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="font-semibold text-foreground">
              {activity.personalityDisplayName}
            </span>
            <span className="rounded border border-border bg-background px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
              {sideLabel}
            </span>
          </div>
          <p className="mt-1 break-words leading-relaxed text-foreground">
            {activity.text}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[10px] text-muted-foreground">
            <span className="rounded bg-background/80 px-1.5 py-0.5">
              {emotionLabel}
            </span>
            <span className="rounded bg-background/80 px-1.5 py-0.5">
              {reactionLabel}
            </span>
          </div>
        </div>
      </div>
    </article>
  );
}
```

The White/Black backgrounds may differ visually, but the visible side badge and accessible name are the required non-color identity cues.

- [ ] **Step 7: Run the component tests and confirm GREEN**

```bash
npm test -- src/features/match-viewer/components/MatchActivityPanel.test.tsx
```

Expected: PASS, including the existing move/capture/check/result/scroll tests and the new dialogue/accessibility/long-history tests.

- [ ] **Step 8: Run all focused #45 frontend tests together**

```bash
npm test -- \
  src/features/match-viewer/lib/matchActivity.test.ts \
  src/store/matchViewerStore.test.ts \
  src/features/match-viewer/components/MatchActivityPanel.test.tsx
```

Expected: PASS.

- [ ] **Step 9: Format and commit Task 3**

```bash
npx prettier --write \
  src/features/match-viewer/components/MatchActivityItem.tsx \
  src/features/match-viewer/components/MatchActivityPanel.tsx \
  src/features/match-viewer/components/MatchActivityPanel.test.tsx
npm test -- src/features/match-viewer/components/MatchActivityPanel.test.tsx
git add \
  client/src/features/match-viewer/components/MatchActivityItem.tsx \
  client/src/features/match-viewer/components/MatchActivityPanel.tsx \
  client/src/features/match-viewer/components/MatchActivityPanel.test.tsx
git commit -m "feat: render personality dialogue in match activity"
```

---

### Task 4: Verify Regression Safety and Manually Accept the Unified Feed

**Files:**
- Verify only; no production file should be changed by this task after formatting is clean.

**Interfaces:**
- Confirms the complete #45 acceptance slice against the repository gates and real live/hydrated behavior.

- [ ] **Step 1: Run frontend formatting, type, lint, tests, and production build**

From `client/`:

```bash
npm run format
npm run format:check
npm run typecheck
npm run verify
```

Expected: every command exits `0`.

- [ ] **Step 2: Run the whole-repository verifier**

From repository root:

```bash
./scripts/verify.sh
```

On Windows PowerShell use:

```powershell
.\scripts\verify.ps1
```

Expected: backend and frontend verification both pass. No backend files should have changed.

- [ ] **Step 3: Start the normal local topology with fast chess pacing**

Terminal 1:

```bash
cd server
docker compose up -d postgres
GAME_MOVE_DELAY_MIN=0s GAME_MOVE_DELAY_MAX=0s ./mvnw spring-boot:run
```

PowerShell equivalent:

```powershell
cd server
docker compose up -d postgres
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
.\mvnw.cmd spring-boot:run
```

Terminal 2:

```bash
cd client
npm run dev
```

Expected: backend starts on the existing application port, frontend starts on `http://localhost:5173`, and the existing AI provider/fallback configuration is used unchanged.

- [ ] **Step 4: Verify live chronological rendering**

1. Open `http://localhost:5173/admin`.
2. Select two distinct personalities and start a new match using the existing owner controls.
3. Open `http://localhost:5173/`.
4. Confirm `Match started` is first.
5. Confirm game-start dialogue appears after `Match started` and before move 1.
6. Confirm move dialogue appears immediately after its triggering move, not before it or after a later move.
7. When the game finishes, confirm any terminal move dialogue appears after the terminal move, game-end dialogue appears after move dialogue, and `Match finished` is the final row.
8. Confirm existing Capture, Check, Mate, Promotion (if encountered), and result annotations still render exactly as before.

Expected: one chronological feed; no separate banter panel and no duplicated dialogue rows.

- [ ] **Step 5: Verify refresh/reconnect hydration is identical and idempotent**

1. During a match after several dialogue lines, note the visible order of the last five feed rows.
2. Refresh `http://localhost:5173/`.
3. Confirm those same rows reappear in the same order and exactly once.
4. In browser DevTools, switch the network offline long enough for the existing connection-loss state to appear, then restore network.
5. Confirm reconnect hydration does not duplicate any move or dialogue row and does not reorder the feed.

Expected: live event history and `MATCH_STATE` reconstruction are visually identical for the same authoritative match state.

- [ ] **Step 6: Verify speaker identity and accessibility cues**

For at least one line from each personality confirm:

- persisted display name is visible;
- `White` or `Black` text badge is visible;
- the monogram reference is visible;
- emotion text is visible;
- reaction text is visible;
- White/Black identity remains understandable without relying on background color.

Expected: the two personalities are immediately distinguishable by text identity even in monochrome/high-contrast interpretation.

- [ ] **Step 7: Verify the existing 360-pixel mobile target and long-history usability**

1. Set the browser responsive viewport width to `360px`.
2. Let the match accumulate a long feed or use a completed match with substantial dialogue history.
3. Confirm there is no horizontal overflow.
4. Confirm the board/player/status content remains readable above the activity panel.
5. Confirm the activity panel stays bounded and its history scrolls inside `#match-activity-feed` rather than expanding indefinitely.
6. Confirm long dialogue text wraps instead of clipping horizontally.

Expected: the existing mobile stacking behavior remains intact and long banter does not push core match UI into an unusable layout.

- [ ] **Step 8: Confirm the working tree contains only #45 scope**

```bash
git status --short
git diff --stat master...HEAD
```

Expected changed production scope:

```text
client/src/types/match.ts
client/src/features/match-viewer/lib/matchActivity.ts
client/src/store/matchViewerStore.ts
client/src/features/match-viewer/components/MatchActivityItem.tsx
client/src/features/match-viewer/components/MatchActivityPanel.tsx
```

Expected test scope:

```text
client/src/features/match-viewer/lib/matchActivity.test.ts
client/src/store/matchViewerStore.test.ts
client/src/features/match-viewer/components/MatchActivityPanel.test.tsx
```

No `server/**`, dependency manifest, admin flow, or unrelated refactor belongs in the implementation diff.

---

## Completion Criteria

Issue #45 is complete only when all of the following are true:

- Live `DIALOGUE_PLAYED` events render in `activities` immediately after their triggering activity.
- `MATCH_STATE` hydration reconstructs the same chronological move/dialogue/result order as live publication.
- Repeated live events and repeated hydration do not duplicate dialogue, moves, or the final result.
- Dialogue side is derived from the authoritative match personalities and displayed as text, not inferred from color alone.
- Dialogue rows show persisted character name, monogram reference, dialogue text, emotion, and reaction type.
- Existing move/capture/check/checkmate/promotion/result rendering remains green.
- Stop/resume, connection state, player identity, and new-match reset behavior remain green.
- The activity panel remains bounded and internally scrollable with long histories.
- Focused tests, `npm run verify`, and the root repository verifier pass.
- Manual refresh/reconnect and 360-pixel acceptance checks pass.

## Scope Guard for Luna

If implementation reveals a desire to add `avatarRef` to backend match/dialogue DTOs, add a viewer-specific personality cache, redesign the activity panel, add animations, or introduce a new state-management abstraction, stop and do **not** expand this issue. The accepted #45 requirements can be satisfied with the existing contracts plus a monogram reference and a small deterministic frontend activity mapper. Keep this slice frontend-only and ship it.