export type MatchStreamMessageType =
  | "MATCH_STATE"
  | "MATCH_STARTED"
  | "MOVE_PLAYED"
  | "MATCH_FINISHED"
  | "NO_MATCH";

export interface MatchSocketCallbacks {
  onOpen?(): void;
  onMessage?(message: unknown): void;
  onClose?(event: CloseEvent): void;
  onError?(event: Event): void;
}

export function createMatchSocket(baseHttpUrl: string, callbacks: MatchSocketCallbacks) {
  const socketUrl = new URL("/ws/match", baseHttpUrl);
  socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";

  let socket: WebSocket | null = null;

  return {
    connect() {
      socket = new WebSocket(socketUrl);
      socket.onopen = () => callbacks.onOpen?.();
      socket.onmessage = (event) => callbacks.onMessage?.(JSON.parse(event.data) as unknown);
      socket.onclose = (event) => callbacks.onClose?.(event);
      socket.onerror = (event) => callbacks.onError?.(event);
    },
    close() {
      socket?.close();
      socket = null;
    },
  };
}
