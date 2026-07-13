import { describe, it, expect } from "vitest";
import { parseMatchMessage } from "./matchViewer.messages";

const startAvailability = {
  allowed: true,
  blockedBy: null,
  retryAfterSeconds: 0,
  dailyStartsAccepted: 2,
  dailyStartLimit: 12,
} as const;

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
          startAvailability,
        },
      }),
    ).toMatchObject({
      payload: {
        moves: [{ player: "WHITE", fenAfterMove: "after-e4" }],
        startAvailability,
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
          startAvailability,
        },
      }),
    ).toBeNull();
  });

  it("rejects malformed start availability", () => {
    expect(
      parseMatchMessage({
        type: "MATCH_STATE",
        payload: {
          sideToMove: "WHITE",
          fen: "start",
          moves: [],
          status: "IN_PROGRESS",
          result: null,
          running: true,
          startAvailability: { allowed: "yes" },
        },
      }),
    ).toBeNull();
  });

  it("accepts an authoritative MATCH_STOPPED message", () => {
    expect(
      parseMatchMessage({
        type: "MATCH_STOPPED",
        payload: { sideToMove: "BLACK", fen: "after-e4", totalPlies: 1 },
      }),
    ).toMatchObject({ type: "MATCH_STOPPED", payload: { totalPlies: 1 } });
  });
});
