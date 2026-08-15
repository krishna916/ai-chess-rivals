package dev.krishnamurti.ai_chess_rivals.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly typed configuration for the bounded Phase 2 AI provider chain. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean enabled, @Valid @NotNull Groq groq, @Valid @NotNull Gemini gemini) {

  @AssertTrue(
      message = "When app.ai.enabled=true, Groq/Gemini API keys and model names must be configured")
  public boolean isEnabledConfigurationComplete() {
    return !enabled
        || (hasText(groq.apiKey())
            && hasText(groq.model())
            && hasText(groq.baseUrl())
            && hasText(gemini.apiKey())
            && hasText(gemini.model()));
  }

  public record Groq(String apiKey, String baseUrl, String model, @NotNull Duration timeout) {
    @AssertTrue(message = "app.ai.groq.timeout must be greater than zero")
    public boolean isTimeoutPositive() {
      return timeout == null || (!timeout.isNegative() && !timeout.isZero());
    }
  }

  public record Gemini(String apiKey, String model, @NotNull Duration timeout) {
    @AssertTrue(
        message =
            "app.ai.gemini.timeout must be between 1 and " + Integer.MAX_VALUE + " milliseconds")
    public boolean isTimeoutWithinHttpOptionsRange() {
      if (timeout == null) {
        return true;
      }

      try {
        long timeoutMillis = timeout.toMillis();
        return timeoutMillis >= 1 && timeoutMillis <= Integer.MAX_VALUE;
      } catch (ArithmeticException exception) {
        return false;
      }
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
