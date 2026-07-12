import { Chessboard } from "react-chessboard";
import { useMatchViewerStore } from "@/store/matchViewerStore";

export function MatchBoard() {
  const fen = useMatchViewerStore((s) => s.boardFen);

  return (
    <div className="w-full aspect-square max-w-[600px] mx-auto">
      <Chessboard 
        position={fen} 
        arePiecesDraggable={false}
        customDarkSquareStyle={{ backgroundColor: "#779556" }}
        customLightSquareStyle={{ backgroundColor: "#ebecd0" }}
      />
    </div>
  );
}
