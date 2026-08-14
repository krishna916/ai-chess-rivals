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
                .onMethodInvocation(AiProperties.Groq.class, "isTimeoutPositive")
                .test(hints))
        .isTrue();
    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AiProperties.Gemini.class, "isTimeoutWithinHttpOptionsRange")
                .test(hints))
        .isTrue();
  }

  @Test
  void registersAiPropertiesDeclaredFieldsForNativeAccess() {
    RuntimeHints hints = new RuntimeHints();
    new AiRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertFieldAccess(hints, AiProperties.class, "enabled");
    assertFieldAccess(hints, AiProperties.class, "groq");
    assertFieldAccess(hints, AiProperties.class, "gemini");

    assertFieldAccess(hints, AiProperties.Groq.class, "apiKey");
    assertFieldAccess(hints, AiProperties.Groq.class, "baseUrl");
    assertFieldAccess(hints, AiProperties.Groq.class, "model");
    assertFieldAccess(hints, AiProperties.Groq.class, "timeout");

    assertFieldAccess(hints, AiProperties.Gemini.class, "apiKey");
    assertFieldAccess(hints, AiProperties.Gemini.class, "model");
    assertFieldAccess(hints, AiProperties.Gemini.class, "timeout");
  }

  private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
    assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
        .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
        .isTrue();
  }
}
