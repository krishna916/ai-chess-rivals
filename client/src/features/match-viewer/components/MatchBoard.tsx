import { Chessboard } from "react-chessboard";
import { useMatchViewerStore } from "@/store/matchViewerStore";

export function MatchBoard() {
  const fen = useMatchViewerStore((s) => s.boardFen);

  return (
    <div className="w-full aspect-square max-w-[600px] mx-auto">
      <Chessboard
        options={{
          position: fen,
          allowDragging: false,
          darkSquareStyle: { backgroundColor: "#779556" },
          lightSquareStyle: { backgroundColor: "#ebecd0" },
        }}
      />
    </div>
  );
}
