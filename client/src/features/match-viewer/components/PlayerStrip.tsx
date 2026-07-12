import { useMatchViewerStore } from "@/store/matchViewerStore";
import { cn } from "@/lib/utils";

interface PlayerStripProps {
  side: "WHITE" | "BLACK";
}

export function PlayerStrip({ side }: PlayerStripProps) {
  const { activeTurn, matchStatus } = useMatchViewerStore();
  const isActive = matchStatus === "IN_PROGRESS" && activeTurn === side;

  return (
    <div className={cn("p-3 rounded border flex items-center justify-between", isActive ? "bg-accent border-accent text-accent-foreground" : "bg-card text-card-foreground")}>
      <span className="font-semibold">Stockfish {side === "WHITE" ? "White" : "Black"}</span>
      {isActive && <span className="text-xs uppercase bg-background/50 px-2 py-1 rounded">Thinking...</span>}
    </div>
  );
}
