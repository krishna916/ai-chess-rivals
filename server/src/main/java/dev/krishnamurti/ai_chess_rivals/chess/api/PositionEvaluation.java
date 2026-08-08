package dev.krishnamurti.ai_chess_rivals.chess.api;

import java.util.Objects;

public record PositionEvaluation(ScoreType type, int value) {

  public static final long MATE_COMPARABLE_CENTIPAWNS = 100_000L;

  public PositionEvaluation {
    Objects.requireNonNull(type, "type must not be null");
  }

  public long comparableCentipawns() {
    if (type == ScoreType.CENTIPAWNS) {
      return value;
    }
    return value > 0 ? MATE_COMPARABLE_CENTIPAWNS : -MATE_COMPARABLE_CENTIPAWNS;
  }

  public enum ScoreType {
    CENTIPAWNS,
    MATE
  }
}
