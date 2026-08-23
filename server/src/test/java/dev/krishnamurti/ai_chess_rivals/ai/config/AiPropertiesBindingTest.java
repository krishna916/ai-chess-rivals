package dev.krishnamurti.ai_chess_rivals.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class AiPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(ValidationConfiguration.class, AiConfig.class)
          .withPropertyValues(
              "app.ai.openrouter.base-url=https://openrouter.ai/api/v1",
              "app.ai.openrouter.primary-timeout=8s",
              "app.ai.openrouter.fallback-timeout=12s");

  @Test
  void disabledModeStartsWithoutProviderCredentials() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(AiProperties.class).enabled()).isFalse();
            });
  }

  @Test
  void enabledModeBindsProviderModelsAndTimeouts() {
    contextRunner
        .withPropertyValues(
            "app.ai.enabled=true",
            "app.ai.openrouter.api-key=test-openrouter-key",
            "app.ai.openrouter.primary-model=inclusionai/ling-3.0-flash:free",
            "app.ai.openrouter.fallback-model=~deepseek/deepseek-v4-flash-latest")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AiProperties properties = context.getBean(AiProperties.class);
              assertThat(properties.openrouter().primaryModel())
                  .isEqualTo("inclusionai/ling-3.0-flash:free");
              assertThat(properties.openrouter().primaryTimeout()).isEqualTo(Duration.ofSeconds(8));
              assertThat(properties.openrouter().fallbackModel())
                  .isEqualTo("~deepseek/deepseek-v4-flash-latest");
              assertThat(properties.openrouter().fallbackTimeout())
                  .isEqualTo(Duration.ofSeconds(12));
            });
  }

  @Test
  void enabledModeRejectsMissingCredentialsAndModels() {
    contextRunner
        .withPropertyValues("app.ai.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsNonPositiveProviderTimeout() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false", "app.ai.openrouter.primary-timeout=0s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsNonPositiveFallbackTimeout() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false", "app.ai.openrouter.fallback-timeout=-1s")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabledModeRejectsRandomOpenRouterFreeRoute() {
    contextRunner
        .withPropertyValues(
            "app.ai.enabled=true",
            "app.ai.openrouter.api-key=test-openrouter-key",
            "app.ai.openrouter.primary-model=openrouter/free",
            "app.ai.openrouter.fallback-model=~deepseek/deepseek-v4-flash-latest")
        .run(context -> assertThat(context).hasFailed());
  }
}
