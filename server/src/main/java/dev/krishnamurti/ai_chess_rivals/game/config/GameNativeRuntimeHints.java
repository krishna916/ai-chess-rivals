package dev.krishnamurti.ai_chess_rivals.game.config;

import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers reflection hints required for validated game configuration properties in native mode.
 */
public class GameNativeRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    // Hibernate Validator reflectively reads configuration-record backing fields and invokes
    // application @AssertTrue methods while evaluating Jakarta Bean Validation constraints.
    hints.reflection().registerType(GameProperties.class, MemberCategory.ACCESS_DECLARED_FIELDS);

    hints
        .reflection()
        .registerType(
            GameProperties.MoveDelay.class,
            typeHint ->
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod("isMinimumNonNegative", List.of(), ExecutableMode.INVOKE)
                    .withMethod("isMaximumNonNegative", List.of(), ExecutableMode.INVOKE)
                    .withMethod("isValidRange", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            MatchGuardProperties.class,
            typeHint ->
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod("isCooldownNonNegative", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(OwnerControlProperties.class, MemberCategory.ACCESS_DECLARED_FIELDS);

    hints
        .reflection()
        .registerTypeIfPresent(
            classLoader,
            "dev.krishnamurti.ai_chess_rivals.game.websocket.WebSocketProperties",
            typeHint -> typeHint.withMembers(MemberCategory.ACCESS_DECLARED_FIELDS));

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

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.websocket.MovePlayedMessage.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType.class,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide.class,
            MemberCategory.INVOKE_DECLARED_METHODS);
  }
}
