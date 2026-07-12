import { create } from "zustand";
import type {
  MatchStatus,
  ConnectionStatus,
  MatchStreamMessage,
  MatchActivityItem,
} from "../types/match";

const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

interface MatchViewerState {
  boardFen: string;
  matchStatus: MatchStatus;
  connectionStatus: ConnectionStatus;
  activeTurn: "WHITE" | "BLACK";
  moveCount: number;
  activities: MatchActivityItem[];
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
  activities: [],

  setConnectionStatus: (status) =>
    set({
      connectionStatus: status,
      ...(status === "CONNECTED" ? { error: undefined } : {}),
    }),
  setError: (error) => set({ error, connectionStatus: "ERROR" }),

  processMessage: (msg: MatchStreamMessage) => {
    switch (msg.type) {
      case "NO_MATCH":
        set({
          matchStatus: "IDLE",
          boardFen: START_FEN,
          result: undefined,
          moveCount: 0,
          activities: [],
        });
        break;
      case "MATCH_STARTED":
        set({
          matchStatus: "IN_PROGRESS",
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.sideToMove,
          moveCount: 0,
          result: undefined,
          activities: [
            {
              id: "match-started",
              kind: "MATCH_STARTED",
              sequence: 0,
            },
          ],
        });
        break;
      case "MATCH_STATE": {
        const status =
          msg.payload.status === "NOT_STARTED" ? "IDLE" : msg.payload.status;
        const activities: MatchActivityItem[] = [];

        if (status !== "IDLE") {
          activities.push({
            id: "match-started",
            kind: "MATCH_STARTED",
            sequence: 0,
          });

          if (msg.payload.moves) {
            const sortedMoves = [...msg.payload.moves].sort(
              (a, b) => a.sequenceNumber - b.sequenceNumber,
            );
            for (const move of sortedMoves) {
              activities.push({
                id: `move-${move.sequenceNumber}`,
                kind: "MOVE",
                sequence: move.sequenceNumber,
                player: move.playedBy,
                notation: move.notation,
              });
            }
          }

          if (status === "FINISHED" && msg.payload.result) {
            const lastPly = msg.payload.moves ? msg.payload.moves.length : 0;
            activities.push({
              id: "match-finished",
              kind: "MATCH_FINISHED",
              sequence: lastPly + 1,
              result: msg.payload.result,
            });
          }
        }

        set({
          matchStatus: status,
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.sideToMove,
          moveCount: msg.payload.moves ? msg.payload.moves.length : 0,
          result: msg.payload.result || undefined,
          activities,
        });
        break;
      }
      case "MOVE_PLAYED":
        set((state) => {
          const moveId = `move-${msg.payload.ply}`;
          const newMove: MatchActivityItem = {
            id: moveId,
            kind: "MOVE",
            sequence: msg.payload.ply,
            player: msg.payload.player,
            notation: msg.payload.notation,
            capture: msg.payload.capture,
            check: msg.payload.check,
            checkmate: msg.payload.checkmate,
            promotion: msg.payload.promotion,
          };

          const filtered = state.activities.filter((act) => act.id !== moveId);
          const updatedActivities = [...filtered, newMove].sort(
            (a, b) => a.sequence - b.sequence,
          );

          return {
            boardFen: msg.payload.fen,
            activeTurn: msg.payload.player === "WHITE" ? "BLACK" : "WHITE",
            moveCount: msg.payload.ply,
            activities: updatedActivities,
          };
        });
        break;
      case "MATCH_FINISHED":
        set((state) => {
          const finishedId = "match-finished";
          const lastPly = msg.payload.totalPlies;
          const finishedItem: MatchActivityItem = {
            id: finishedId,
            kind: "MATCH_FINISHED",
            sequence: lastPly + 1,
            result: msg.payload.result,
          };

          const filtered = state.activities.filter(
            (act) => act.id !== finishedId,
          );
          const updatedActivities = [...filtered, finishedItem].sort(
            (a, b) => a.sequence - b.sequence,
          );

          return {
            matchStatus: "FINISHED",
            boardFen: msg.payload.fen,
            result: msg.payload.result,
            activities: updatedActivities,
          };
        });
        break;
    }
  },
}));
