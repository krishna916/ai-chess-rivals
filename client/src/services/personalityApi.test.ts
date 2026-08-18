import { beforeEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => ({ get })),
  },
}));

import { personalityApi } from "./personalityApi";

describe("personalityApi", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads the selectable roster", async () => {
    const roster = [
      {
        key: "blaze",
        displayName: "Blaze",
        description: "Explosive confidence.",
        avatarRef: null,
      },
      {
        key: "vesper",
        displayName: "Vesper",
        description: "Cold precision.",
        avatarRef: null,
      },
    ];
    get.mockResolvedValue({ data: roster });

    await expect(personalityApi.listSelectable()).resolves.toEqual(roster);
    expect(get).toHaveBeenCalledWith("/personalities");
  });
});
