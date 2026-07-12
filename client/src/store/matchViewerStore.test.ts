import { describe, it, expect, beforeEach } from "vitest";
import { useMatchViewerStore } from "./matchViewerStore";

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
    });
  });

  it("should process MATCH_STARTED message", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STARTED",
      payload: { fen: "newfen", sideToMove: "BLACK" },
    });
    const state = useMatchViewerStore.getState();
    expect(state.matchStatus).toBe("IN_PROGRESS");
    expect(state.boardFen).toBe("newfen");
    expect(state.activeTurn).toBe("BLACK");
  });

  it("should clear a stale error when connected", () => {
    useMatchViewerStore.setState({ error: "Connection error occurred." });

    useMatchViewerStore.getState().setConnectionStatus("CONNECTED");

    expect(useMatchViewerStore.getState()).toMatchObject({
      connectionStatus: "CONNECTED",
      error: undefined,
    });
  });

  it("should process MATCH_STARTED message, clearing previous activities and creating a start item", () => {
    useMatchViewerStore.setState({
      activities: [{ id: "stale-move", kind: "MOVE", sequence: 1 }],
    });

    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STARTED",
      payload: { fen: "newfen", sideToMove: "BLACK" },
    });

    const state = useMatchViewerStore.getState();
    expect(state.activities).toEqual([
      { id: "match-started", kind: "MATCH_STARTED", sequence: 0 },
    ]);
  });

  it("should reconstruct start, ordered moves, and final result from MATCH_STATE", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "FINISHED",
        fen: "finalfen",
        sideToMove: "WHITE",
        result: "WHITE_WINS",
        running: false,
        moves: [
          {
            sequenceNumber: 2,
            player: "BLACK",
            notation: "e7e5",
            fenAfterMove: "fen2",
          },
          {
            sequenceNumber: 1,
            player: "WHITE",
            notation: "e2e4",
            fenAfterMove: "fen1",
          },
        ],
      },
    });

    const state = useMatchViewerStore.getState();
    expect(state.activities).toEqual([
      { id: "match-started", kind: "MATCH_STARTED", sequence: 0 },
      {
        id: "move-1",
        kind: "MOVE",
        sequence: 1,
        player: "WHITE",
        notation: "e2e4",
        capture: false,
        check: false,
        checkmate: false,
        promotion: false,
      },
      {
        id: "move-2",
        kind: "MOVE",
        sequence: 2,
        player: "BLACK",
        notation: "e7e5",
        capture: false,
        check: false,
        checkmate: false,
        promotion: false,
      },
      {
        id: "match-finished",
        kind: "MATCH_FINISHED",
        sequence: 3,
        result: "WHITE_WINS",
      },
    ]);
  });

  it("should not duplicate items when reprocessing the same MATCH_STATE", () => {
    const msg = {
      type: "MATCH_STATE" as const,
      payload: {
        status: "IN_PROGRESS" as const,
        fen: "fen",
        sideToMove: "WHITE" as const,
        result: null,
        running: true,
        moves: [
          {
            sequenceNumber: 1,
            player: "WHITE" as const,
            notation: "e2e4",
            fenAfterMove: "fen1",
          },
        ],
      },
    };

    useMatchViewerStore.getState().processMessage(msg);
    useMatchViewerStore.getState().processMessage(msg);

    const state = useMatchViewerStore.getState();
    expect(state.activities).toHaveLength(2); // start + 1 move
  });

  it("should append a move with all event flags on MOVE_PLAYED", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MOVE_PLAYED",
      payload: {
        ply: 1,
        player: "WHITE",
        notation: "e4",
        fen: "fen1",
        capture: true,
        check: true,
        checkmate: false,
        promotion: false,
      },
    });

    const state = useMatchViewerStore.getState();
    expect(state.activities).toContainEqual({
      id: "move-1",
      kind: "MOVE",
      sequence: 1,
      player: "WHITE",
      notation: "e4",
      capture: true,
      check: true,
      checkmate: false,
      promotion: false,
    });
  });

  it("should replace/deduplicate rather than appending twice on duplicate MOVE_PLAYED", () => {
    const moveMsg = {
      type: "MOVE_PLAYED" as const,
      payload: {
        ply: 1,
        player: "WHITE" as const,
        notation: "e4",
        fen: "fen1",
        capture: false,
        check: false,
        checkmate: false,
        promotion: false,
      },
    };

    useMatchViewerStore.getState().processMessage(moveMsg);
    useMatchViewerStore.getState().processMessage(moveMsg);

    const state = useMatchViewerStore.getState();
    expect(state.activities.filter((a) => a.id === "move-1")).toHaveLength(1);
  });

  it("should create exactly one final activity on MATCH_FINISHED", () => {
    useMatchViewerStore.setState({ moveCount: 5 });

    const finishMsg = {
      type: "MATCH_FINISHED" as const,
      payload: { result: "DRAW", fen: "fen", totalPlies: 5 },
    };

    useMatchViewerStore.getState().processMessage(finishMsg);
    useMatchViewerStore.getState().processMessage(finishMsg);

    const state = useMatchViewerStore.getState();
    expect(
      state.activities.filter((a) => a.id === "match-finished"),
    ).toHaveLength(1);
    expect(state.activities).toContainEqual({
      id: "match-finished",
      kind: "MATCH_FINISHED",
      sequence: 6,
      result: "DRAW",
    });
  });

  it("should clear activities on NO_MATCH", () => {
    useMatchViewerStore.setState({
      activities: [{ id: "match-started", kind: "MATCH_STARTED", sequence: 0 }],
    });

    useMatchViewerStore.getState().processMessage({
      type: "NO_MATCH",
      payload: {},
    });

    const state = useMatchViewerStore.getState();
    expect(state.activities).toEqual([]);
  });

  it("hydrates the actual backend snapshot contract as a stopped match", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "IN_PROGRESS",
        fen: "after-e4",
        sideToMove: "BLACK",
        result: null,
        running: false,
        moves: [
          {
            sequenceNumber: 1,
            player: "WHITE",
            notation: "e2e4",
            fenAfterMove: "after-e4",
          },
        ],
      },
    });

    expect(useMatchViewerStore.getState()).toMatchObject({
      boardFen: "after-e4",
      moveCount: 1,
      activeTurn: "BLACK",
      matchStatus: "STOPPED",
      result: undefined,
    });
    expect(useMatchViewerStore.getState().activities[1]).toMatchObject({
      player: "WHITE",
      notation: "e2e4",
    });
  });

  it("resets all stale match state on NO_MATCH", () => {
    useMatchViewerStore.setState({
      activeTurn: "BLACK",
      result: "DRAW",
      error: "stale",
    });

    useMatchViewerStore.getState().processMessage({
      type: "NO_MATCH",
      payload: {},
    });

    expect(useMatchViewerStore.getState()).toMatchObject({
      activeTurn: "WHITE",
      result: undefined,
      error: undefined,
    });
  });

  it("reconstructs checkmate metadata from snapshot moves", () => {
    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "FINISHED",
        fen: "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
        sideToMove: "WHITE",
        result: "BLACK_WINS",
        running: false,
        moves: [
          {
            sequenceNumber: 1,
            player: "WHITE",
            notation: "f2f3",
            fenAfterMove:
              "rnbqkbnr/pppppppp/8/8/8/5P2/PPPPP1PP/RNBQKBNR b KQkq - 0 1",
          },
          {
            sequenceNumber: 2,
            player: "BLACK",
            notation: "e7e5",
            fenAfterMove:
              "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
          },
          {
            sequenceNumber: 3,
            player: "WHITE",
            notation: "g2g4",
            fenAfterMove:
              "rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2",
          },
          {
            sequenceNumber: 4,
            player: "BLACK",
            notation: "d8h4",
            fenAfterMove:
              "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
          },
        ],
      },
    });

    expect(
      useMatchViewerStore
        .getState()
        .activities.find((item) => item.id === "move-4"),
    ).toMatchObject({ check: true, checkmate: true });
  });

  it("reconstructs capture and promotion metadata from snapshot moves", () => {
    const moves = [
      ["WHITE", "a2a4"],
      ["BLACK", "h7h5"],
      ["WHITE", "a4a5"],
      ["BLACK", "h5h4"],
      ["WHITE", "a5a6"],
      ["BLACK", "h4h3"],
      ["WHITE", "a6b7"],
      ["BLACK", "h3g2"],
      ["WHITE", "b7a8q"],
    ] as const;

    useMatchViewerStore.getState().processMessage({
      type: "MATCH_STATE",
      payload: {
        status: "IN_PROGRESS",
        fen: "promotion-position",
        sideToMove: "BLACK",
        result: null,
        running: true,
        moves: moves.map(([player, notation], index) => ({
          sequenceNumber: index + 1,
          player,
          notation,
          fenAfterMove: `fen-${index + 1}`,
        })),
      },
    });

    expect(
      useMatchViewerStore
        .getState()
        .activities.find((item) => item.id === "move-9"),
    ).toMatchObject({ capture: true, promotion: true });
  });
});
