/// <reference types="@testing-library/jest-dom" />
import { cleanup, render, screen, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { MatchActivityPanel } from "./MatchActivityPanel";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import type { MatchActivityItem } from "@/types/match";

function moveActivity(
  overrides: Partial<Extract<MatchActivityItem, { kind: "MOVE" }>> = {},
): Extract<MatchActivityItem, { kind: "MOVE" }> {
  return {
    id: "move-1",
    kind: "MOVE",
    sequence: 1,
    player: "WHITE",
    notation: "e2e4",
    movingPiece: "PAWN",
    movingPieceColor: "WHITE",
    sourceSquare: "e2",
    destinationSquare: "e4",
    capture: false,
    check: false,
    checkmate: false,
    promotion: false,
    isNew: false,
    ...overrides,
  };
}

function dialogueActivity(
  overrides: Partial<Extract<MatchActivityItem, { kind: "DIALOGUE" }>> = {},
): Extract<MatchActivityItem, { kind: "DIALOGUE" }> {
  return {
    id: "dialogue-1",
    kind: "DIALOGUE",
    sequence: 1,
    dialogueId: 1,
    triggerType: "MOVE",
    personalityKey: "blaze",
    personalityDisplayName: "Blaze",
    side: "WHITE",
    text: "That pawn move already has you worried.",
    emotion: "CONFIDENT",
    reactionType: "MOVE_REACTION",
    createdAt: "2026-08-18T00:00:00Z",
    ...overrides,
  };
}

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
    expect(
      screen.getByText("Waiting for the match to start..."),
    ).toBeInTheDocument();
  });

  it("renders activities in chronological order and formats results", () => {
    const activities: MatchActivityItem[] = [
      { id: "match-started", kind: "MATCH_STARTED", sequence: 0 },
      moveActivity(),
      moveActivity({
        id: "move-2",
        sequence: 2,
        player: "BLACK",
        notation: "e7e5",
        movingPieceColor: "BLACK",
        sourceSquare: "e7",
        destinationSquare: "e5",
        check: true,
      }),
      moveActivity({
        id: "move-3",
        sequence: 3,
        notation: "c4d5",
        movingPiece: "KNIGHT",
        sourceSquare: "c4",
        destinationSquare: "d5",
        capturedPiece: "PAWN",
        capturedPieceColor: "BLACK",
        capture: true,
      }),
      {
        id: "match-finished",
        kind: "MATCH_FINISHED",
        sequence: 4,
        result: "WHITE_WINS",
      },
    ];
    useMatchViewerStore.setState({ activities });

    render(<MatchActivityPanel />);

    expect(screen.getByText("Match Activity")).toBeInTheDocument();
    expect(screen.getByText("5 events")).toBeInTheDocument();

    expect(screen.getByText("Match started")).toBeInTheDocument();
    expect(
      screen.getByText("Stockfish White moves the Pawn to e4"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Stockfish Black moves the Pawn to e5"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Stockfish White's Knight captures Stockfish Black's Pawn",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("c4 × d5 · c4d5")).toBeInTheDocument();
    expect(screen.getByLabelText("White Knight")).toHaveTextContent("♘");
    expect(screen.getByText("Match finished — White wins")).toBeInTheDocument();

    expect(screen.getByText("Check")).toBeInTheDocument();
    expect(screen.getByText("Capture")).toBeInTheDocument();
  });

  it("styles a new capture for arrival and keeps capture rows focusable", () => {
    useMatchViewerStore.setState({
      activities: [
        moveActivity({
          movingPiece: "KNIGHT",
          notation: "c4d5",
          sourceSquare: "c4",
          destinationSquare: "d5",
          capturedPiece: "PAWN",
          capturedPieceColor: "BLACK",
          capture: true,
          isNew: true,
        }),
      ],
    });

    render(<MatchActivityPanel />);

    const row = screen.getByTestId("activity-move-1");
    expect(row).toHaveAttribute("tabindex", "0");
    expect(row).toHaveClass("capture-row-arrival");
    expect(screen.getByText("Capture")).toBeInTheDocument();
    expect(screen.getByTestId("capture-indicator-arrival")).toHaveClass(
      "capture-indicator-arrival",
    );
    expect(screen.getByTestId("capture-indicator-hover")).toHaveClass(
      "capture-indicator-hover",
    );
  });

  it("keeps historical captures calm until hover or focus", () => {
    useMatchViewerStore.setState({
      activities: [
        moveActivity({
          capturedPiece: "PAWN",
          capturedPieceColor: "BLACK",
          capture: true,
          isNew: false,
        }),
      ],
    });

    render(<MatchActivityPanel />);

    const row = screen.getByTestId("activity-move-1");
    expect(row).toHaveAttribute("tabindex", "0");
    expect(row).not.toHaveClass("capture-row-arrival");
    expect(
      screen.queryByTestId("capture-indicator-arrival"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("capture-indicator-hover")).toHaveClass(
      "capture-indicator-hover",
    );
  });

  it("does not add capture behavior to normal moves", () => {
    useMatchViewerStore.setState({ activities: [moveActivity()] });

    render(<MatchActivityPanel />);

    const row = screen.getByTestId("activity-move-1");
    expect(row).not.toHaveAttribute("tabindex");
    expect(row).not.toHaveClass("capture-row-arrival");
    expect(screen.queryByText("Capture")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("capture-indicator-hover"),
    ).not.toBeInTheDocument();
  });

  it("renders personality dialogue with visible identity, side, emotion, and reaction labels", () => {
    useMatchViewerStore.setState({
      activities: [
        dialogueActivity(),
        dialogueActivity({
          id: "dialogue-2",
          dialogueId: 2,
          personalityKey: "vesper",
          personalityDisplayName: "Vesper",
          side: "BLACK",
          text: "Worried? I call that optimism.",
          emotion: "DEFIANT",
          reactionType: "MOVE_REACTION",
        }),
      ],
    });

    render(<MatchActivityPanel />);

    const blaze = screen.getByTestId("activity-dialogue-1");
    expect(within(blaze).getByText("Blaze")).toBeInTheDocument();
    expect(within(blaze).getByText("White")).toBeInTheDocument();
    expect(within(blaze).getByText("Confident")).toBeInTheDocument();
    expect(within(blaze).getByText("Move reaction")).toBeInTheDocument();
    expect(
      within(blaze).getByText("That pawn move already has you worried."),
    ).toBeInTheDocument();
    expect(blaze).toHaveAccessibleName(
      "Blaze dialogue, White, Confident, Move reaction",
    );

    const vesper = screen.getByTestId("activity-dialogue-2");
    expect(within(vesper).getByText("Vesper")).toBeInTheDocument();
    expect(within(vesper).getByText("Black")).toBeInTheDocument();
    expect(within(vesper).getByText("Defiant")).toBeInTheDocument();
    expect(vesper).toHaveAccessibleName(
      "Vesper dialogue, Black, Defiant, Move reaction",
    );
  });

  it("renders an unmatched speaker neutrally instead of guessing a side", () => {
    useMatchViewerStore.setState({
      activities: [
        dialogueActivity({
          personalityKey: "unknown",
          personalityDisplayName: "Unknown",
          side: null,
        }),
      ],
    });

    render(<MatchActivityPanel />);

    const row = screen.getByTestId("activity-dialogue-1");
    expect(within(row).getByText("Character")).toBeInTheDocument();
    expect(row).toHaveAccessibleName(
      "Unknown dialogue, Character, Confident, Move reaction",
    );
  });

  it("keeps long dialogue histories inside the bounded scroll feed", () => {
    useMatchViewerStore.setState({
      activities: Array.from({ length: 50 }, (_, index) =>
        dialogueActivity({
          id: `dialogue-${index + 1}`,
          dialogueId: index + 1,
          sequence: index + 1,
          text: `Banter line ${index + 1}`,
        }),
      ),
    });

    render(<MatchActivityPanel />);

    expect(screen.getByTestId("match-activity-panel")).toHaveClass(
      "max-h-[600px]",
      "lg:max-h-[calc(100vh-200px)]",
    );
    expect(screen.getByTestId("match-activity-feed")).toHaveClass(
      "overflow-y-auto",
    );
    expect(screen.getByText("50 events")).toBeInTheDocument();
    expect(screen.getByText("Banter line 50")).toBeInTheDocument();
  });

  it("scrolls only the activity feed when activities are added", () => {
    const { rerender } = render(<MatchActivityPanel />);
    const feed = document.querySelector("#match-activity-feed");
    expect(feed).not.toBeNull();
    Object.defineProperty(feed, "scrollHeight", {
      configurable: true,
      value: 480,
    });

    useMatchViewerStore.setState({
      activities: [{ id: "match-started", kind: "MATCH_STARTED", sequence: 0 }],
    });
    rerender(<MatchActivityPanel />);

    expect(feed?.scrollTop).toBe(480);
  });
});
