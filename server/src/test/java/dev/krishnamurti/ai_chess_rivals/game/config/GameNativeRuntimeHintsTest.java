package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class GameNativeRuntimeHintsTest {

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
