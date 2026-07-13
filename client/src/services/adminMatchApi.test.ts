import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => ({ get, post })),
  },
}));

import { adminMatchApi } from "./adminMatchApi";
import { matchApi } from "./matchApi";

describe("adminMatchApi", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    post.mockResolvedValue({ data: { running: true } });
    get.mockResolvedValue({ data: { running: false } });
  });

  it("attaches the bearer token only to Start", async () => {
    await adminMatchApi.startMatch("owner-token");

    expect(post).toHaveBeenCalledWith("/match/start", undefined, {
      headers: { Authorization: "Bearer owner-token" },
    });
  });

  it("attaches the bearer token only to Stop", async () => {
    await adminMatchApi.stopMatch("owner-token");

    expect(post).toHaveBeenCalledWith("/match/stop", undefined, {
      headers: { Authorization: "Bearer owner-token" },
    });
  });

  it("keeps the public match request free of authorization", async () => {
    await matchApi.getCurrentMatch();

    expect(get).toHaveBeenCalledWith("/match");
    expect(post).not.toHaveBeenCalled();
  });
});
