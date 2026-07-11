package dev.krishnamurti.ai_chess_rivals.game.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers reflection hints required for validated game configuration properties in native mode.
 */
public class GameNativeRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints.reflection().registerType(GameProperties.class, MemberCategory.DECLARED_FIELDS);
  }
}
