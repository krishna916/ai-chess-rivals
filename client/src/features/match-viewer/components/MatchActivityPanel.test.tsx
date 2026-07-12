import { render, screen, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { MatchActivityPanel } from "./MatchActivityPanel";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import type { MatchActivityItem } from "@/types/match";

// Mock scrollIntoView
const scrollIntoViewMock = vi.fn();
window.HTMLElement.prototype.scrollIntoView = scrollIntoViewMock;

describe("MatchActivityPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useMatchViewerStore.setState({
      activities: [],
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when there are no activities", () => {
    render(<MatchActivityPanel />);
    expect(screen.getByText("No activity yet")).toBeInTheDocument();
    expect(screen.getByText("Waiting for the match to start...")).toBeInTheDocument();
  });

  it("renders activities in chronological order and formats results", () => {
    const activities: MatchActivityItem[] = [
      { id: "match-started", kind: "MATCH_STARTED", sequence: 0 },
      { id: "move-1", kind: "MOVE", sequence: 1, player: "WHITE", notation: "e4", capture: false, check: false },
      { id: "move-2", kind: "MOVE", sequence: 2, player: "BLACK", notation: "e5", capture: false, check: true },
      { id: "move-3", kind: "MOVE", sequence: 3, player: "WHITE", notation: "Nf3", capture: true, check: false },
      { id: "match-finished", kind: "MATCH_FINISHED", sequence: 4, result: "WHITE_WINS" },
    ];
    useMatchViewerStore.setState({ activities });

    render(<MatchActivityPanel />);

    expect(screen.getByText("Match Activity")).toBeInTheDocument();
    expect(screen.getByText("5 events")).toBeInTheDocument();

    expect(screen.getByText("Match started")).toBeInTheDocument();
    expect(screen.getByText("e4")).toBeInTheDocument();
    expect(screen.getByText("e5")).toBeInTheDocument();
    expect(screen.getByText("Nf3")).toBeInTheDocument();
    expect(screen.getByText("Match finished — White wins")).toBeInTheDocument();

    expect(screen.getByText("Check")).toBeInTheDocument();
    expect(screen.getByText("Capture")).toBeInTheDocument();
  });

  it("triggers scrollIntoView when activities are added", () => {
    const { rerender } = render(<MatchActivityPanel />);
    expect(scrollIntoViewMock).not.toHaveBeenCalled();

    useMatchViewerStore.setState({
      activities: [{ id: "match-started", kind: "MATCH_STARTED", sequence: 0 }],
    });
    rerender(<MatchActivityPanel />);

    expect(scrollIntoViewMock).toHaveBeenCalled();
  });
});
