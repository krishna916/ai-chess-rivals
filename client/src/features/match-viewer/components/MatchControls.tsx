import { useState } from "react";
import { Button } from "@/components/ui/button";
import { matchApi } from "@/services/matchApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";

export function MatchControls() {
  const { matchStatus } = useMatchViewerStore();
  const [loading, setLoading] = useState(false);
  const [requestError, setRequestError] = useState<string>();

  const handleMatchOperation = async (
    operation: () => Promise<unknown>,
    action: "start" | "stop",
  ) => {
    try {
      setLoading(true);
      setRequestError(undefined);
      await operation();
    } catch (error) {
      console.error(`Failed to ${action} match:`, error);
      setRequestError(`Unable to ${action} the match. Please try again.`);
    } finally {
      setLoading(false);
    }
  };

  const isRunning = matchStatus === "IN_PROGRESS";

  return (
    <div className="flex items-center gap-2" aria-live="polite">
      <Button
        onClick={() => handleMatchOperation(matchApi.startMatch, "start")}
        disabled={loading || isRunning}
        variant={isRunning ? "secondary" : "default"}
        size="sm"
      >
        Start Match
      </Button>
      <Button
        onClick={() => handleMatchOperation(matchApi.stopMatch, "stop")}
        disabled={loading || !isRunning}
        variant="destructive"
        size="sm"
      >
        Stop Match
      </Button>
      {requestError && (
        <p className="text-sm text-destructive" role="alert">
          {requestError}
        </p>
      )}
    </div>
  );
}
