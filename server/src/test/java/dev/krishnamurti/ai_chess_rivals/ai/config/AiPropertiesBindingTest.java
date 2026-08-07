package dev.krishnamurti.ai_chess_rivals.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

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
          .withUserConfiguration(AiConfig.class)
          .withPropertyValues(
              "app.ai.groq.base-url=https://api.groq.com/openai/v1",
              "app.ai.groq.timeout=8s",
              "app.ai.gemini.timeout=12s");

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
            "app.ai.groq.api-key=test-groq-key",
            "app.ai.groq.model=test-groq-model",
            "app.ai.gemini.api-key=test-gemini-key",
            "app.ai.gemini.model=test-gemini-model")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AiProperties properties = context.getBean(AiProperties.class);
              assertThat(properties.groq().model()).isEqualTo("test-groq-model");
              assertThat(properties.groq().timeout()).isEqualTo(Duration.ofSeconds(8));
              assertThat(properties.gemini().model()).isEqualTo("test-gemini-model");
              assertThat(properties.gemini().timeout()).isEqualTo(Duration.ofSeconds(12));
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
        .withPropertyValues("app.ai.enabled=false", "app.ai.groq.timeout=0s")
        .run(context -> assertThat(context).hasFailed());
  }
}
