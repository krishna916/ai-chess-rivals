import { Chessboard } from "react-chessboard";
import { useMatchViewerStore } from "@/store/matchViewerStore";

const darkSquareStyle = { backgroundColor: "#779556" };
const lightSquareStyle = { backgroundColor: "#ebecd0" };

export function MatchBoard() {
  const fen = useMatchViewerStore((s) => s.boardFen);

  return (
    <div className="w-full aspect-square max-w-[600px] mx-auto">
      <Chessboard
        key={fen}
        position={fen}
        arePiecesDraggable={false}
        animationDuration={0}
        customDarkSquareStyle={darkSquareStyle}
        customLightSquareStyle={lightSquareStyle}
      />
    </div>
  );
}
