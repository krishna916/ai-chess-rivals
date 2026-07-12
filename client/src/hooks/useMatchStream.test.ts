import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useMatchViewerStore } from "../store/matchViewerStore";
import { createMatchSocket } from "../services/matchSocket";
import { useMatchStream } from "./useMatchStream";

const socket = {
  connect: vi.fn(),
  close: vi.fn(),
};
let callbacks: Parameters<typeof createMatchSocket>[1];

vi.mock("../services/matchSocket", () => ({
  createMatchSocket: vi.fn((_baseUrl, socketCallbacks) => {
    callbacks = socketCallbacks;
    return socket;
  }),
}));

describe("useMatchStream", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    socket.connect.mockClear();
    socket.close.mockClear();
    useMatchViewerStore.setState({
      connectionStatus: "CONNECTING",
      error: undefined,
    });
  });

  it("reconnects with exponential backoff and resets retries after connecting", () => {
    const { unmount } = renderHook(() => useMatchStream());
    expect(socket.connect).toHaveBeenCalledTimes(1);

    act(() => callbacks.onClose?.(new CloseEvent("close")));
    act(() => vi.advanceTimersByTime(3_000));
    expect(socket.connect).toHaveBeenCalledTimes(2);

    act(() => callbacks.onClose?.(new CloseEvent("close")));
    act(() => vi.advanceTimersByTime(3_000));
    expect(socket.connect).toHaveBeenCalledTimes(2);
    act(() => vi.advanceTimersByTime(3_000));
    expect(socket.connect).toHaveBeenCalledTimes(3);

    act(() => callbacks.onOpen?.());
    act(() => callbacks.onClose?.(new CloseEvent("close")));
    act(() => vi.advanceTimersByTime(3_000));
    expect(socket.connect).toHaveBeenCalledTimes(4);

    unmount();
  });

  it("stops reconnecting after five failed attempts", () => {
    const { unmount } = renderHook(() => useMatchStream());

    for (let attempt = 0; attempt < 5; attempt += 1) {
      act(() => callbacks.onClose?.(new CloseEvent("close")));
      act(() => vi.advanceTimersByTime(3_000 * 2 ** attempt));
    }
    expect(socket.connect).toHaveBeenCalledTimes(6);

    act(() => callbacks.onClose?.(new CloseEvent("close")));
    act(() => vi.runAllTimers());
    expect(socket.connect).toHaveBeenCalledTimes(6);

    unmount();
  });
});
