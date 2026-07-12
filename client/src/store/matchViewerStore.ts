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
          boardFen: msg.state.fen,
          activeTurn: msg.state.turn,
          moveCount: msg.state.moveCount,
          result: undefined,
        });
        break;
      case "MATCH_STATE":
        set((state) => ({
          matchStatus: msg.status,
          boardFen: msg.state?.fen ?? state.boardFen,
          activeTurn: msg.state?.turn ?? state.activeTurn,
          moveCount: msg.state?.moveCount ?? state.moveCount,
          result: msg.state?.result,
        }));
        break;
      case "MOVE_PLAYED":
        set({
          boardFen: msg.state.fen,
          activeTurn: msg.state.turn,
          moveCount: msg.state.moveCount,
        });
        break;
      case "MATCH_FINISHED":
        set({
          matchStatus: "FINISHED",
          boardFen: msg.state.fen,
          result: msg.state.result,
        });
        break;
    }
  },
}));
