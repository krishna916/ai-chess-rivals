package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.util.UUID;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    // Register UUID[] for reflection instantiation
    hints.reflection().registerType(UUID[].class);

    // Hibernate Validator reflectively reads configuration-record backing fields when evaluating
    // Jakarta Bean Validation constraints in the native runtime.
    hints.reflection().registerType(ChessProperties.class, MemberCategory.ACCESS_DECLARED_FIELDS);
    hints
        .reflection()
        .registerType(ChessProperties.Stockfish.class, MemberCategory.ACCESS_DECLARED_FIELDS);
    hints
        .reflection()
        .registerType(
            ChessProperties.Stockfish.Evaluation.class, MemberCategory.ACCESS_DECLARED_FIELDS);
  }
}
