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
  return [
    ...activities.filter((activity) => activity.id !== next.id),
    next,
  ].sort(compareMatchActivities);
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
