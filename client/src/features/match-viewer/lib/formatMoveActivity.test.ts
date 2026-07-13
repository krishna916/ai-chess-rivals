import { describe, expect, it } from "vitest";
import type { MatchActivityItem } from "@/types/match";
import {
  formatMoveDescription,
  formatMoveDetail,
  getOpponentColor,
  getPieceAccessibleLabel,
  getPieceSymbol,
} from "./formatMoveActivity";

const knightMove: MatchActivityItem = {
  id: "move-1",
  kind: "MOVE",
  sequence: 1,
  player: "WHITE",
  notation: "g1f3",
  movingPiece: "KNIGHT",
  movingPieceColor: "WHITE",
  sourceSquare: "g1",
  destinationSquare: "f3",
  capture: false,
  check: false,
  checkmate: false,
  promotion: false,
  isNew: false,
};

describe("formatMoveActivity", () => {
  it("formats normal moves for both players", () => {
    expect(formatMoveDescription(knightMove)).toBe(
      "Stockfish White moves the Knight to f3",
    );
    expect(
      formatMoveDescription({
        ...knightMove,
        player: "BLACK",
        movingPieceColor: "BLACK",
      }),
    ).toBe("Stockfish Black moves the Knight to f3");
  });

  it("formats captures with both player and piece names", () => {
    expect(
      formatMoveDescription({
        ...knightMove,
        notation: "c4d5",
        sourceSquare: "c4",
        destinationSquare: "d5",
        capturedPiece: "PAWN",
        capturedPieceColor: "BLACK",
        capture: true,
      }),
    ).toBe("Stockfish White's Knight captures Stockfish Black's Pawn");
  });

  it("formats castling and promotion before other flags", () => {
    expect(
      formatMoveDescription({
        ...knightMove,
        movingPiece: "KING",
        castlingSide: "KING_SIDE",
      }),
    ).toBe("Stockfish White castles kingside");
    expect(
      formatMoveDescription({
        ...knightMove,
        movingPiece: "PAWN",
        promotedPiece: "QUEEN",
        promotion: true,
      }),
    ).toBe("Stockfish White promotes a Pawn to a Queen");
  });

  it("formats square detail with a capture-specific separator", () => {
    expect(formatMoveDetail(knightMove)).toBe("g1 → f3 · g1f3");
    expect(formatMoveDetail({ ...knightMove, capture: true })).toBe(
      "g1 × f3 · g1f3",
    );
  });

  it("returns color-specific symbols and accessible labels", () => {
    expect(getPieceSymbol("KNIGHT", "WHITE")).toBe("♘");
    expect(getPieceSymbol("KNIGHT", "BLACK")).toBe("♞");
    expect(getPieceAccessibleLabel("KNIGHT", "BLACK")).toBe("Black Knight");
    expect(getOpponentColor("WHITE")).toBe("BLACK");
  });
});
