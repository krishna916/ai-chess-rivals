import { create } from "zustand";
import type {
  MatchStatus,
  ConnectionStatus,
  MatchStreamMessage,
} from "../types/match";

const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

interface MatchViewerState {
  boardFen: string;
  matchStatus: MatchStatus;
  connectionStatus: ConnectionStatus;
  activeTurn: "WHITE" | "BLACK";
  moveCount: number;
  result?: string;
  error?: string;
  setConnectionStatus: (status: ConnectionStatus) => void;
  setError: (error: string) => void;
  processMessage: (msg: MatchStreamMessage) => void;
}

export const useMatchViewerStore = create<MatchViewerState>((set) => ({
  boardFen: START_FEN,
  matchStatus: "IDLE",
  connectionStatus: "CONNECTING",
  activeTurn: "WHITE",
  moveCount: 0,

  setConnectionStatus: (status) => set({ connectionStatus: status }),
  setError: (error) => set({ error, connectionStatus: "ERROR" }),

  processMessage: (msg: MatchStreamMessage) => {
    switch (msg.type) {
      case "NO_MATCH":
        set({
          matchStatus: "IDLE",
          boardFen: START_FEN,
          result: undefined,
          moveCount: 0,
        });
        break;
      case "MATCH_STARTED":
        set({
          matchStatus: "IN_PROGRESS",
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.sideToMove,
          moveCount: 0,
          result: undefined,
        });
        break;
      case "MATCH_STATE":
        set({
          matchStatus: msg.payload.status === "NOT_STARTED" ? "IDLE" : msg.payload.status,
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.sideToMove,
          moveCount: msg.payload.moves ? msg.payload.moves.length : 0,
          result: msg.payload.result || undefined,
        });
        break;
      case "MOVE_PLAYED":
        console.log("MOVE_PLAYED received with fen:", msg.payload.fen);
        set({
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.player === "WHITE" ? "BLACK" : "WHITE",
          moveCount: msg.payload.ply,
        });
        break;
      case "MATCH_FINISHED":
        set({
          matchStatus: "FINISHED",
          boardFen: msg.payload.fen,
          result: msg.payload.result,
        });
        break;
    }
  },
}));
