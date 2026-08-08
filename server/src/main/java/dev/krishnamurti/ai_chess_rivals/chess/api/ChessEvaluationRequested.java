package dev.krishnamurti.ai_chess_rivals.chess.api;

import java.util.Objects;
import java.util.Optional;

/** Requests a chess-position evaluation from the chess module. */
public record ChessEvaluationRequested(
    long correlationId, int ply, String fen, Optional<PositionEvaluation> before) {

  public ChessEvaluationRequested {
    if (correlationId <= 0) {
      throw new IllegalArgumentException("correlationId must be positive");
    }
    if (ply < 0) {
      throw new IllegalArgumentException("ply must not be negative");
    }
    if (fen == null || fen.isBlank()) {
      throw new IllegalArgumentException("fen must not be blank");
    }
    Objects.requireNonNull(before, "before must not be null");
  }
}
