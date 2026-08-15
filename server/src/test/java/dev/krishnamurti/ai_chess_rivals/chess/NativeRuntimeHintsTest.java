package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class NativeRuntimeHintsTest {

  @Test
  void registersAllChessConfigurationFieldsForNativeValidation() {
    RuntimeHints hints = new RuntimeHints();
    new NativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertFieldAccess(hints, ChessProperties.class, "stockfish");

    assertFieldAccess(hints, ChessProperties.Stockfish.class, "path");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "threads");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "hashMb");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "startupTimeoutSeconds");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "moveTimeoutSeconds");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "evaluation");

    assertFieldAccess(hints, ChessProperties.Stockfish.Evaluation.class, "depth");
    assertFieldAccess(hints, ChessProperties.Stockfish.Evaluation.class, "moveTimeMillis");
    assertFieldAccess(
        hints, ChessProperties.Stockfish.Evaluation.class, "majorGainThresholdCentipawns");
    assertFieldAccess(
        hints, ChessProperties.Stockfish.Evaluation.class, "majorMistakeThresholdCentipawns");
  }

  private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
    assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
        .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
        .isTrue();
  }
}
