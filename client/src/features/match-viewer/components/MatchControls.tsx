import { useState } from "react";
import { Button } from "@/components/ui/button";
import { matchApi } from "@/services/matchApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import type { MatchResponse } from "@/types/match";

export function MatchControls() {
  const { matchStatus, processMessage } = useMatchViewerStore();
  const [pendingAction, setPendingAction] = useState<"start" | "stop">();
  const [requestError, setRequestError] = useState<string>();

  const handleMatchOperation = async (
    operation: () => Promise<MatchResponse>,
    action: "start" | "stop",
  ) => {
    try {
      setPendingAction(action);
      setRequestError(undefined);
      const snapshot = await operation();
      processMessage({ type: "MATCH_STATE", payload: snapshot });
    } catch (error) {
      console.error(`Failed to ${action} match:`, error);
      setRequestError(`Unable to ${action} the match. Please try again.`);
    } finally {
      setPendingAction(undefined);
    }
  };

  const isRunning = matchStatus === "IN_PROGRESS";
  const isLoading = pendingAction !== undefined;
  const startLabel =
    pendingAction === "start"
      ? "Starting…"
      : matchStatus === "STOPPED"
        ? "Resume Match"
        : "Start Match";
  const stopLabel = pendingAction === "stop" ? "Stopping…" : "Stop Match";

  return (
    <div className="flex items-center gap-2" aria-live="polite">
      <Button
        onClick={() => handleMatchOperation(matchApi.startMatch, "start")}
        disabled={isLoading || isRunning}
        variant={isRunning ? "secondary" : "default"}
        size="sm"
      >
        {startLabel}
      </Button>
      <Button
        onClick={() => handleMatchOperation(matchApi.stopMatch, "stop")}
        disabled={isLoading || !isRunning}
        variant="destructive"
        size="sm"
      >
        {stopLabel}
      </Button>
      {requestError && (
        <p className="text-sm text-destructive" role="alert">
          {requestError}
        </p>
      )}
    </div>
  );
}
