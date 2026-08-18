import { useMatchViewerStore } from "@/store/matchViewerStore";
import { cn } from "@/lib/utils";
import { MATCH_PLAYERS } from "../lib/matchPlayers";

interface PlayerStripProps {
  side: "WHITE" | "BLACK";
}

export function PlayerStrip({ side }: PlayerStripProps) {
  const { activeTurn, matchStatus, whitePersonality, blackPersonality } =
    useMatchViewerStore();
  const isActive = matchStatus === "IN_PROGRESS" && activeTurn === side;
  const personality = side === "WHITE" ? whitePersonality : blackPersonality;
  const displayName = personality?.displayName ?? MATCH_PLAYERS[side].name;

  return (
    <div
      className={cn(
        "p-3 rounded border flex items-center justify-between",
        isActive
          ? "bg-accent border-accent text-accent-foreground"
          : "bg-card text-card-foreground",
      )}
    >
      <span className="min-w-0 break-words font-semibold">{displayName}</span>
      {isActive && (
        <span className="text-xs uppercase bg-background/50 px-2 py-1 rounded">
          Thinking...
        </span>
      )}
    </div>
  );
}
