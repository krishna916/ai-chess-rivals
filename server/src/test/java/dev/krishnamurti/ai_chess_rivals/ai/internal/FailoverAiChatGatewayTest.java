package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class FailoverAiChatGatewayTest {

  private static final AiChatRequest REQUEST =
      new AiChatRequest("test prompt", "deterministic fallback");
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUpMetrics() {
    meterRegistry = new SimpleMeterRegistry();
  }

  private FailoverAiChatGateway gateway(ProviderChatClient groq, ProviderChatClient gemini) {
    return new FailoverAiChatGateway(groq, gemini, new AiGatewayMetrics(meterRegistry));
  }

  @Test
  void returnsGroqResultWithoutCallingGemini() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(returning("groq response", groqCalls), returning("gemini response", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("groq response");
    assertThat(result.source()).isEqualTo(AiResponseSource.GROQ);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.RESPONSES)
                .tags("source", "groq", "reason", "primary")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.PROVIDER_DURATION)
                .tags("provider", "groq", "outcome", "success")
                .timer()
                .count())
        .isEqualTo(1L);
  }

  @Test
  void groqFailureInvokesGeminiExactlyOnce() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(failing(groqCalls), returning("gemini response", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("gemini response");
    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "gemini", "reason", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.RESPONSES)
                .tags("source", "gemini", "reason", "fallback")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void invalidGroqResultInvokesGeminiExactlyOnce() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(returning("invalid", groqCalls), returning("valid", geminiCalls));
    AiResponseValidator validator = "valid"::equals;

    AiChatResult result = gateway.generate(REQUEST, validator);

    assertThat(result.content()).isEqualTo("valid");
    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "gemini", "reason", "validation_failure")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void validatorExceptionAlsoFallsThroughToGemini() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    AtomicInteger validations = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(returning("groq response", groqCalls), returning("gemini response", geminiCalls));
    AiResponseValidator validator =
        response -> {
          if (validations.getAndIncrement() == 0) {
            throw new IllegalArgumentException("malformed response");
          }
          return true;
        };

    AiChatResult result = gateway.generate(REQUEST, validator);

    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

  @Test
  void bothProvidersFailReturnsDeterministicFallback() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway = gateway(failing(groqCalls), failing(geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("deterministic fallback");
    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "gemini", "reason", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "deterministic_fallback", "reason", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.RESPONSES)
                .tags("source", "deterministic_fallback", "reason", "providers_exhausted")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void invalidGeminiResultReturnsDeterministicFallback() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway = gateway(returning("", groqCalls), returning("", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

  @Test
  void groqTimeoutIsObservableAndFallsBackToGemini() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(timingOut(groqCalls), returning("gemini response", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.PROVIDER_DURATION)
                .tags("provider", "groq", "outcome", "timeout")
                .timer()
                .count())
        .isEqualTo(1L);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "gemini", "reason", "timeout")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void malformedGroqStructuredOutputFallsThroughToValidGemini() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    BeanOutputConverter<StructuredDialogue> converter =
        new BeanOutputConverter<>(StructuredDialogue.class);
    FailoverAiChatGateway gateway =
        gateway(
            returning("not-json", groqCalls),
            returning(
                "{\"text\":\"A measured reply.\",\"emotion\":\"CALM\",\"reactionType\":\"MOVE_REACTION\"}",
                geminiCalls));

    AiChatResult result =
        gateway.generate(
            REQUEST,
            response -> {
              try {
                StructuredDialogue dialogue = converter.convert(response);
                return dialogue != null
                    && !dialogue.text().isBlank()
                    && dialogue.emotion() == StructuredEmotion.CALM
                    && dialogue.reactionType() == StructuredReactionType.MOVE_REACTION;
              } catch (RuntimeException ignored) {
                return false;
              }
            });

    assertThat(result.content()).contains("A measured reply.");
    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

  @Test
  void providerLogsContainOnlySafeMetadata(CapturedOutput output) {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    AiChatRequest request =
        new AiChatRequest("SECRET_PROMPT_DO_NOT_LOG", "SECRET_FALLBACK_DO_NOT_LOG");
    FailoverAiChatGateway gateway =
        gateway(returning("SECRET_RAW_RESPONSE_DO_NOT_LOG", groqCalls), failing(geminiCalls));

    AiChatResult result = gateway.generate(request, response -> false);

    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(output.getAll())
        .contains("provider=groq")
        .contains("outcome=validation_failure")
        .contains("target=gemini")
        .contains("source=deterministic_fallback")
        .doesNotContain("SECRET_PROMPT_DO_NOT_LOG")
        .doesNotContain("SECRET_RAW_RESPONSE_DO_NOT_LOG")
        .doesNotContain("SECRET_FALLBACK_DO_NOT_LOG");
  }

  public enum StructuredEmotion {
    CALM
  }

  public enum StructuredReactionType {
    MOVE_REACTION
  }

  public record StructuredDialogue(
      String text, StructuredEmotion emotion, StructuredReactionType reactionType) {}

  private static ProviderChatClient returning(String response, AtomicInteger calls) {
    return prompt -> {
      calls.incrementAndGet();
      return response;
    };
  }

  private static ProviderChatClient failing(AtomicInteger calls) {
    return prompt -> {
      calls.incrementAndGet();
      throw new IllegalStateException("provider unavailable");
    };
  }

  private static ProviderChatClient timingOut(AtomicInteger calls) {
    return prompt -> {
      calls.incrementAndGet();
      throw new IllegalStateException(new SocketTimeoutException("read timed out"));
    };
  }
}
