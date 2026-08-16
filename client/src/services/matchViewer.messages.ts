import type { MatchStreamMessage } from "../types/match";
import type { MatchStreamMessageType } from "./matchSocket";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function hasFen(payload: unknown): payload is Record<string, unknown> {
  return isRecord(payload) && typeof payload.fen === "string";
}

function isSnapshotMove(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.sequenceNumber === "number" &&
    (value.player === "WHITE" || value.player === "BLACK") &&
    typeof value.notation === "string" &&
    typeof value.fenAfterMove === "string"
  );
}

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

function isStartAvailability(value: unknown): boolean {
  if (!isRecord(value)) return false;
  const validBlockReasons = [
    "MATCH_ALREADY_RUNNING",
    "MATCH_COOLDOWN_ACTIVE",
    "MATCH_DAILY_LIMIT_REACHED",
  ];
  return (
    typeof value.allowed === "boolean" &&
    (value.blockedBy === null ||
      (typeof value.blockedBy === "string" &&
        validBlockReasons.includes(value.blockedBy))) &&
    typeof value.retryAfterSeconds === "number" &&
    typeof value.dailyStartsAccepted === "number" &&
    typeof value.dailyStartLimit === "number"
  );
}

export function parseMatchMessage(data: unknown): MatchStreamMessage | null {
  if (!isRecord(data)) return null;
  const msg = data as { type?: string; payload?: unknown };
  const validTypes: MatchStreamMessageType[] = [
    "MATCH_STATE",
    "MATCH_STARTED",
    "MOVE_PLAYED",
    "DIALOGUE_PLAYED",
    "MATCH_STOPPED",
    "MATCH_FINISHED",
    "NO_MATCH",
  ];
  if (!msg.type || !validTypes.includes(msg.type as MatchStreamMessageType)) {
    return null;
  }

  switch (msg.type) {
    case "NO_MATCH":
      return data as unknown as MatchStreamMessage;
    case "MATCH_STARTED":
      if (
        !hasFen(msg.payload) ||
        typeof msg.payload.matchId !== "string" ||
        (msg.payload.sideToMove !== "WHITE" &&
          msg.payload.sideToMove !== "BLACK")
      ) {
        return null;
      }
      break;
    case "MATCH_STATE":
      if (
        !hasFen(msg.payload) ||
        typeof msg.payload.matchId !== "string" ||
        (msg.payload.sideToMove !== "WHITE" &&
          msg.payload.sideToMove !== "BLACK") ||
        !Array.isArray(msg.payload.moves) ||
        !msg.payload.moves.every(isSnapshotMove) ||
        !Array.isArray(msg.payload.dialogue) ||
        !msg.payload.dialogue.every(isDialogue) ||
        typeof msg.payload.status !== "string" ||
        typeof msg.payload.running !== "boolean" ||
        !isStartAvailability(msg.payload.startAvailability)
      ) {
        return null;
      }
      break;
    case "DIALOGUE_PLAYED":
      if (!isDialogue(msg.payload)) {
        return null;
      }
      break;
    case "MOVE_PLAYED":
      if (
        !hasFen(msg.payload) ||
        (msg.payload.player !== "WHITE" && msg.payload.player !== "BLACK") ||
        typeof msg.payload.ply !== "number"
      ) {
        return null;
      }
      break;
    case "MATCH_STOPPED":
      if (
        !hasFen(msg.payload) ||
        (msg.payload.sideToMove !== "WHITE" &&
          msg.payload.sideToMove !== "BLACK") ||
        typeof msg.payload.totalPlies !== "number"
      ) {
        return null;
      }
      break;
    case "MATCH_FINISHED":
      if (!hasFen(msg.payload) || typeof msg.payload.result !== "string") {
        return null;
      }
      break;
  }

  return data as unknown as MatchStreamMessage;
}
