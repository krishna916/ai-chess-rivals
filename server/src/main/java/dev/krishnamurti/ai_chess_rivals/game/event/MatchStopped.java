package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.Objects;

public record MatchStopped(PlayerColor sideToMove, BoardPosition position, int totalPlies)
    implements MatchEvent {

  public MatchStopped {
    Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    Objects.requireNonNull(position, "position must not be null");
    if (totalPlies < 0) {
      throw new IllegalArgumentException("totalPlies must not be negative");
    }
  }
}
