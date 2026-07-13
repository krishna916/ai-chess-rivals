import { beforeEach, describe, expect, it } from "vitest";
import type {
  MovePlayedMessage,
  MoveResponse,
  StartAvailability,
} from "../types/match";
import { useMatchViewerStore } from "./matchViewerStore";

const startAvailability: StartAvailability = {
  allowed: true,
  blockedBy: null,
  retryAfterSeconds: 0,
  dailyStartsAccepted: 2,
  dailyStartLimit: 12,
};

function snapshotMove(overrides: Partial<MoveResponse> = {}): MoveResponse {
  return {
    sequenceNumber: 1,
    player: "WHITE",
    notation: "e2e4",
    fenAfterMove: "after-e4",
    movingPiece: "PAWN",
    movingPieceColor: "WHITE",
    sourceSquare: "e2",
    destinationSquare: "e4",
    capturedPiece: null,
    capturedPieceColor: null,
    promotedPiece: null,
    castlingSide: null,
    capture: false,
    check: false,
    checkmate: false,
    promotion: false,
    ...overrides,
  };
}

function liveMove(
  overrides: Partial<MovePlayedMessage["payload"]> = {},
): MovePlayedMessage["payload"] {
  return {
    ply: 1,
    player: "WHITE",
    notation: "e2e4",
    fen: "after-e4",
    movingPiece: "PAWN",
    movingPieceColor: "WHITE",
    sourceSquare: "e2",
    destinationSquare: "e4",
    capturedPiece: null,
    capturedPieceColor: null,
    promotedPiece: null,
    castlingSide: null,
    capture: false,
    check: false,
    checkmate: false,
    promotion: false,
    ...overrides,
  };
}

