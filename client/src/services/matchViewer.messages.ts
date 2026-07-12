import type { MatchStreamMessage } from "../types/match";
import type { MatchStreamMessageType } from "./matchSocket";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function hasFen(payload: unknown): payload is Record<string, unknown> {
  return isRecord(payload) && typeof payload.fen === "string";
}

export function parseMatchMessage(data: unknown): MatchStreamMessage | null {
  if (!isRecord(data)) return null;
  const msg = data as { type?: string; payload?: unknown };
  const validTypes: MatchStreamMessageType[] = [
    "MATCH_STATE",
    "MATCH_STARTED",
    "MOVE_PLAYED",
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
        (msg.payload.sideToMove !== "WHITE" &&
          msg.payload.sideToMove !== "BLACK")
      ) {
        return null;
      }
      break;
    case "MATCH_STATE":
      if (
        !hasFen(msg.payload) ||
        (msg.payload.sideToMove !== "WHITE" &&
          msg.payload.sideToMove !== "BLACK") ||
        !Array.isArray(msg.payload.moves) ||
        typeof msg.payload.status !== "string"
      ) {
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
    case "MATCH_FINISHED":
      if (!hasFen(msg.payload) || typeof msg.payload.result !== "string") {
        return null;
      }
      break;
  }

  return data as unknown as MatchStreamMessage;
}
