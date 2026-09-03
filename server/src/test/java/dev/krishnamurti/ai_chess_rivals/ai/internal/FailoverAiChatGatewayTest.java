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
import org.slf4j.MDC;
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

  private FailoverAiChatGateway gateway(
      ProviderChatClient primary, ProviderChatClient remoteFallback) {
    return new FailoverAiChatGateway(primary, remoteFallback, new AiGatewayMetrics(meterRegistry));
  }

  @Test
  void returnsPrimaryResultWithoutCallingRemoteFallback() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(
            returning("primary response", primaryCalls),
            returning("fallback response", remoteFallbackCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("primary response");
    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_PRIMARY);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.RESPONSES)
                .tags("source", "primary", "reason", "primary")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.PROVIDER_DURATION)
                .tags("provider", "primary", "outcome", "success")
                .timer()
                .count())
        .isEqualTo(1L);
  }

  @Test
  void primaryFailureInvokesRemoteFallbackExactlyOnce() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(failing(primaryCalls), returning("fallback response", remoteFallbackCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("fallback response");
    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "remote_fallback", "reason", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.RESPONSES)
                .tags("source", "remote_fallback", "reason", "fallback")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void invalidPrimaryResultInvokesRemoteFallbackExactlyOnce() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(returning("invalid", primaryCalls), returning("valid", remoteFallbackCalls));
    AiResponseValidator validator = "valid"::equals;

    AiChatResult result = gateway.generate(REQUEST, validator);

    assertThat(result.content()).isEqualTo("valid");
    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "remote_fallback", "reason", "validation_failure")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void validatorExceptionAlsoFallsThroughToRemoteFallback() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    AtomicInteger validations = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(
            returning("primary response", primaryCalls),
            returning("fallback response", remoteFallbackCalls));
    AiResponseValidator validator =
        response -> {
          if (validations.getAndIncrement() == 0) {
            throw new IllegalArgumentException("malformed response");
          }
          return true;
        };

    AiChatResult result = gateway.generate(REQUEST, validator);

    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
  }

  @Test
  void bothRemoteAttemptsFailReturnsDeterministicFallback() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    FailoverAiChatGateway gateway = gateway(failing(primaryCalls), failing(remoteFallbackCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("deterministic fallback");
    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "remote_fallback", "reason", "failure")
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
  void invalidRemoteFallbackResultReturnsDeterministicFallback() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(returning("", primaryCalls), returning("", remoteFallbackCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
  }

  @Test
  void primaryTimeoutIsObservableAndFallsBackToRemoteFallback() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        gateway(timingOut(primaryCalls), returning("fallback response", remoteFallbackCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.PROVIDER_DURATION)
                .tags("provider", "primary", "outcome", "timeout")
                .timer()
                .count())
        .isEqualTo(1L);
    assertThat(
            meterRegistry
                .get(AiGatewayMetrics.FALLBACK_ACTIVATIONS)
                .tags("target", "remote_fallback", "reason", "timeout")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void malformedPrimaryStructuredOutputFallsThroughToValidRemoteFallback() {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    BeanOutputConverter<StructuredDialogue> converter =
        new BeanOutputConverter<>(StructuredDialogue.class);
    FailoverAiChatGateway gateway =
        gateway(
            returning("not-json", primaryCalls),
            returning(
                "{\"text\":\"A measured reply.\",\"emotion\":\"CALM\",\"reactionType\":\"MOVE_REACTION\"}",
                remoteFallbackCalls));

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
    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_FALLBACK);
    assertThat(primaryCalls).hasValue(1);
    assertThat(remoteFallbackCalls).hasValue(1);
  }

  @Test
  void providerLogsContainOnlySafeMetadata(CapturedOutput output) {
    AtomicInteger primaryCalls = new AtomicInteger();
    AtomicInteger remoteFallbackCalls = new AtomicInteger();
    AiChatRequest request =
        new AiChatRequest("SECRET_PROMPT_DO_NOT_LOG", "SECRET_FALLBACK_DO_NOT_LOG");
    FailoverAiChatGateway gateway =
        gateway(
            returning("SECRET_RAW_RESPONSE_DO_NOT_LOG", primaryCalls),
            failing(remoteFallbackCalls));

    AiChatResult result;
    try (MDC.MDCCloseable ignoredMatch = MDC.putCloseable("matchId", "match-123");
        MDC.MDCCloseable ignoredTrigger = MDC.putCloseable("triggerType", "MOVE");
        MDC.MDCCloseable ignoredPly = MDC.putCloseable("triggerPly", "7")) {
      result = gateway.generate(request, response -> false);
    }

    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(output.getAll())
        .contains("provider=primary")
        .contains("outcome=validation_failure")
        .contains("target=remote_fallback")
        .contains("source=deterministic_fallback")
        .contains("matchId=match-123")
        .contains("triggerType=MOVE")
        .contains("triggerPly=7")
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
