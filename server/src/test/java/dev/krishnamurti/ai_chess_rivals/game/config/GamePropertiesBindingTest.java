package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class GamePropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(GameConfig.class)
          .withPropertyValues("app.game.move-think-time-millis=250", "app.game.max-plies=300");

  @Test
  void bindsDurationValues() {
    contextRunner
        .withPropertyValues("app.game.move-delay.min=500ms", "app.game.move-delay.max=3s")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              GameProperties properties = context.getBean(GameProperties.class);
              assertThat(properties.moveDelay().min()).isEqualTo(Duration.ofMillis(500));
              assertThat(properties.moveDelay().max()).isEqualTo(Duration.ofSeconds(3));
            });
  }

  @Test
  void rejectsNegativeMinimumAtStartup() {
    assertStartupFailure("app.game.move-delay.min=-1s", "app.game.move-delay.max=3s");
  }

  @Test
  void rejectsNegativeMaximumAtStartup() {
    assertStartupFailure("app.game.move-delay.min=0s", "app.game.move-delay.max=-1s");
  }

  @Test
  void rejectsReversedRangeAtStartup() {
    assertStartupFailure("app.game.move-delay.min=10s", "app.game.move-delay.max=3s");
  }

  @Test
  void rejectsMissingMoveDelayAtStartup() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  private void assertStartupFailure(String minimumProperty, String maximumProperty) {
    contextRunner
        .withPropertyValues(minimumProperty, maximumProperty)
        .run(context -> assertThat(context).hasFailed());
  }
}
