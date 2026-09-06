package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.game.web.DialogueResponse;
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class GameNativeRuntimeHintsTest {

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

  @Test
  void registersAllGameConfigurationFieldsForNativeValidation() throws ClassNotFoundException {
    RuntimeHints hints = new RuntimeHints();
    new GameNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertFieldAccess(hints, GameProperties.class, "moveThinkTimeMillis");
    assertFieldAccess(hints, GameProperties.class, "maxPlies");
    assertFieldAccess(hints, GameProperties.class, "moveDelay");

    assertFieldAccess(hints, GameProperties.MoveDelay.class, "min");
    assertFieldAccess(hints, GameProperties.MoveDelay.class, "max");

    assertFieldAccess(hints, MatchGuardProperties.class, "cooldown");
    assertFieldAccess(hints, MatchGuardProperties.class, "dailyStartLimit");

    assertFieldAccess(hints, OwnerControlProperties.class, "controlToken");

    Class<?> webSocketProperties =
        Class.forName(
            "dev.krishnamurti.ai_chess_rivals.game.websocket.WebSocketProperties",
            false,
            getClass().getClassLoader());
    assertFieldAccess(hints, webSocketProperties, "allowedOrigins");
  }

  @Test
  void registersGameAssertTrueMethodsForNativeInvocation() {
    RuntimeHints hints = new RuntimeHints();
    new GameNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertMethodInvocation(hints, GameProperties.MoveDelay.class, "isMinimumNonNegative");
    assertMethodInvocation(hints, GameProperties.MoveDelay.class, "isMaximumNonNegative");
    assertMethodInvocation(hints, GameProperties.MoveDelay.class, "isValidRange");
    assertMethodInvocation(hints, MatchGuardProperties.class, "isCooldownNonNegative");
  }

  @Test
  void registersWebSocketRecordBindingHintsForNativeSerialization() throws ClassNotFoundException {
    RuntimeHints hints = new RuntimeHints();
    ClassLoader classLoader = getClass().getClassLoader();
    new GameNativeRuntimeHints().registerHints(hints, classLoader);

    for (String typeName : WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES) {
      Class<?> type = Class.forName(typeName, false, classLoader);
      assertRecordBindingHints(hints, type);
    }

    // MatchStateMessage reaches these DTOs transitively through record components.
    assertRecordBindingHints(hints, MatchPersonalityResponse.class);
    assertRecordBindingHints(hints, DialogueResponse.class);
  }

  private static void assertRecordBindingHints(RuntimeHints hints, Class<?> type) {
    assertThat(type.isRecord()).as("expected %s to remain a Java record", type.getName()).isTrue();

    assertThat(RuntimeHintsPredicates.reflection().onType(type).test(hints))
        .as("expected native reflection registration for %s", type.getName())
        .isTrue();

    for (RecordComponent component : type.getRecordComponents()) {
      assertMethodInvocation(hints, type, component.getAccessor().getName());
    }
  }

  private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
    assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
        .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
        .isTrue();
  }

  private static void assertMethodInvocation(RuntimeHints hints, Class<?> type, String methodName) {
    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(type, methodName).test(hints))
        .as("expected native reflective invocation for %s.%s", type.getName(), methodName)
        .isTrue();
  }
}
