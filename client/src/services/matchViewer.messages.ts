import type { MatchStreamMessage } from "../types/match";
import type { MatchStreamMessageType } from "./matchSocket";

export function parseMatchMessage(data: unknown): MatchStreamMessage | null {
  if (!data || typeof data !== "object") return null;
  const msg = data as { type?: string };
  const validTypes: MatchStreamMessageType[] = [
    "MATCH_STATE",
    "MATCH_STARTED",
    "MOVE_PLAYED",
    "MATCH_FINISHED",
    "NO_MATCH",
  ];
  if (msg.type && validTypes.includes(msg.type as MatchStreamMessageType)) {
    return data as MatchStreamMessage;
  }
  return null;
}
