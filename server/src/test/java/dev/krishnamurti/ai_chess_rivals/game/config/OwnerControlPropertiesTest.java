package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class OwnerControlPropertiesTest {

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
              "app.match.cooldown=60s",
              "app.match.daily-start-limit=12");

  @Test
  void bindsNonBlankOwnerToken() {
    contextRunner
        .withPropertyValues("app.owner.control-token=test-owner-token")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(OwnerControlProperties.class).controlToken())
                  .isEqualTo("test-owner-token");
            });
  }

  @Test
  void rejectsMissingOwnerToken() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsBlankOwnerToken() {
    contextRunner
        .withPropertyValues("app.owner.control-token=   ")
        .run(context -> assertThat(context).hasFailed());
  }
}
