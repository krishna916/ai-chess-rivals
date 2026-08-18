package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.MatchRivalry;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.Objects;
import java.util.UUID;

public record MatchStarted(
    UUID matchId, PlayerColor sideToMove, BoardPosition position, MatchRivalry rivalry)
    implements MatchEvent {

  public MatchStarted {
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(rivalry, "rivalry must not be null");
  }
}
