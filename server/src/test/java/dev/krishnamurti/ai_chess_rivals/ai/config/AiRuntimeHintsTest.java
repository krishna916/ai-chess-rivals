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
}
