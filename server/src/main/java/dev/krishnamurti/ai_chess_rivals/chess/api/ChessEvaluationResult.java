package dev.krishnamurti.ai_chess_rivals.chess.api;

import java.util.Objects;
import java.util.Optional;

/** Returns a chess-position evaluation and optional mover-perspective swing. */
public record ChessEvaluationResult(
    long correlationId,
    int ply,
    String fen,
    Optional<PositionEvaluation> evaluation,
    Optional<EvaluationSwing> swing) {

  public ChessEvaluationResult {
    if (correlationId <= 0) {
      throw new IllegalArgumentException("correlationId must be positive");
    }
    if (ply < 0) {
      throw new IllegalArgumentException("ply must not be negative");
    }
    if (fen == null || fen.isBlank()) {
      throw new IllegalArgumentException("fen must not be blank");
    }
    Objects.requireNonNull(evaluation, "evaluation must not be null");
    Objects.requireNonNull(swing, "swing must not be null");
  }

  public static ChessEvaluationResult unavailable(ChessEvaluationRequested request) {
    Objects.requireNonNull(request, "request must not be null");
    return new ChessEvaluationResult(
        request.correlationId(), request.ply(), request.fen(), Optional.empty(), Optional.empty());
  }
}
