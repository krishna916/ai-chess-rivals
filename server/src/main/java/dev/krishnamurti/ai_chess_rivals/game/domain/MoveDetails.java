package dev.krishnamurti.ai_chess_rivals.game.domain;

import java.util.Objects;

public record MoveDetails(
    ChessPieceType movingPiece,
    PlayerColor movingPieceColor,
    String sourceSquare,
    String destinationSquare,
    ChessPieceType capturedPiece,
    PlayerColor capturedPieceColor,
    ChessPieceType promotedPiece,
    CastlingSide castlingSide,
    boolean check,
    boolean checkmate) {

  public MoveDetails {
    Objects.requireNonNull(movingPiece, "movingPiece must not be null");
    Objects.requireNonNull(movingPieceColor, "movingPieceColor must not be null");
    Objects.requireNonNull(sourceSquare, "sourceSquare must not be null");
    Objects.requireNonNull(destinationSquare, "destinationSquare must not be null");
    if ((capturedPiece == null) != (capturedPieceColor == null)) {
      throw new IllegalArgumentException(
          "capturedPiece and capturedPieceColor must both be present or absent");
    }
  }

  public boolean capture() {
    return capturedPiece != null;
  }

  public boolean promotion() {
    return promotedPiece != null;
  }
}
