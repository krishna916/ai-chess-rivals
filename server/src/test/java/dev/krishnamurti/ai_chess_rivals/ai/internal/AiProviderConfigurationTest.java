package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.HttpOptions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;

class AiProviderConfigurationTest {

  @Test
  void groqOptionsUseConfiguredEndpointModelTimeoutAndNoRetries() {
    OpenAiChatOptions options =
        AiProviderConfiguration.groqOptions(
            "test-groq-key",
            "https://api.groq.com/openai/v1",
            "test-groq-model",
            Duration.ofSeconds(8));

    assertThat(options.getApiKey()).isEqualTo("test-groq-key");
    assertThat(options.getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
    assertThat(options.getModel()).isEqualTo("test-groq-model");
    assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(8));
    assertThat(options.getMaxRetries()).isZero();
  }

  @Test
  void geminiHttpOptionsUseTwelveSecondTimeoutAndSingleSdkAttempt() {
    HttpOptions options = AiProviderConfiguration.geminiHttpOptions(Duration.ofSeconds(12));

    assertThat(options.timeout()).contains(12_000);
    assertThat(options.retryOptions()).isPresent();
    assertThat(options.retryOptions().orElseThrow().attempts()).contains(1);
  }

  @Test
  void springRetryTemplateDoesNotRetryGeminiCall() {
    RetryTemplate retryTemplate = AiProviderConfiguration.noRetryTemplate();
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                retryTemplate.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("provider failure");
                    }))
        .isInstanceOf(RetryException.class);

    assertThat(attempts).hasValue(1);
  }
}
