import type {
  ChessPieceType,
  MoveActivityItem,
  PlayerColor,
} from "@/types/match";
import { MATCH_PLAYERS } from "./matchPlayers";

type MatchPlayers = Record<PlayerColor, { id: PlayerColor; name: string }>;

const PIECE_SYMBOLS: Record<PlayerColor, Record<ChessPieceType, string>> = {
  WHITE: {
    KING: "♔",
    QUEEN: "♕",
    ROOK: "♖",
    BISHOP: "♗",
    KNIGHT: "♘",
    PAWN: "♙",
  },
  BLACK: {
    KING: "♚",
    QUEEN: "♛",
    ROOK: "♜",
    BISHOP: "♝",
    KNIGHT: "♞",
    PAWN: "♟",
  },
};

export function formatPieceName(piece: ChessPieceType): string {
  return piece.charAt(0) + piece.slice(1).toLowerCase();
}

export function getOpponentColor(player: PlayerColor): PlayerColor {
  return player === "WHITE" ? "BLACK" : "WHITE";
}

export function formatMoveDescription(
  activity: MoveActivityItem,
  players: MatchPlayers = MATCH_PLAYERS,
): string {
  const playerName = players[activity.player].name;
  const opponentColor =
    activity.capturedPieceColor ?? getOpponentColor(activity.player);
  const opponentName = players[opponentColor].name;
  const movingPiece = formatPieceName(activity.movingPiece);

  if (activity.castlingSide) {
    const side =
      activity.castlingSide === "KING_SIDE" ? "kingside" : "queenside";
    return `${playerName} castles ${side}`;
  }
  if (activity.promotedPiece) {
    return `${playerName} promotes a Pawn to a ${formatPieceName(activity.promotedPiece)}`;
  }
  if (activity.capture && activity.capturedPiece) {
    return `${playerName}'s ${movingPiece} captures ${opponentName}'s ${formatPieceName(activity.capturedPiece)}`;
  }
  return `${playerName} moves the ${movingPiece} to ${activity.destinationSquare}`;
}

export function formatMoveDetail(activity: MoveActivityItem): string {
  const separator = activity.capture ? "×" : "→";
  return `${activity.sourceSquare} ${separator} ${activity.destinationSquare} · ${activity.notation}`;
}

export function getPieceSymbol(
  piece: ChessPieceType,
  color: PlayerColor,
): string {
  return PIECE_SYMBOLS[color][piece];
}

export function getPieceAccessibleLabel(
  piece: ChessPieceType,
  color: PlayerColor,
): string {
  const colorName = color === "WHITE" ? "White" : "Black";
  return `${colorName} ${formatPieceName(piece)}`;
}
