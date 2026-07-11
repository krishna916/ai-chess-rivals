package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.Objects;

public record MovePlayed(
    int ply,
    PlayerColor player,
    MoveNotation notation,
    BoardPosition position,
    boolean capture,
    boolean check,
    boolean checkmate,
    boolean promotion)
    implements MatchEvent {

  public MovePlayed {
    if (ply <= 0) {
      throw new IllegalArgumentException("ply must be positive");
    }
    Objects.requireNonNull(player, "player must not be null");
    Objects.requireNonNull(notation, "notation must not be null");
    Objects.requireNonNull(position, "position must not be null");
  }
}
