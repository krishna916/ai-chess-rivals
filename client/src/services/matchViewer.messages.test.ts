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
  });
});
