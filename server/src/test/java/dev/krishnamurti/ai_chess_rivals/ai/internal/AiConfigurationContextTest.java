package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import dev.krishnamurti.ai_chess_rivals.ai.config.AiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class AiConfigurationContextTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(
              AiConfig.class, AiProviderConfiguration.class, AiGatewayConfiguration.class)
          .withPropertyValues(
              "app.ai.groq.base-url=https://api.groq.com/openai/v1",
              "app.ai.groq.timeout=8s",
              "app.ai.gemini.timeout=12s");

  @Test
  void disabledModeCreatesOnlyFallbackGateway() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
              assertThat(context.getBeansOfType(ChatClient.class)).isEmpty();
              assertThat(context).hasSingleBean(AiChatGateway.class);

              var result =
                  context
                      .getBean(AiChatGateway.class)
                      .generate(
                          new AiChatRequest("test prompt", "offline fallback"),
                          AiResponseValidator.nonBlank());
              assertThat(result.content()).isEqualTo("offline fallback");
              assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
            });
  }

  @Test
  void enabledModeCreatesBothNamedProviderModelsAndClientsAndOneGateway() {
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
              assertThat(context).hasBean("groqChatModel");
              assertThat(context).hasBean("groqChatClient");
              assertThat(context).hasBean("geminiChatModel");
              assertThat(context).hasBean("geminiChatClient");
              assertThat(context).hasSingleBean(AiChatGateway.class);
            });
  }
}
