import type { MatchActivityItem as ActivityType } from "@/types/match";
import { Swords, Trophy, CirclePlay, Crown, ShieldAlert } from "lucide-react";

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
      const isWhite = activity.player === "WHITE";
      const moveNumber = Math.ceil(activity.sequence / 2);

      return (
        <div className="flex items-center justify-between py-2 px-3 rounded-lg hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors border border-transparent text-sm">
          <div className="flex items-center gap-2 text-foreground font-medium">
            <span className="text-muted-foreground w-8 font-mono text-xs">
              {moveNumber}.
            </span>
            <span
              className={`w-3 h-3 rounded-sm border ${
                isWhite
                  ? "bg-white border-neutral-300"
                  : "bg-neutral-900 border-black"
              }`}
              aria-label={activity.player}
            />
            <span className="font-mono text-sm">{activity.notation}</span>
          </div>

          <div className="flex items-center gap-1.5">
            {activity.checkmate && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-red-100 dark:bg-red-950/50 text-red-700 dark:text-red-300 text-xs font-semibold uppercase tracking-wider">
                <Crown className="w-3 h-3" />
                Mate
              </span>
            )}
            {!activity.checkmate && activity.check && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-950/50 text-amber-700 dark:text-amber-300 text-xs font-semibold uppercase tracking-wider">
                <ShieldAlert className="w-3 h-3" />
                Check
              </span>
            )}
            {activity.capture && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-rose-100 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 text-xs font-semibold uppercase tracking-wider">
                <Swords className="w-3 h-3" />
                Capture
              </span>
            )}
            {activity.promotion && (
              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-950/50 text-blue-700 dark:text-blue-300 text-xs font-semibold uppercase tracking-wider">
                Promotion
              </span>
            )}
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
