import { describe, it, expect } from "vitest";
import { parseMatchMessage } from "./matchViewer.messages";

describe("matchViewer.messages", () => {
  it("should safely parse valid messages", () => {
    const msg = { type: "NO_MATCH" };
    expect(parseMatchMessage(msg)).toEqual(msg);
  });

  it("should return null for invalid messages", () => {
    expect(parseMatchMessage(null)).toBeNull();
    expect(parseMatchMessage({ type: "UNKNOWN" })).toBeNull();
    expect(
      parseMatchMessage({ type: "MATCH_STARTED", payload: {} }),
    ).toBeNull();
    expect(
      parseMatchMessage({
        type: "MOVE_PLAYED",
        payload: { fen: 42 },
      }),
    ).toBeNull();
  });

  it("accepts the backend MATCH_STATE move contract", () => {
    expect(
      parseMatchMessage({
        type: "MATCH_STATE",
        payload: {
          sideToMove: "BLACK",
          fen: "after-e4",
          moves: [
            {
              sequenceNumber: 1,
              player: "WHITE",
              notation: "e2e4",
              fenAfterMove: "after-e4",
            },
          ],
          status: "IN_PROGRESS",
          result: null,
          running: false,
        },
      }),
    ).toMatchObject({
      payload: {
        moves: [{ player: "WHITE", fenAfterMove: "after-e4" }],
      },
    });
  });

  it("rejects malformed moves inside MATCH_STATE", () => {
    expect(
      parseMatchMessage({
        type: "MATCH_STATE",
        payload: {
          sideToMove: "BLACK",
          fen: "after-e4",
          moves: [{ sequenceNumber: 1, playedBy: "WHITE" }],
          status: "IN_PROGRESS",
          result: null,
          running: true,
        },
      }),
    ).toBeNull();
  });
});
