package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.Move;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;

public record MoveResponse(
    int sequenceNumber,
    PlayerColor player,
    String notation,
    String fenAfterMove,
    ChessPieceType movingPiece,
    PlayerColor movingPieceColor,
    String sourceSquare,
    String destinationSquare,
    ChessPieceType capturedPiece,
    PlayerColor capturedPieceColor,
    ChessPieceType promotedPiece,
    CastlingSide castlingSide,
    boolean capture,
    boolean check,
    boolean checkmate,
    boolean promotion) {

  public static MoveResponse from(Move move) {
    return new MoveResponse(
        move.sequenceNumber(),
        move.playedBy(),
        move.notation().value(),
        move.positionAfterMove().fen(),
        move.details().movingPiece(),
        move.details().movingPieceColor(),
        move.details().sourceSquare(),
        move.details().destinationSquare(),
        move.details().capturedPiece(),
        move.details().capturedPieceColor(),
        move.details().promotedPiece(),
        move.details().castlingSide(),
        move.capture(),
        move.details().check(),
        move.details().checkmate(),
        move.promotion());
  }
}
