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
});
