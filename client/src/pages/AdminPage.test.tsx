import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { AdminPage } from "./AdminPage";

vi.mock("@/hooks/useMatchStream", () => ({ useMatchStream: vi.fn() }));

describe("AdminPage", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useMatchViewerStore.setState({
      matchStatus: "IDLE",
      startAvailability: {
        allowed: true,
        blockedBy: null,
        retryAfterSeconds: 0,
        dailyStartsAccepted: 0,
        dailyStartLimit: 12,
      },
    });
  });
  afterEach(cleanup);

  it("starts locked and can lock the same-tab restored session", () => {
    const { unmount } = render(<AdminPage />);
    expect(screen.getByLabelText("Owner control token")).toBeVisible();
    unmount();

    sessionStorage.setItem("ownerControlToken", "owner-token");
    render(<AdminPage />);
    fireEvent.click(screen.getByRole("button", { name: "Lock Controls" }));

    expect(screen.getByLabelText("Owner control token")).toBeVisible();
    expect(sessionStorage.getItem("ownerControlToken")).toBeNull();
  });
});
