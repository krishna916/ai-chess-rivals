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
    hints.reflection().registerType(GameProperties.MoveDelay.class, MemberCategory.DECLARED_FIELDS);

    // Register Web endpoints & configs for Native Image since they were missed during AOT
    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.web.MatchController.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.web.MatchControllerAdvice.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.websocket.MatchWebSocketConfig.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.websocket.MatchWebSocketHandler.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);

    // Register Data models for JSON Serialization in Native Image
    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.web.MatchResponse.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.web.MoveResponse.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);
  }
}
