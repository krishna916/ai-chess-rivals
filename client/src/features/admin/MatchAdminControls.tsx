import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useMatchStream } from "@/hooks/useMatchStream";
import { adminMatchApi } from "@/services/adminMatchApi";
import { matchApi } from "@/services/matchApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import type { MatchResponse } from "@/types/match";
import { clearOwnerToken } from "./ownerToken";

interface MatchAdminControlsProps {
  token: string;
  onLock: () => void;
  onUnauthorized: () => void;
}

interface ProblemResponse {
  message?: string;
}

function responseStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number } })?.response?.status;
}

function responseMessage(error: unknown): string | undefined {
  return (error as { response?: { data?: ProblemResponse } })?.response?.data
    ?.message;
}

export function MatchAdminControls({
  token,
  onLock,
  onUnauthorized,
}: MatchAdminControlsProps) {
  useMatchStream();
  const { matchStatus, startAvailability, processMessage } =
    useMatchViewerStore();
  const [pendingAction, setPendingAction] = useState<"start" | "stop">();
  const [requestError, setRequestError] = useState<string>();
  const [cooldownCountdown, setCooldownCountdown] = useState<{
    sourceSeconds: number;
    remainingSeconds: number;
  }>();

  const hydrate = useCallback(
    (snapshot: MatchResponse) =>
      processMessage({ type: "MATCH_STATE", payload: snapshot }),
    [processMessage],
  );

  const refreshSnapshot = useCallback(async () => {
    try {
      hydrate(await matchApi.getCurrentMatch());
    } catch {
      // The live stream remains authoritative if a refresh races the backend.
    }
  }, [hydrate]);

  useEffect(() => {
    if (
      startAvailability?.blockedBy !== "MATCH_COOLDOWN_ACTIVE" ||
      startAvailability.retryAfterSeconds <= 0
    ) {
      return;
    }

    let remaining = startAvailability.retryAfterSeconds;
    const timer = window.setInterval(() => {
      remaining = Math.max(0, remaining - 1);
      setCooldownCountdown({
        sourceSeconds: startAvailability.retryAfterSeconds,
        remainingSeconds: remaining,
      });
      if (remaining === 0) {
        window.clearInterval(timer);
        void refreshSnapshot();
      }
    }, 1_000);
    return () => window.clearInterval(timer);
  }, [refreshSnapshot, startAvailability]);

  useEffect(() => {
    if (matchStatus !== "FINISHED" && matchStatus !== "STOPPED") return;
    const timer = window.setTimeout(() => void refreshSnapshot(), 0);
    return () => window.clearTimeout(timer);
  }, [matchStatus, refreshSnapshot]);

  const runOperation = async (
    action: "start" | "stop",
    operation: (ownerToken: string) => Promise<MatchResponse>,
  ) => {
    setPendingAction(action);
    setRequestError(undefined);
    try {
      hydrate(await operation(token));
    } catch (error) {
      if (responseStatus(error) === 401) {
        clearOwnerToken();
        onUnauthorized();
        return;
      }
      setRequestError(
        responseMessage(error) ??
          `Unable to ${action} the match. Please try again.`,
      );
    } finally {
      setPendingAction(undefined);
    }
  };

  const lock = () => {
    clearOwnerToken();
    onLock();
  };

  const running = matchStatus === "IN_PROGRESS";
  const showStart =
    !running &&
    (startAvailability?.allowed === true ||
      (matchStatus === "IDLE" && startAvailability === undefined));
  const isPending = pendingAction !== undefined;
  const cooldownSeconds =
    cooldownCountdown &&
    cooldownCountdown.sourceSeconds === startAvailability?.retryAfterSeconds
      ? cooldownCountdown.remainingSeconds
      : (startAvailability?.retryAfterSeconds ?? 0);

  return (
    <Card className="w-full max-w-xl shadow-lg">
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle>Owner Match Controls</CardTitle>
            <CardDescription>
              Current state: {matchStatus.toLowerCase()}
            </CardDescription>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={lock}>
            Lock Controls
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {startAvailability && (
          <p className="text-sm text-muted-foreground">
            Daily starts: {startAvailability.dailyStartsAccepted} /{" "}
            {startAvailability.dailyStartLimit}
          </p>
        )}

        {startAvailability?.blockedBy === "MATCH_COOLDOWN_ACTIVE" && (
          <p className="text-sm font-medium text-amber-700">
            Start available in {cooldownSeconds} seconds.
          </p>
        )}
        {startAvailability?.blockedBy === "MATCH_DAILY_LIMIT_REACHED" && (
          <p className="text-sm font-medium text-amber-700">
            The configured daily match limit has been reached.
          </p>
        )}

        <div className="flex gap-2" aria-live="polite">
          {showStart && (
            <Button
              type="button"
              disabled={isPending}
              onClick={() =>
                void runOperation("start", adminMatchApi.startMatch)
              }
            >
              {pendingAction === "start" ? "Starting…" : "Start Match"}
            </Button>
          )}
          {running && (
            <Button
              type="button"
              variant="destructive"
              disabled={isPending}
              onClick={() => void runOperation("stop", adminMatchApi.stopMatch)}
            >
              {pendingAction === "stop" ? "Stopping…" : "Stop Match"}
            </Button>
          )}
        </div>

        {requestError && (
          <p className="text-sm text-destructive" role="alert">
            {requestError}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
