package dev.krishnamurti.ai_chess_rivals.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class AiRuntimeHintsTest {

  @Test
  void registersAiPropertiesValidationMethodsForNativeInvocation() {
    RuntimeHints hints = new RuntimeHints();
    new AiRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AiProperties.class, "isEnabledConfigurationComplete")
                .test(hints))
        .isTrue();
    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AiProperties.OpenRouter.class, "areTimeoutsPositive")
                .test(hints))
        .isTrue();
  }

  @Test
  void registersAiPropertiesDeclaredFieldsForNativeAccess() {
    RuntimeHints hints = new RuntimeHints();
    new AiRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertFieldAccess(hints, AiProperties.class, "enabled");
    assertFieldAccess(hints, AiProperties.class, "openrouter");

    assertFieldAccess(hints, AiProperties.OpenRouter.class, "apiKey");
    assertFieldAccess(hints, AiProperties.OpenRouter.class, "baseUrl");
    assertFieldAccess(hints, AiProperties.OpenRouter.class, "primaryModel");
    assertFieldAccess(hints, AiProperties.OpenRouter.class, "fallbackModel");
    assertFieldAccess(hints, AiProperties.OpenRouter.class, "primaryTimeout");
    assertFieldAccess(hints, AiProperties.OpenRouter.class, "fallbackTimeout");
  }

  private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
    assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
        .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
        .isTrue();
  }
}