describe("matchViewerStore", () => {
  beforeEach(() => {
    useMatchViewerStore.setState({
      boardFen: "start",
      matchStatus: "IDLE",
      connectionStatus: "DISCONNECTED",
      activeTurn: "WHITE",
      moveCount: 0,
      activities: [],
      result: undefined,
      error: undefined,
      startAvailability: undefined,
    });
  });

  it("starts a match and clears stale activity", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STARTED",
      payload: { fen: "newfen", sideToMove: "BLACK" },
    });

    expect(useMatchViewerStore.getState()).toMatchObject({
      matchStatus: "IN_PROGRESS",
      boardFen: "newfen",
      activeTurn: "BLACK",
      moveCount: 0,
      activities: [{ id: "match-started", kind: "MATCH_STARTED", sequence: 0 }],
    });
  });

  it("clears a stale error when connected", () => {
    useMatchViewerStore.setState({ error: "Connection error occurred." });

    useMatchViewerStore.getState().setConnectionStatus("CONNECTED");

    expect(useMatchViewerStore.getState()).toMatchObject({
      connectionStatus: "CONNECTED",
      error: undefined,
    });
  });

  it("hydrates ordered structured moves and a final result", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "FINISHED",
        fen: "finalfen",
        sideToMove: "WHITE",
        result: "WHITE_WINS",
        running: false,
        startAvailability,
        moves: [
          snapshotMove({
            sequenceNumber: 2,
            player: "BLACK",
            notation: "e7e5",
            fenAfterMove: "fen2",
            movingPieceColor: "BLACK",
            sourceSquare: "e7",
            destinationSquare: "e5",
          }),
          snapshotMove({ fenAfterMove: "fen1" }),
        ],
      },
    });

    const state = useMatchViewerStore.getState();
    expect(state.activities.map((activity) => activity.id)).toEqual([
      "match-started",
      "move-1",
      "move-2",
      "match-finished",
    ]);
    expect(state.activities[1]).toMatchObject({
      movingPiece: "PAWN",
      sourceSquare: "e2",
      destinationSquare: "e4",
      isNew: false,
    });
    expect(state.activities[3]).toEqual({
      id: "match-finished",
      kind: "MATCH_FINISHED",
      sequence: 3,
      result: "WHITE_WINS",
    });
  });

  it("maps a snapshot capture directly and keeps it historical", () => {
    const message = {
      type: "MATCH_STATE" as const,
      payload: {
        status: "IN_PROGRESS" as const,
        fen: "after-capture",
        sideToMove: "BLACK" as const,
        result: null,
        running: true,
        startAvailability,
        moves: [
          snapshotMove({
            notation: "c4d5",
            fenAfterMove: "after-capture",
            movingPiece: "KNIGHT",
            sourceSquare: "c4",
            destinationSquare: "d5",
            capturedPiece: "PAWN",
            capturedPieceColor: "BLACK",
            capture: true,
          }),
        ],
      },
    };

    useMatchViewerStore.getState().processMessage(message);
    useMatchViewerStore.getState().processMessage(message);

    expect(useMatchViewerStore.getState().activities).toHaveLength(2);
    expect(useMatchViewerStore.getState().activities[1]).toMatchObject({
      movingPiece: "KNIGHT",
      capturedPiece: "PAWN",
      capture: true,
      isNew: false,
    });
  });

  it("maps a live capture directly and marks it newly arrived", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MOVE_PLAYED",
      payload: liveMove({
        notation: "c4d5",
        fen: "after-capture",
        movingPiece: "KNIGHT",
        sourceSquare: "c4",
        destinationSquare: "d5",
        capturedPiece: "PAWN",
        capturedPieceColor: "BLACK",
        capture: true,
        check: true,
      }),
    });

    expect(useMatchViewerStore.getState().activities[0]).toMatchObject({
      movingPiece: "KNIGHT",
      capturedPiece: "PAWN",
      capture: true,
      check: true,
      isNew: true,
    });
  });

  it("deduplicates repeated live moves", () => {
    const message = {
      type: "MOVE_PLAYED" as const,
      payload: liveMove(),
    };

    useMatchViewerStore.getState().processMessage(message);
    useMatchViewerStore.getState().processMessage(message);

    expect(
      useMatchViewerStore
        .getState()
        .activities.filter((activity) => activity.id === "move-1"),
    ).toHaveLength(1);
  });

  it("hydrates server flags without chess.js reconstruction", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "FINISHED",
        fen: "mate",
        sideToMove: "WHITE",
        result: "BLACK_WINS",
        running: false,
        startAvailability,
        moves: [
          snapshotMove({
            sequenceNumber: 4,
            player: "BLACK",
            notation: "d8h4",
            fenAfterMove: "mate",
            movingPiece: "QUEEN",
            movingPieceColor: "BLACK",
            sourceSquare: "d8",
            destinationSquare: "h4",
            check: true,
            checkmate: true,
          }),
        ],
      },
    });

    expect(
      useMatchViewerStore
        .getState()
        .activities.find((activity) => activity.id === "move-4"),
    ).toMatchObject({ check: true, checkmate: true, isNew: false });
  });

  it("marks a running snapshot as stopped when execution is not running", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "IN_PROGRESS",
        fen: "after-e4",
        sideToMove: "BLACK",
        result: null,
        running: false,
        startAvailability,
        moves: [snapshotMove()],
      },
    });

    expect(useMatchViewerStore.getState()).toMatchObject({
      boardFen: "after-e4",
      moveCount: 1,
      activeTurn: "BLACK",
      matchStatus: "STOPPED",
      startAvailability,
    });
  });

  it("creates exactly one final activity", () => {
    const message = {
      type: "MATCH_FINISHED" as const,
      payload: { result: "DRAW", fen: "fen", totalPlies: 5 },
    };

    useMatchViewerStore.getState().processMessage(message);
    useMatchViewerStore.getState().processMessage(message);

    expect(
      useMatchViewerStore
        .getState()
        .activities.filter((activity) => activity.id === "match-finished"),
    ).toHaveLength(1);
  });

  it("applies the server stopped event", () => {
    useMatchViewerStore.setState({ matchStatus: "IN_PROGRESS", moveCount: 1 });

    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STOPPED",
      payload: { sideToMove: "BLACK", fen: "after-e4", totalPlies: 1 },
    });

    expect(useMatchViewerStore.getState()).toMatchObject({
      matchStatus: "STOPPED",
      boardFen: "after-e4",
      activeTurn: "BLACK",
      moveCount: 1,
    });
  });

  it("resets all stale state on NO_MATCH", () => {
    useMatchViewerStore.setState({
      activeTurn: "BLACK",
      result: "DRAW",
      error: "stale",
      startAvailability,
    });

    useMatchViewerStore.getState().processMessage({
      type: "NO_MATCH",
      payload: {},
    });

    expect(useMatchViewerStore.getState()).toMatchObject({
      matchStatus: "IDLE",
      activeTurn: "WHITE",
      result: undefined,
      error: undefined,
      activities: [],
      startAvailability: undefined,
    });
  });
});
