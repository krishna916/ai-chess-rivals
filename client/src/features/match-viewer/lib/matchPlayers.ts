import type { PlayerColor } from "@/types/match";

export const MATCH_PLAYERS = {
  WHITE: { id: "WHITE", name: "Stockfish White" },
  BLACK: { id: "BLACK", name: "Stockfish Black" },
} as const satisfies Record<PlayerColor, { id: PlayerColor; name: string }>;
