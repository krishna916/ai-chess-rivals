import { describe, expect, it } from "vitest";
import type { PersonalityRosterItem } from "@/types/match";
import { randomizeRivalry } from "./rivalrySelection";

const roster: PersonalityRosterItem[] = [
  { key: "a", displayName: "A", description: "A", avatarRef: null },
  { key: "b", displayName: "B", description: "B", avatarRef: null },
  { key: "c", displayName: "C", description: "C", avatarRef: null },
  { key: "d", displayName: "D", description: "D", avatarRef: null },
];

describe("randomizeRivalry", () => {
  it("returns two distinct roster entries without retrying", () => {
    const randomValues = [0, 0];
    let index = 0;

    const result = randomizeRivalry(roster, () => randomValues[index++]);

    expect(result).toEqual({
      whitePersonalityKey: "a",
      blackPersonalityKey: "b",
    });
    expect(index).toBe(2);
  });

  it("can choose the last item for both compressed index paths", () => {
    const randomValues = [0.99, 0.99];
    let index = 0;

    const result = randomizeRivalry(roster, () => randomValues[index++]);

    expect(result.whitePersonalityKey).toBe("d");
    expect(result.blackPersonalityKey).toBe("c");
    expect(result.whitePersonalityKey).not.toBe(result.blackPersonalityKey);
  });

  it("rejects a roster with fewer than two personalities", () => {
    expect(() => randomizeRivalry(roster.slice(0, 1), () => 0)).toThrow(
      "At least two personalities are required",
    );
  });
});
