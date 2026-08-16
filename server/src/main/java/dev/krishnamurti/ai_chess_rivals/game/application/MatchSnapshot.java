package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.List;
import java.util.Objects;

public record MatchSnapshot(
    Match match,
    boolean running,
    MatchStartAvailability startAvailability,
    List<PersistedDialogue> dialogue) {
  public MatchSnapshot {
    Objects.requireNonNull(match, "match must not be null");
    Objects.requireNonNull(startAvailability, "startAvailability must not be null");
    dialogue = dialogue != null ? List.copyOf(dialogue) : List.of();
  }

  public MatchSnapshot(Match match, boolean running, MatchStartAvailability startAvailability) {
    this(match, running, startAvailability, List.of());
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
            Integer.MAX_VALUE),
        List.of());
  }
}
