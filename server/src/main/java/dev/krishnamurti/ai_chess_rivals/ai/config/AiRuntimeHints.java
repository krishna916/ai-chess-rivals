package dev.krishnamurti.ai_chess_rivals.ai.config;

import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class AiRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints
        .reflection()
        .registerType(
            AiProperties.class,
            typeHint ->
                typeHint.withMethod(
                    "isEnabledConfigurationComplete", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            AiProperties.Groq.class,
            typeHint -> typeHint.withMethod("isTimeoutPositive", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            AiProperties.Gemini.class,
            typeHint ->
                typeHint.withMethod(
                    "isTimeoutWithinHttpOptionsRange", List.of(), ExecutableMode.INVOKE));
  }
}
