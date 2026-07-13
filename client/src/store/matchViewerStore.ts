import { create } from "zustand";
import { Chess } from "chess.js";
import type {
  MatchStatus,
  ConnectionStatus,
  MatchStreamMessage,
  MatchActivityItem,
  StartAvailability,
} from "../types/match";

const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

function reconstructMoveActivities(
  moves: Extract<
    MatchStreamMessage,
    { type: "MATCH_STATE" }
  >["payload"]["moves"],
): MatchActivityItem[] {
  const chess = new Chess();

  return [...moves]
    .sort((a, b) => a.sequenceNumber - b.sequenceNumber)
    .map((move) => {
      const activity: MatchActivityItem = {
        id: `move-${move.sequenceNumber}`,
        kind: "MOVE",
        sequence: move.sequenceNumber,
        player: move.player,
        notation: move.notation,
      };

      try {
        const applied = chess.move({
          from: move.notation.slice(0, 2),
          to: move.notation.slice(2, 4),
          promotion: move.notation[4],
        });
        return {
          ...activity,
          capture: applied.isCapture(),
          check: chess.inCheck(),
          checkmate: chess.isCheckmate(),
          promotion: applied.isPromotion(),
        };
      } catch {
        try {
          chess.load(move.fenAfterMove);
        } catch {
          // Primary snapshot data remains usable even if annotation reconstruction fails.
        }
        return activity;
      }
    });
}

interface MatchViewerState {
  boardFen: string;
  matchStatus: MatchStatus;
  connectionStatus: ConnectionStatus;
  activeTurn: "WHITE" | "BLACK";
  moveCount: number;
  activities: MatchActivityItem[];
  result?: string;
  error?: string;
  startAvailability?: StartAvailability;
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
          activeTurn: "WHITE",
          result: undefined,
          moveCount: 0,
          activities: [],
          error: undefined,
          startAvailability: undefined,
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
        const lastPly = msg.payload.moves.reduce(
          (highest, move) => Math.max(highest, move.sequenceNumber),
          0,
        );
        const status =
          msg.payload.status === "NOT_STARTED"
            ? "IDLE"
            : msg.payload.status === "IN_PROGRESS" && !msg.payload.running
              ? "STOPPED"
              : msg.payload.status;
        const activities: MatchActivityItem[] = [];

        if (status !== "IDLE") {
          activities.push({
            id: "match-started",
            kind: "MATCH_STARTED",
            sequence: 0,
          });

          activities.push(...reconstructMoveActivities(msg.payload.moves));

          if (status === "FINISHED" && msg.payload.result) {
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
          moveCount: lastPly,
          result: msg.payload.result || undefined,
          activities,
          startAvailability: msg.payload.startAvailability,
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
      case "MATCH_STOPPED":
        set({
          matchStatus: "STOPPED",
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.sideToMove,
          moveCount: msg.payload.totalPlies,
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
