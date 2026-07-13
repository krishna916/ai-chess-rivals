import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearOwnerToken, getOwnerToken, setOwnerToken } from "./ownerToken";

describe("ownerToken", () => {
  const localStorageSpy = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
  };

  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllMocks();
    vi.stubGlobal("localStorage", localStorageSpy);
  });

  it("trims and restores the token only from session storage", () => {
    setOwnerToken("  owner-token  ");

    expect(getOwnerToken()).toBe("owner-token");
    expect(sessionStorage.getItem("ownerControlToken")).toBe("owner-token");
    expect(localStorageSpy.setItem).not.toHaveBeenCalled();
  });

  it("clears the token from the current browser session", () => {
    sessionStorage.setItem("ownerControlToken", "owner-token");

    clearOwnerToken();

    expect(getOwnerToken()).toBeNull();
  });
});
