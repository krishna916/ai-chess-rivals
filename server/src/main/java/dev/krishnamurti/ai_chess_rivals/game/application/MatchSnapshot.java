package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;

public record MatchSnapshot(
    Match match, boolean running, MatchStartAvailability startAvailability) {
  public MatchSnapshot {
    if (match == null) {
      throw new NullPointerException("match must not be null");
    }
    if (startAvailability == null) {
      throw new NullPointerException("startAvailability must not be null");
    }
  }

  public MatchSnapshot(Match match, boolean running) {
    this(
        match,
        running,
        new MatchStartAvailability(
            !running,
            running ? MatchStartBlockReason.MATCH_ALREADY_RUNNING : null,
            0,
            0,
            Integer.MAX_VALUE));
  }
}
