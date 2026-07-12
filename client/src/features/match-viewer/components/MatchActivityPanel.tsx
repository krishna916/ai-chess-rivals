import { useEffect, useRef } from "react";
import { useMatchViewerStore } from "@/store/matchViewerStore";
import { MatchActivityItem } from "./MatchActivityItem";

export function MatchActivityPanel() {
  const activities = useMatchViewerStore((state) => state.activities);
  const feedRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (activities.length > 0 && feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [activities.length]);

  return (
    <div className="bg-card shadow rounded-xl border flex flex-col h-full min-h-[400px] max-h-[600px] lg:max-h-[calc(100vh-200px)]">
      <div className="p-4 border-b border-border flex items-center justify-between">
        <h2 className="text-lg font-semibold tracking-tight text-foreground">
          Match Activity
        </h2>
        <span className="text-xs text-muted-foreground font-medium px-2 py-0.5 bg-neutral-100 dark:bg-neutral-800 rounded-full">
          {activities.length} {activities.length === 1 ? "event" : "events"}
        </span>
      </div>

      <div
        ref={feedRef}
        className="flex-1 overflow-y-auto p-4 space-y-3"
        id="match-activity-feed"
      >
        {activities.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-4">
            <p className="text-sm text-muted-foreground font-medium">
              No activity yet
            </p>
            <p className="text-xs text-muted-foreground/60 mt-1">
              Waiting for the match to start...
            </p>
          </div>
        ) : (
          <>
            {activities.map((activity) => (
              <MatchActivityItem key={activity.id} activity={activity} />
            ))}
          </>
        )}
      </div>
    </div>
  );
}
