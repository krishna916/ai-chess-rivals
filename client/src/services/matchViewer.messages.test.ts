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
      parseMatchMessage({
        type: "MATCH_STARTED",
        payload: {},
      }),
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
          matchId: "match-1",
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
          dialogue: [],
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
          matchId: "match-1",
          sideToMove: "BLACK",
          fen: "after-e4",
          moves: [{ sequenceNumber: 1, playedBy: "WHITE" }],
          dialogue: [],
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
          matchId: "match-1",
          sideToMove: "WHITE",
          fen: "start",
          moves: [],
          dialogue: [],
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

  it("accepts a valid persisted dialogue message", () => {
    expect(
      parseMatchMessage({ type: "DIALOGUE_PLAYED", payload: dialogue() }),
    ).toMatchObject({
      type: "DIALOGUE_PLAYED",
      payload: { id: 1, matchId: "match-1", text: "hello" },
    });
  });

  it.each(["matchId", "id", "triggerPly", "text", "createdAt"])(
    "rejects dialogue without %s",
    (field) => {
      const payload = dialogue();
      delete (payload as Record<string, unknown>)[field];
      expect(
        parseMatchMessage({ type: "DIALOGUE_PLAYED", payload }),
      ).toBeNull();
    },
  );

  it("requires dialogue history and match identity in MATCH_STATE", () => {
    const payload = {
      matchId: "match-1",
      sideToMove: "WHITE",
      fen: "start",
      moves: [],
      status: "IN_PROGRESS",
      result: null,
      running: true,
      startAvailability,
      dialogue: [],
    };
    expect(parseMatchMessage({ type: "MATCH_STATE", payload })).not.toBeNull();
    expect(
      parseMatchMessage({
        type: "MATCH_STATE",
        payload: { ...payload, matchId: undefined },
      }),
    ).toBeNull();
    expect(
      parseMatchMessage({
        type: "MATCH_STATE",
        payload: { ...payload, dialogue: undefined },
      }),
    ).toBeNull();
  });
});

function dialogue() {
  return {
    id: 1,
    matchId: "match-1",
    triggerType: "MOVE",
    triggerPly: 1,
    personalityKey: "blaze",
    personalityDisplayName: "Blaze",
    text: "hello",
    emotion: "CONFIDENT",
    reactionType: "MOVE_REACTION",
    source: "DETERMINISTIC_FALLBACK",
    createdAt: "2026-08-16T00:00:00Z",
  };
}
