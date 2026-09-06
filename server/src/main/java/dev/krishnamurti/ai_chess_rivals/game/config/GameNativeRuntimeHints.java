package dev.krishnamurti.ai_chess_rivals.game.config;

import java.util.List;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;

/**
 * Registers reflection hints required for validated game configuration properties in native mode.
 */
public class GameNativeRuntimeHints implements RuntimeHintsRegistrar {

  private static final List<String> WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES =
      List.of(
          "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStreamMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStateMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.NoMatchMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStartedMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.MovePlayedMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.DialoguePlayedMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStoppedMessage",
          "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchFinishedMessage");

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

    // Jackson 3 introspects Java record components while serializing WebSocket messages in native
    // mode.
    registerWebSocketSerializationHints(hints, classLoader);

    // Register remaining data models for JSON serialization in Native Image.
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
            dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType.class,
            MemberCategory.INVOKE_DECLARED_METHODS);

    hints
        .reflection()
        .registerType(
            dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide.class,
            MemberCategory.INVOKE_DECLARED_METHODS);
  }

  private static void registerWebSocketSerializationHints(
      RuntimeHints hints, ClassLoader classLoader) {
    BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();

    for (String typeName : WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES) {
      Class<?> type = ClassUtils.resolveClassName(typeName, classLoader);
      bindingRegistrar.registerReflectionHints(hints.reflection(), type);
    }
  }
}
