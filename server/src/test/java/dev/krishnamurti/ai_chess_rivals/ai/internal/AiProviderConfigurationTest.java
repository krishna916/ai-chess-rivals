package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

class AiProviderConfigurationTest {

  @Test
  void openRouterOptionsUseSharedEndpointModelTimeoutAndNoRetries() {
    OpenAiChatOptions primary =
        AiProviderConfiguration.openRouterOptions(
            "test-openrouter-key",
            "https://openrouter.ai/api/v1",
            "inclusionai/ling-3.0-flash:free",
            Duration.ofSeconds(8));

    assertThat(primary.getApiKey()).isEqualTo("test-openrouter-key");
    assertThat(primary.getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1");
    assertThat(primary.getModel()).isEqualTo("inclusionai/ling-3.0-flash:free");
    assertThat(primary.getTimeout()).isEqualTo(Duration.ofSeconds(8));
    assertThat(primary.getMaxRetries()).isZero();

    OpenAiChatOptions fallback =
        AiProviderConfiguration.openRouterOptions(
            "test-openrouter-key",
            "https://openrouter.ai/api/v1",
            "~deepseek/deepseek-v4-flash-latest",
            Duration.ofSeconds(12));

    assertThat(fallback.getApiKey()).isEqualTo("test-openrouter-key");
    assertThat(fallback.getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1");
    assertThat(fallback.getModel()).isEqualTo("~deepseek/deepseek-v4-flash-latest");
    assertThat(fallback.getTimeout()).isEqualTo(Duration.ofSeconds(12));
    assertThat(fallback.getMaxRetries()).isZero();
  }
}
