package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;

public record MatchSnapshot(Match match, boolean running) {
  public MatchSnapshot {
    if (match == null) {
      throw new NullPointerException("match must not be null");
    }
  }
}
