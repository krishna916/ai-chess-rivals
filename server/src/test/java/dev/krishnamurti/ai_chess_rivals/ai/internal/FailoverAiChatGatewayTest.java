package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FailoverAiChatGatewayTest {

  private static final AiChatRequest REQUEST =
      new AiChatRequest("test prompt", "deterministic fallback");

  @Test
  void returnsGroqResultWithoutCallingGemini() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        new FailoverAiChatGateway(
            returning("groq response", groqCalls), returning("gemini response", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("groq response");
    assertThat(result.source()).isEqualTo(AiResponseSource.GROQ);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(0);
  }

  @Test
  void groqFailureInvokesGeminiExactlyOnce() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        new FailoverAiChatGateway(failing(groqCalls), returning("gemini response", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("gemini response");
    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

  @Test
  void invalidGroqResultInvokesGeminiExactlyOnce() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        new FailoverAiChatGateway(returning("invalid", groqCalls), returning("valid", geminiCalls));
    AiResponseValidator validator = "valid"::equals;

    AiChatResult result = gateway.generate(REQUEST, validator);

    assertThat(result.content()).isEqualTo("valid");
    assertThat(result.source()).isEqualTo(AiResponseSource.GEMINI);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

  @Test
  void validatorExceptionAlsoFallsThroughToGemini() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    AtomicInteger validations = new AtomicInteger();
    FailoverAiChatGateway gateway =
        new FailoverAiChatGateway(
            returning("groq response", groqCalls), returning("gemini response", geminiCalls));
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
    FailoverAiChatGateway gateway =
        new FailoverAiChatGateway(failing(groqCalls), failing(geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.content()).isEqualTo("deterministic fallback");
    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

  @Test
  void invalidGeminiResultReturnsDeterministicFallback() {
    AtomicInteger groqCalls = new AtomicInteger();
    AtomicInteger geminiCalls = new AtomicInteger();
    FailoverAiChatGateway gateway =
        new FailoverAiChatGateway(returning("", groqCalls), returning("", geminiCalls));

    AiChatResult result = gateway.generate(REQUEST, AiResponseValidator.nonBlank());

    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(groqCalls).hasValue(1);
    assertThat(geminiCalls).hasValue(1);
  }

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
}
