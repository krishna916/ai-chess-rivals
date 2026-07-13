package dev.krishnamurti.ai_chess_rivals.game.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** In-memory safety limits for the single showcase match. */
@ConfigurationProperties(prefix = "app.match")
@Validated
public record MatchGuardProperties(@NotNull Duration cooldown, @Min(1) int dailyStartLimit) {

  @AssertTrue(message = "app.match.cooldown must be non-negative")
  public boolean isCooldownNonNegative() {
    return cooldown == null || !cooldown.isNegative();
  }
}
