package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import java.util.Objects;

public record MatchFinished(GameResult result, BoardPosition finalPosition, int totalPlies)
    implements MatchEvent {

  public MatchFinished {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(finalPosition, "finalPosition must not be null");
    if (totalPlies < 0) {
      throw new IllegalArgumentException("totalPlies must not be negative");
    }
  }
}
