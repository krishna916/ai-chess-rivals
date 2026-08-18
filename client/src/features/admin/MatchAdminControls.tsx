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
import { personalityApi } from "@/services/personalityApi";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import type { MatchResponse, PersonalityRosterItem } from "@/types/match";
import { clearOwnerToken } from "./ownerToken";
import { RivalrySetup } from "./RivalrySetup";
import { randomizeRivalry } from "./rivalrySelection";

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
  const {
    matchStatus,
    startAvailability,
    processMessage,
    whitePersonality,
    blackPersonality,
  } = useMatchViewerStore();
  const [pendingAction, setPendingAction] = useState<"start" | "stop">();
  const [requestError, setRequestError] = useState<string>();
  const [cooldownCountdown, setCooldownCountdown] = useState<{
    sourceSeconds: number;
    remainingSeconds: number;
  }>();
  const [roster, setRoster] = useState<PersonalityRosterItem[]>([]);
  const [rosterLoading, setRosterLoading] = useState(true);
  const [rosterError, setRosterError] = useState<string>();
  const [whitePersonalityKey, setWhitePersonalityKey] = useState("");
  const [blackPersonalityKey, setBlackPersonalityKey] = useState("");

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

  const loadRoster = useCallback(async () => {
    setRosterLoading(true);
    setRosterError(undefined);
    try {
      const loaded = await personalityApi.listSelectable();
      setRoster(loaded);
      if (loaded.length < 2) {
        setRosterError(
          "At least two active personalities are required to start a rivalry.",
        );
      }
    } catch {
      setRoster([]);
      setRosterError("Unable to load personalities. Please try again.");
    } finally {
      setRosterLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadRoster(), 0);
    return () => window.clearTimeout(timer);
  }, [loadRoster]);

  useEffect(() => {
    if (
      (matchStatus === "IN_PROGRESS" || matchStatus === "STOPPED") &&
      whitePersonality &&
      blackPersonality
    ) {
      const timer = window.setTimeout(() => {
        setWhitePersonalityKey(whitePersonality.key);
        setBlackPersonalityKey(blackPersonality.key);
      }, 0);
      return () => window.clearTimeout(timer);
    }

    if (roster.length < 2) return;

    const keys = new Set(roster.map((item) => item.key));
    const currentPairIsUsable =
      keys.has(whitePersonalityKey) &&
      keys.has(blackPersonalityKey) &&
      whitePersonalityKey !== blackPersonalityKey;

    if (!currentPairIsUsable) {
      const timer = window.setTimeout(() => {
        setWhitePersonalityKey(roster[0].key);
        setBlackPersonalityKey(roster[1].key);
      }, 0);
      return () => window.clearTimeout(timer);
    }
  }, [
    matchStatus,
    whitePersonality,
    blackPersonality,
    roster,
    whitePersonalityKey,
    blackPersonalityKey,
  ]);

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
  const stopped = matchStatus === "STOPPED";
  const rivalryEditable = !running && !stopped;
  const showStart =
    !running &&
    (stopped ||
      startAvailability?.allowed === true ||
      (matchStatus === "IDLE" && startAvailability === undefined));
  const isPending = pendingAction !== undefined;
  const validSelection =
    whitePersonalityKey !== "" &&
    blackPersonalityKey !== "" &&
    whitePersonalityKey !== blackPersonalityKey;
  const startRequest =
    stopped && whitePersonality && blackPersonality
      ? {
          whitePersonalityKey: whitePersonality.key,
          blackPersonalityKey: blackPersonality.key,
        }
      : { whitePersonalityKey, blackPersonalityKey };
  const canResume =
    stopped &&
    startAvailability?.allowed === true &&
    whitePersonality !== undefined &&
    blackPersonality !== undefined;
  const canCreateNewMatch =
    !rosterLoading &&
    rosterError === undefined &&
    roster.length >= 2 &&
    validSelection;
  const canStartOrResume = stopped ? canResume : canCreateNewMatch;
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

        {rivalryEditable && rosterLoading && (
          <p className="text-sm text-muted-foreground">
            Loading personalities…
          </p>
        )}

        {rivalryEditable && rosterError && (
          <div className="space-y-2" role="alert">
            <p className="text-sm text-destructive">{rosterError}</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void loadRoster()}
            >
              Retry
            </Button>
          </div>
        )}

        {(running || stopped) && whitePersonality && blackPersonality && (
          <div
            className="grid gap-4 rounded-md border p-3 sm:grid-cols-2"
            aria-label="Current rivalry"
          >
            <div className="space-y-1">
              <p className="text-sm font-medium">White personality</p>
              <p className="text-sm text-muted-foreground">
                {whitePersonality.displayName}
              </p>
            </div>
            <div className="space-y-1">
              <p className="text-sm font-medium">Black personality</p>
              <p className="text-sm text-muted-foreground">
                {blackPersonality.displayName}
              </p>
            </div>
          </div>
        )}

        {rivalryEditable && roster.length >= 2 && (
          <RivalrySetup
            roster={roster}
            whitePersonalityKey={whitePersonalityKey}
            blackPersonalityKey={blackPersonalityKey}
            disabled={isPending}
            onWhiteChange={setWhitePersonalityKey}
            onBlackChange={setBlackPersonalityKey}
            onRandomize={() => {
              const next = randomizeRivalry(roster);
              setWhitePersonalityKey(next.whitePersonalityKey);
              setBlackPersonalityKey(next.blackPersonalityKey);
            }}
          />
        )}

        <div className="flex gap-2" aria-live="polite">
          {showStart && (
            <Button
              type="button"
              disabled={isPending || !canStartOrResume}
              onClick={() =>
                void runOperation("start", (ownerToken) =>
                  adminMatchApi.startMatch(ownerToken, startRequest),
                )
              }
            >
              {pendingAction === "start"
                ? stopped
                  ? "Resuming…"
                  : "Starting…"
                : stopped
                  ? "Resume Match"
                  : "Start Match"}
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
