package dev.krishnamurti.ai_chess_rivals.chess.api;

import java.util.Objects;

public record EvaluationSwing(
    long beforeCentipawns,
    long afterCentipawns,
    long swingCentipawns,
    EvaluationSwingClassification classification) {

  public EvaluationSwing {
    Objects.requireNonNull(classification, "classification must not be null");
  }
}
