import { useEffect } from "react";
import { createMatchSocket } from "../services/matchSocket";
import { useMatchViewerStore } from "../store/matchViewerStore";
import { parseMatchMessage } from "../services/matchViewer.messages";

const INITIAL_RECONNECT_DELAY_MS = 3_000;
const MAX_RECONNECT_ATTEMPTS = 5;

export function useMatchStream(baseUrl = "http://localhost:8082") {
  const { setConnectionStatus, processMessage, setError } =
    useMatchViewerStore();

  useEffect(() => {
    let reconnectTimer: number;
    let isActive = true;
    let reconnectAttempts = 0;

    const socket = createMatchSocket(baseUrl, {
      onOpen: () => {
        if (!isActive) return;
        reconnectAttempts = 0;
        setConnectionStatus("CONNECTED");
      },
      onMessage: (data) => {
        if (!isActive) return;
        const msg = parseMatchMessage(data);
        if (msg) {
          processMessage(msg);
        }
      },
      onClose: () => {
        if (!isActive) return;
        setConnectionStatus("DISCONNECTED");
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return;

        const delay = INITIAL_RECONNECT_DELAY_MS * 2 ** reconnectAttempts;
        reconnectAttempts += 1;
        reconnectTimer = window.setTimeout(() => {
          if (isActive) socket.connect();
        }, delay);
      },
      onError: () => {
        if (!isActive) return;
        setError("Connection error occurred.");
      },
    });

    socket.connect();

    return () => {
      isActive = false;
      clearTimeout(reconnectTimer);
      socket.close();
    };
  }, [baseUrl, setConnectionStatus, processMessage, setError]);
}
