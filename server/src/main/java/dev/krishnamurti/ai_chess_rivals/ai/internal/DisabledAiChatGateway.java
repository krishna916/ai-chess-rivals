package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import java.util.Objects;

final class DisabledAiChatGateway implements AiChatGateway {

  private final AiGatewayMetrics metrics;

  DisabledAiChatGateway(AiGatewayMetrics metrics) {
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  @Override
  public AiChatResult generate(AiChatRequest request, AiResponseValidator validator) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(validator, "validator must not be null");
    metrics.responseSelected(AiResponseSource.DETERMINISTIC_FALLBACK, "ai_disabled");
    return new AiChatResult(
        request.deterministicFallback(), AiResponseSource.DETERMINISTIC_FALLBACK);
  }
}
