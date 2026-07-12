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
          { sequenceNumber: 2, playedBy: "BLACK", notation: "e5", fen: "fen2" },
          { sequenceNumber: 1, playedBy: "WHITE", notation: "e4", fen: "fen1" },
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
        notation: "e4",
      },
      {
        id: "move-2",
        kind: "MOVE",
        sequence: 2,
        player: "BLACK",
        notation: "e5",
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
            playedBy: "WHITE" as const,
            notation: "e4",
            fen: "fen1",
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
});
