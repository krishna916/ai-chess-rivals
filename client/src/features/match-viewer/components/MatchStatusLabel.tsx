import { useMatchViewerStore } from "@/store/matchViewerStore";

export function MatchStatusLabel() {
  const { matchStatus, result, moveCount } = useMatchViewerStore();

  if (matchStatus === "IDLE") return <div className="text-neutral-500">No match is currently running.</div>;
  if (matchStatus === "FINISHED") return <div className="text-lg font-bold text-green-700">{result || "Match Finished"}</div>;

  return (
    <div className="flex gap-4 text-sm font-medium">
      <span className="text-green-600 uppercase tracking-wider">Live</span>
      <span className="text-neutral-500">Move {moveCount}</span>
    </div>
  );
}
