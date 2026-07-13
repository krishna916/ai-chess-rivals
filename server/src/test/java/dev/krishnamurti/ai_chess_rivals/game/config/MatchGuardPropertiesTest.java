package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class MatchGuardPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(GameConfig.class)
          .withPropertyValues(
              "app.game.move-think-time-millis=250",
              "app.game.max-plies=300",
              "app.game.move-delay.min=3s",
              "app.game.move-delay.max=10s",
              "app.owner.control-token=test-owner-token");

  @Test
  void bindsCooldownAndDailyLimit() {
    contextRunner
        .withPropertyValues("app.match.cooldown=90s", "app.match.daily-start-limit=5")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              MatchGuardProperties properties = context.getBean(MatchGuardProperties.class);
              assertThat(properties.cooldown()).isEqualTo(Duration.ofSeconds(90));
              assertThat(properties.dailyStartLimit()).isEqualTo(5);
            });
  }

  @Test
  void rejectsMissingCooldown() {
    contextRunner
        .withPropertyValues("app.match.daily-start-limit=12")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsNegativeCooldown() {
    contextRunner
        .withPropertyValues("app.match.cooldown=-1s", "app.match.daily-start-limit=12")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsZeroDailyLimit() {
    contextRunner
        .withPropertyValues("app.match.cooldown=60s", "app.match.daily-start-limit=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
