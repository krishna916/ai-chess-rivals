import { useEffect } from "react";
import { createMatchSocket } from "../services/matchSocket";
import { useMatchViewerStore } from "../store/matchViewerStore";
import { parseMatchMessage } from "../services/matchViewer.messages";

export function useMatchStream(baseUrl = "http://localhost:8082") {
  const { setConnectionStatus, processMessage, setError } =
    useMatchViewerStore();

  useEffect(() => {
    let reconnectTimer: number;
    let isActive = true;

    const socket = createMatchSocket(baseUrl, {
      onOpen: () => {
        if (!isActive) return;
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
        reconnectTimer = window.setTimeout(() => {
          socket.connect();
        }, 3000);
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
