package dev.krishnamurti.ai_chess_rivals.ai.config;

import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
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
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod(
                        "isEnabledConfigurationComplete", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            AiProperties.Groq.class,
            typeHint ->
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod("isTimeoutPositive", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            AiProperties.Gemini.class,
            typeHint ->
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod(
                        "isTimeoutWithinHttpOptionsRange", List.of(), ExecutableMode.INVOKE));
  }
}
