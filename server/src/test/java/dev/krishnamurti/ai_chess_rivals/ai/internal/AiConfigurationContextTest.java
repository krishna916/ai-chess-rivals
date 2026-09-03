package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import dev.krishnamurti.ai_chess_rivals.ai.config.AiConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

@ExtendWith(OutputCaptureExtension.class)
class AiConfigurationContextTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(
              ValidationConfiguration.class,
              AiConfig.class,
              AiProviderConfiguration.class,
              AiGatewayConfiguration.class)
          .withBean(ObservationRegistry.class, ObservationRegistry::create)
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withPropertyValues(
              "app.ai.openrouter.base-url=https://openrouter.ai/api/v1",
              "app.ai.openrouter.primary-timeout=8s",
              "app.ai.openrouter.fallback-timeout=12s");

  @Test
  void disabledModeCreatesOnlyFallbackGateway(CapturedOutput output) {
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
              MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
              assertThat(
                      meterRegistry
                          .get(AiGatewayMetrics.RESPONSES)
                          .tags("source", "deterministic_fallback", "reason", "ai_disabled")
                          .counter()
                          .count())
                  .isEqualTo(1.0);
              assertThat(output.getAll())
                  .contains("AI gateway topology: disabled")
                  .doesNotContain(
                      "AI gateway topology: enabled (OpenRouter primary -> OpenRouter fallback -> deterministic fallback)");
            });
  }

  @Test
  void enabledModeCreatesBothNamedProviderModelsAndClientsAndOneGateway(CapturedOutput output) {
    contextRunner
        .withPropertyValues(
            "app.ai.enabled=true",
            "app.ai.openrouter.api-key=test-openrouter-key",
            "app.ai.openrouter.primary-model=inclusionai/ling-3.0-flash:free",
            "app.ai.openrouter.fallback-model=~deepseek/deepseek-v4-flash-latest")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasBean("openRouterPrimaryChatModel");
              assertThat(context).hasBean("openRouterPrimaryChatClient");
              assertThat(context).hasBean("openRouterFallbackChatModel");
              assertThat(context).hasBean("openRouterFallbackChatClient");
              assertThat(context).hasSingleBean(AiChatGateway.class);
              assertThat(context).hasSingleBean(AiGatewayMetrics.class);
              assertThat(output.getAll())
                  .contains(
                      "AI gateway topology: enabled (OpenRouter primary -> OpenRouter fallback -> deterministic fallback)")
                  .doesNotContain("AI gateway topology: disabled");
            });
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
