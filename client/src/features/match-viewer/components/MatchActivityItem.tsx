import type { MatchActivityItem as ActivityType } from "@/types/match";
import { Swords, Trophy, CirclePlay, Crown, ShieldAlert } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  formatMoveDescription,
  formatMoveDetail,
  getPieceAccessibleLabel,
  getPieceSymbol,
} from "../lib/formatMoveActivity";

interface MatchActivityItemProps {
  activity: ActivityType;
}

export function MatchActivityItem({ activity }: MatchActivityItemProps) {
  const formatResult = (result: string) => {
    switch (result) {
      case "WHITE_WINS":
        return "White wins";
      case "BLACK_WINS":
        return "Black wins";
      case "DRAW":
        return "Draw";
      default:
        return result.replace(/_/g, " ").toLowerCase();
    }
  };

  switch (activity.kind) {
    case "MATCH_STARTED":
      return (
        <div className="flex items-center gap-2.5 py-1.5 px-3 rounded-lg bg-emerald-50/50 dark:bg-emerald-950/20 border border-emerald-100/50 dark:border-emerald-900/30 text-emerald-800 dark:text-emerald-300 text-sm font-medium">
          <CirclePlay className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
          <span>Match started</span>
        </div>
      );

    case "MOVE": {
      const moveNumber = Math.ceil(activity.sequence / 2);
      const pieceLabel = getPieceAccessibleLabel(
        activity.movingPiece,
        activity.movingPieceColor,
      );
      const captureIndicator = (
        <span
          data-testid="capture-indicator-hover"
          className="capture-indicator-hover inline-flex"
        >
          <Swords className="w-3 h-3" />
        </span>
      );

      return (
        <div
          data-testid={`activity-${activity.id}`}
          tabIndex={activity.capture ? 0 : undefined}
          className={cn(
            "group rounded-lg border border-transparent px-3 py-2 text-sm transition-colors hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring dark:hover:bg-neutral-800",
            activity.capture && activity.isNew && "capture-row-arrival",
          )}
        >
          <div className="flex items-start gap-2">
            <span className="w-7 shrink-0 pt-0.5 font-mono text-xs text-muted-foreground">
              {moveNumber}.
            </span>
            <span
              aria-label={pieceLabel}
              className="w-6 shrink-0 text-center text-xl leading-6"
            >
              {getPieceSymbol(activity.movingPiece, activity.movingPieceColor)}
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-xs font-medium leading-snug text-foreground">
                {formatMoveDescription(activity)}
              </p>
              <p
                className="mt-0.5 truncate font-mono text-[11px] text-muted-foreground"
                title={activity.notation}
              >
                {formatMoveDetail(activity)}
              </p>
              <div className="mt-1 flex flex-wrap items-center gap-1.5">
                {activity.checkmate && (
                  <span className="inline-flex items-center gap-1 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider text-red-700 dark:bg-red-950/50 dark:text-red-300">
                    <Crown className="w-3 h-3" />
                    Mate
                  </span>
                )}
                {!activity.checkmate && activity.check && (
                  <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider text-amber-700 dark:bg-amber-950/50 dark:text-amber-300">
                    <ShieldAlert className="w-3 h-3" />
                    Check
                  </span>
                )}
                {activity.capture && (
                  <span className="inline-flex items-center gap-1 rounded bg-rose-100 px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider text-rose-700 dark:bg-rose-950/50 dark:text-rose-300">
                    {activity.isNew ? (
                      <span
                        data-testid="capture-indicator-arrival"
                        className="capture-indicator-arrival inline-flex"
                      >
                        {captureIndicator}
                      </span>
                    ) : (
                      captureIndicator
                    )}
                    Capture
                  </span>
                )}
                {activity.promotion && (
                  <span className="inline-flex items-center gap-1 rounded bg-blue-100 px-1.5 py-0.5 text-xs font-semibold uppercase tracking-wider text-blue-700 dark:bg-blue-950/50 dark:text-blue-300">
                    Promotion
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      );
    }

    case "MATCH_FINISHED":
      return (
        <div className="flex items-center gap-2.5 py-2 px-3 rounded-lg bg-indigo-50/50 dark:bg-indigo-950/20 border border-indigo-100/50 dark:border-indigo-900/30 text-indigo-800 dark:text-indigo-300 text-sm font-medium">
          <Trophy className="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
          <span>Match finished — {formatResult(activity.result || "")}</span>
        </div>
      );

    default:
      return null;
  }
}
