package dev.krishnamurti.ai_chess_rivals.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed configuration for the bounded Phase 2 AI provider chain. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(boolean enabled, @Valid @NotNull OpenRouter openrouter) {

  @AssertTrue(
      message =
          "When app.ai.enabled=true, OpenRouter API key/base URL, a specific :free primary model (not openrouter/free), and fallback model must be configured")
  public boolean isEnabledConfigurationComplete() {
    if (!enabled) {
      return true;
    }
    if (openrouter == null) {
      return false;
    }
    return hasText(openrouter.apiKey())
        && hasText(openrouter.baseUrl())
        && isSpecificFreeModel(openrouter.primaryModel())
        && hasText(openrouter.fallbackModel());
  }

  public record OpenRouter(
      String apiKey,
      String baseUrl,
      String primaryModel,
      String fallbackModel,
      @NotNull Duration primaryTimeout,
      @NotNull Duration fallbackTimeout) {

    @AssertTrue(
        message =
            "app.ai.openrouter.primary-timeout and app.ai.openrouter.fallback-timeout must be greater than zero")
    public boolean areTimeoutsPositive() {
      return isPositive(primaryTimeout) && isPositive(fallbackTimeout);
    }

    private static boolean isPositive(Duration value) {
      return value == null || (!value.isNegative() && !value.isZero());
    }
  }

  private static boolean isSpecificFreeModel(String model) {
    return hasText(model) && model.endsWith(":free") && !"openrouter/free".equals(model);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
