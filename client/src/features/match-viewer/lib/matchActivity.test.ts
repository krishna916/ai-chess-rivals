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

function move(sequenceNumber: number, player: "WHITE" | "BLACK"): MoveResponse {
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
