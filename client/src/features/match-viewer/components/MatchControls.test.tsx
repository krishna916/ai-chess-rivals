import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { matchApi } from "@/services/matchApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { MatchControls } from "./MatchControls";

vi.mock("@/services/matchApi", () => ({
  matchApi: {
    startMatch: vi.fn(),
    stopMatch: vi.fn(),
  },
}));

const stoppedSnapshot = {
  sideToMove: "BLACK" as const,
  fen: "after-e4",
  moves: [
    {
      sequenceNumber: 1,
      player: "WHITE" as const,
      notation: "e2e4",
      fenAfterMove: "after-e4",
    },
  ],
  status: "IN_PROGRESS" as const,
  result: null,
  running: false,
};

describe("MatchControls", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useMatchViewerStore.setState({
      matchStatus: "IDLE",
      boardFen: "start",
      activeTurn: "WHITE",
      moveCount: 0,
      activities: [],
      result: undefined,
    });
  });

  afterEach(cleanup);

  it("hydrates the stopped snapshot returned by the stop request", async () => {
    useMatchViewerStore.setState({ matchStatus: "IN_PROGRESS" });
    vi.mocked(matchApi.stopMatch).mockResolvedValue(stoppedSnapshot);
    render(<MatchControls />);

    fireEvent.click(screen.getByRole("button", { name: "Stop Match" }));

    await waitFor(() => {
      expect(useMatchViewerStore.getState()).toMatchObject({
        matchStatus: "STOPPED",
        boardFen: "after-e4",
        moveCount: 1,
      });
    });
  });

  it("labels start as resume for a stopped match", () => {
    useMatchViewerStore.setState({ matchStatus: "STOPPED" });
    render(<MatchControls />);

    expect(screen.getByRole("button", { name: "Resume Match" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Stop Match" })).toBeDisabled();
  });

  it("shows pending state and a localized request error", async () => {
    let rejectRequest: (reason: Error) => void = () => undefined;
    vi.mocked(matchApi.startMatch).mockImplementation(
      () =>
        new Promise((_, reject) => {
          rejectRequest = reject;
        }),
    );
    render(<MatchControls />);

    fireEvent.click(screen.getByRole("button", { name: "Start Match" }));
    expect(screen.getByRole("button", { name: "Starting…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Stop Match" })).toBeDisabled();

    rejectRequest(new Error("backend detail"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to start the match. Please try again.",
    );
  });
});
