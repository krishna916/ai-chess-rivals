import { create } from "zustand";
import type {
  MatchStatus,
  ConnectionStatus,
  MatchStreamMessage,
  MatchActivityItem,
  StartAvailability,
  MatchPersonality,
} from "../types/match";
import {
  buildSnapshotActivities,
  mergeMatchActivity,
  toDialogueActivity,
  toLiveMoveActivity,
} from "@/features/match-viewer/lib/matchActivity";

const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

interface MatchViewerState {
  boardFen: string;
  matchStatus: MatchStatus;
  connectionStatus: ConnectionStatus;
  activeTurn: "WHITE" | "BLACK";
  moveCount: number;
  activities: MatchActivityItem[];
  currentMatchId?: string;
  whitePersonality?: MatchPersonality;
  blackPersonality?: MatchPersonality;
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
  currentMatchId: undefined,

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
          currentMatchId: undefined,
          whitePersonality: undefined,
          blackPersonality: undefined,
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
          currentMatchId: msg.payload.matchId,
          whitePersonality: msg.payload.whitePersonality,
          blackPersonality: msg.payload.blackPersonality,
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
        const activities = buildSnapshotActivities(msg.payload);

        set({
          matchStatus: status,
          boardFen: msg.payload.fen,
          activeTurn: msg.payload.sideToMove,
          moveCount: lastPly,
          result: msg.payload.result || undefined,
          activities,
          currentMatchId: msg.payload.matchId,
          whitePersonality: msg.payload.whitePersonality,
          blackPersonality: msg.payload.blackPersonality,
          startAvailability: msg.payload.startAvailability,
        });
        break;
      }
      case "MOVE_PLAYED":
        set((state) => {
          return {
            boardFen: msg.payload.fen,
            activeTurn: msg.payload.player === "WHITE" ? "BLACK" : "WHITE",
            moveCount: msg.payload.ply,
            activities: mergeMatchActivity(
              state.activities,
              toLiveMoveActivity(msg.payload),
            ),
          };
        });
        break;
      case "DIALOGUE_PLAYED":
        set((state) => {
          if (
            state.currentMatchId === undefined ||
            msg.payload.matchId !== state.currentMatchId
          ) {
            return state;
          }

          return {
            activities: mergeMatchActivity(
              state.activities,
              toDialogueActivity(
                msg.payload,
                state.whitePersonality,
                state.blackPersonality,
              ),
            ),
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
          return {
            matchStatus: "FINISHED",
            boardFen: msg.payload.fen,
            result: msg.payload.result,
            activities: mergeMatchActivity(state.activities, {
              id: "match-finished",
              kind: "MATCH_FINISHED",
              sequence: msg.payload.totalPlies + 1,
              result: msg.payload.result,
            }),
          };
        });
        break;
    }
  },
}));
