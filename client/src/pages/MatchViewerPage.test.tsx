import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { MatchViewerPage } from "./MatchViewerPage";

vi.mock("@/hooks/useMatchStream", () => ({
  useMatchStream: vi.fn(),
}));

vi.mock("react-chessboard", () => ({
  Chessboard: () => <div aria-label="Chess board" />,
}));

describe("MatchViewerPage", () => {
  beforeEach(() => {
    useMatchViewerStore.setState({
      matchStatus: "IDLE",
      connectionStatus: "CONNECTING",
      activeTurn: "WHITE",
      moveCount: 0,
      activities: [],
      result: undefined,
      error: undefined,
    });
  });

  afterEach(cleanup);

  it("keeps the public viewer read-only while retaining live match context", () => {
    render(<MatchViewerPage />);

    expect(
      screen.getByRole("heading", { name: "Live Match Viewer" }),
    ).toBeVisible();
    expect(screen.getByLabelText("Chess board")).toBeVisible();
    expect(screen.getByText("No match is currently running.")).toBeVisible();
    expect(screen.getByText("Connecting to live match...")).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "Match Activity" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /start match/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /stop match/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /resume match/i }),
    ).not.toBeInTheDocument();
  });
});
