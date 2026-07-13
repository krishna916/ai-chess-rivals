package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveDetails;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.Objects;

public record MovePlayed(
    int ply, PlayerColor player, MoveNotation notation, BoardPosition position, MoveDetails details)
    implements MatchEvent {

  public MovePlayed {
    if (ply <= 0) {
      throw new IllegalArgumentException("ply must be positive");
    }
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(notation, "notation must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(details, "details must not be null");
  }

  public ChessPieceType movingPiece() {
    return details.movingPiece();
  }

  public PlayerColor movingPieceColor() {
    return details.movingPieceColor();
  }

  public String sourceSquare() {
    return details.sourceSquare();
  }

  public String destinationSquare() {
    return details.destinationSquare();
  }

  public ChessPieceType capturedPiece() {
    return details.capturedPiece();
  }

  public PlayerColor capturedPieceColor() {
    return details.capturedPieceColor();
  }

  public ChessPieceType promotedPiece() {
    return details.promotedPiece();
  }

  public CastlingSide castlingSide() {
    return details.castlingSide();
  }

  public boolean capture() {
    return details.capture();
  }

  public boolean check() {
    return details.check();
  }

  public boolean checkmate() {
    return details.checkmate();
  }

  public boolean promotion() {
    return details.promotion();
  }
}
