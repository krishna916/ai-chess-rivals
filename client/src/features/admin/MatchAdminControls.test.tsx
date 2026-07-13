import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { adminMatchApi } from "@/services/adminMatchApi";
import { matchApi } from "@/services/matchApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { clearOwnerToken } from "./ownerToken";
import { MatchAdminControls } from "./MatchAdminControls";

vi.mock("@/hooks/useMatchStream", () => ({ useMatchStream: vi.fn() }));
vi.mock("@/services/adminMatchApi", () => ({
  adminMatchApi: { startMatch: vi.fn(), stopMatch: vi.fn() },
}));
vi.mock("@/services/matchApi", () => ({
  matchApi: { getCurrentMatch: vi.fn() },
}));
vi.mock("./ownerToken", async (importOriginal) => {
  const original = await importOriginal<typeof import("./ownerToken")>();
  return { ...original, clearOwnerToken: vi.fn() };
});

const allowed = {
  allowed: true,
  blockedBy: null,
  retryAfterSeconds: 0,
  dailyStartsAccepted: 2,
  dailyStartLimit: 12,
} as const;

const snapshot = {
  sideToMove: "WHITE" as const,
  fen: "start",
  moves: [],
  status: "IN_PROGRESS" as const,
  result: null,
  running: true,
  startAvailability: {
    ...allowed,
    allowed: false,
    blockedBy: "MATCH_ALREADY_RUNNING" as const,
  },
};

describe("MatchAdminControls", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.setItem("ownerControlToken", "owner-token");
    useMatchViewerStore.setState({
      matchStatus: "IDLE",
      startAvailability: allowed,
      activities: [],
      moveCount: 0,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
  });

  it("starts with the bearer client and hydrates the returned snapshot", async () => {
    vi.mocked(adminMatchApi.startMatch).mockResolvedValue(snapshot);
    render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Start Match" }));

    await waitFor(() =>
      expect(adminMatchApi.startMatch).toHaveBeenCalledWith("owner-token"),
    );
    expect(useMatchViewerStore.getState()).toMatchObject({
      matchStatus: "IN_PROGRESS",
      startAvailability: snapshot.startAvailability,
    });
  });

  it("offers Start before any match snapshot exists", async () => {
    useMatchViewerStore.setState({
      matchStatus: "IDLE",
      startAvailability: undefined,
    });
    vi.mocked(adminMatchApi.startMatch).mockResolvedValue(snapshot);
    render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Start Match" }));

    await waitFor(() =>
      expect(adminMatchApi.startMatch).toHaveBeenCalledWith("owner-token"),
    );
  });

  it("shows pending Stop and sends one stop request", async () => {
    let resolveStop: (value: typeof snapshot) => void = () => undefined;
    vi.mocked(adminMatchApi.stopMatch).mockImplementation(
      () => new Promise((resolve) => (resolveStop = resolve)),
    );
    useMatchViewerStore.setState({ matchStatus: "IN_PROGRESS" });
    render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Stop Match" }));
    expect(screen.getByRole("button", { name: "Stopping…" })).toBeDisabled();
    resolveStop({ ...snapshot, running: false });
    await waitFor(() =>
      expect(adminMatchApi.stopMatch).toHaveBeenCalledTimes(1),
    );
  });

  it("clears and relocks on 401", async () => {
    const onUnauthorized = vi.fn();
    vi.mocked(adminMatchApi.startMatch).mockRejectedValue({
      response: { status: 401 },
    });
    render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={onUnauthorized}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Start Match" }));

    await waitFor(() => expect(clearOwnerToken).toHaveBeenCalled());
    expect(onUnauthorized).toHaveBeenCalled();
  });

  it("shows server cooldown and refreshes when its countdown reaches zero", async () => {
    vi.useFakeTimers();
    useMatchViewerStore.setState({
      startAvailability: {
        ...allowed,
        allowed: false,
        blockedBy: "MATCH_COOLDOWN_ACTIVE",
        retryAfterSeconds: 2,
      },
    });
    vi.mocked(matchApi.getCurrentMatch).mockResolvedValue({
      ...snapshot,
      running: false,
      startAvailability: allowed,
    });
    render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );

    expect(screen.getByText("Start available in 2 seconds.")).toBeVisible();
    await act(async () => vi.advanceTimersByTime(2_000));

    expect(matchApi.getCurrentMatch).toHaveBeenCalledTimes(1);
  });

  it("refreshes stopped availability for admin sessions that did not issue Stop", async () => {
    useMatchViewerStore.setState({
      matchStatus: "IN_PROGRESS",
      startAvailability: snapshot.startAvailability,
    });
    const cooldownSnapshot = {
      ...snapshot,
      running: false,
      startAvailability: {
        ...allowed,
        allowed: false,
        blockedBy: "MATCH_COOLDOWN_ACTIVE" as const,
        retryAfterSeconds: 60,
      },
    };
    vi.mocked(matchApi.getCurrentMatch).mockResolvedValue(cooldownSnapshot);
    render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );

    act(() =>
      useMatchViewerStore.getState().processMessage({
        type: "MATCH_STOPPED",
        payload: { sideToMove: "BLACK", fen: "after-e4", totalPlies: 1 },
      }),
    );

    await waitFor(() => expect(matchApi.getCurrentMatch).toHaveBeenCalled());
    expect(useMatchViewerStore.getState().startAvailability).toEqual(
      cooldownSnapshot.startAvailability,
    );
  });

  it("shows quota and stable backend error messages", async () => {
    useMatchViewerStore.setState({
      startAvailability: {
        ...allowed,
        allowed: false,
        blockedBy: "MATCH_DAILY_LIMIT_REACHED",
        dailyStartsAccepted: 12,
      },
    });
    const { rerender } = render(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );
    expect(screen.getByText("Daily starts: 12 / 12")).toBeVisible();
    expect(
      screen.getByText("The configured daily match limit has been reached."),
    ).toBeVisible();

    useMatchViewerStore.setState({ startAvailability: allowed });
    vi.mocked(adminMatchApi.startMatch).mockRejectedValue({
      response: {
        status: 429,
        data: { message: "A new match cannot be started yet." },
      },
    });
    rerender(
      <MatchAdminControls
        token="owner-token"
        onLock={vi.fn()}
        onUnauthorized={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Start Match" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A new match cannot be started yet.",
    );
  });
});
