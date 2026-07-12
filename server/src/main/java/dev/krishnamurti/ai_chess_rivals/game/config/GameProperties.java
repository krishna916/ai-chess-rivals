package dev.krishnamurti.ai_chess_rivals.game.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Strongly-typed configuration properties for match engine orchestration. */
@ConfigurationProperties(prefix = "app.game")
@Validated
public record GameProperties(
    @Min(1) @Max(60_000) int moveThinkTimeMillis,
    @Min(1) @Max(1_000) int maxPlies,
    @Valid @NotNull MoveDelay moveDelay) {

  public record MoveDelay(@NotNull Duration min, @NotNull Duration max) {

    @AssertTrue(message = "app.game.move-delay.min must be non-negative")
    public boolean isMinimumNonNegative() {
      return min == null || !min.isNegative();
    }

    @AssertTrue(message = "app.game.move-delay.max must be non-negative")
    public boolean isMaximumNonNegative() {
      return max == null || !max.isNegative();
    }

    @AssertTrue(message = "app.game.move-delay.min must not exceed max")
    public boolean isValidRange() {
      return min == null
          || max == null
          || min.isNegative()
          || max.isNegative()
          || min.compareTo(max) <= 0;
    }
  }
}
