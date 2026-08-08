package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import java.util.Objects;

final class FailoverAiChatGateway implements AiChatGateway {

  private final ProviderChatClient groq;
  private final ProviderChatClient gemini;

  FailoverAiChatGateway(ProviderChatClient groq, ProviderChatClient gemini) {
    this.groq = Objects.requireNonNull(groq, "groq must not be null");
    this.gemini = Objects.requireNonNull(gemini, "gemini must not be null");
  }

  @Override
  public AiChatResult generate(AiChatRequest request, AiResponseValidator validator) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(validator, "validator must not be null");

    String groqResponse = attempt(groq, request.prompt());
    if (isValid(groqResponse, validator)) {
      return new AiChatResult(groqResponse, AiResponseSource.GROQ);
    }

    String geminiResponse = attempt(gemini, request.prompt());
    if (isValid(geminiResponse, validator)) {
      return new AiChatResult(geminiResponse, AiResponseSource.GEMINI);
    }

    return new AiChatResult(
        request.deterministicFallback(), AiResponseSource.DETERMINISTIC_FALLBACK);
  }

  private static String attempt(ProviderChatClient provider, String prompt) {
    try {
      return provider.complete(prompt);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static boolean isValid(String response, AiResponseValidator validator) {
    if (response == null) {
      return false;
    }
    try {
      return validator.isValid(response);
    } catch (RuntimeException ignored) {
      return false;
    }
  }
}
