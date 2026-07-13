import { useMatchStream } from "@/hooks/useMatchStream";
import { MatchBoard } from "@/features/match-viewer/components/MatchBoard";
import { PlayerStrip } from "@/features/match-viewer/components/PlayerStrip";
import { MatchStatusLabel } from "@/features/match-viewer/components/MatchStatusLabel";
import { ConnectionBadge } from "@/features/match-viewer/components/ConnectionBadge";
import { MatchActivityPanel } from "@/features/match-viewer/components/MatchActivityPanel";

export function MatchViewerPage() {
  useMatchStream();

  return (
    <div className="min-h-screen bg-neutral-50 dark:bg-neutral-900 p-4 md:p-8 flex justify-center">
      <div className="w-full max-w-5xl grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-8">
        {/* Main Chess Column */}
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <h1 className="text-2xl font-bold">Live Match Viewer</h1>
            </div>
            <ConnectionBadge />
          </div>

          <div className="bg-card shadow rounded-xl p-4 border flex flex-col gap-4">
            <MatchStatusLabel />
            <PlayerStrip side="BLACK" />
            <MatchBoard />
            <PlayerStrip side="WHITE" />
          </div>
        </div>

        {/* Activity Feed (Issue #13) */}
        <div className="block">
          <MatchActivityPanel />
        </div>
      </div>
    </div>
  );
}
