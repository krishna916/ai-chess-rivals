import { useState } from "react";
import { Button } from "@/components/ui/button";
import { matchApi } from "@/services/matchApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";

export function MatchControls() {
  const { matchStatus } = useMatchViewerStore();
  const [loading, setLoading] = useState(false);

  const handleStart = async () => {
    try {
      setLoading(true);
      await matchApi.startMatch();
    } catch (error) {
      console.error("Failed to start match:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleStop = async () => {
    try {
      setLoading(true);
      await matchApi.stopMatch();
    } catch (error) {
      console.error("Failed to stop match:", error);
    } finally {
      setLoading(false);
    }
  };

  const isRunning = matchStatus === "IN_PROGRESS";

  return (
    <div className="flex items-center gap-2">
      <Button
        onClick={handleStart}
        disabled={loading || isRunning}
        variant={isRunning ? "secondary" : "default"}
        size="sm"
      >
        Start Match
      </Button>
      <Button
        onClick={handleStop}
        disabled={loading || !isRunning}
        variant="destructive"
        size="sm"
      >
        Stop Match
      </Button>
    </div>
  );
}
