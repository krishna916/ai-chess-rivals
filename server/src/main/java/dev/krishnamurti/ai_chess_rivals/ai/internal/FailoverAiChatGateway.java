package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

final class FailoverAiChatGateway implements AiChatGateway {

  private static final Logger log = LoggerFactory.getLogger(FailoverAiChatGateway.class);

  private final ProviderChatClient primary;
  private final ProviderChatClient remoteFallback;
  private final AiGatewayMetrics metrics;

  FailoverAiChatGateway(
      ProviderChatClient primary, ProviderChatClient remoteFallback, AiGatewayMetrics metrics) {
    this.primary = Objects.requireNonNull(primary, "primary must not be null");
    this.remoteFallback = Objects.requireNonNull(remoteFallback, "remoteFallback must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  @Override
  public AiChatResult generate(AiChatRequest request, AiResponseValidator validator) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(validator, "validator must not be null");

    ProviderAttempt primaryAttempt = attempt("primary", primary, request.prompt(), validator);
    if (primaryAttempt.outcome() == ProviderOutcome.SUCCESS) {
      return selected(primaryAttempt.response(), AiResponseSource.REMOTE_PRIMARY, "primary");
    }

    activateFallback("remote_fallback", primaryAttempt.outcome());
    ProviderAttempt fallbackAttempt =
        attempt("remote_fallback", remoteFallback, request.prompt(), validator);
    if (fallbackAttempt.outcome() == ProviderOutcome.SUCCESS) {
      return selected(fallbackAttempt.response(), AiResponseSource.REMOTE_FALLBACK, "fallback");
    }

    activateFallback("deterministic_fallback", fallbackAttempt.outcome());
    return selected(
        request.deterministicFallback(),
        AiResponseSource.DETERMINISTIC_FALLBACK,
        "providers_exhausted");
  }

  private ProviderAttempt attempt(
      String provider, ProviderChatClient client, String prompt, AiResponseValidator validator) {
    long startedAt = System.nanoTime();
    String response = null;
    ProviderOutcome outcome;
    try {
      response = client.complete(prompt);
      outcome =
          validates(response, validator)
              ? ProviderOutcome.SUCCESS
              : ProviderOutcome.VALIDATION_FAILURE;
    } catch (RuntimeException exception) {
      outcome = isTimeout(exception) ? ProviderOutcome.TIMEOUT : ProviderOutcome.FAILURE;
    }

    Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
    metrics.providerAttempt(provider, outcome.tag(), duration);
    log.info(
        "AI provider attempt provider={} outcome={} durationMs={} matchId={} triggerType={} triggerPly={}",
        provider,
        outcome.tag(),
        duration.toMillis(),
        correlation("matchId"),
        correlation("triggerType"),
        correlation("triggerPly"));
    return new ProviderAttempt(response, outcome);
  }

  private static boolean validates(String response, AiResponseValidator validator) {
    if (response == null) {
      return false;
    }
    try {
      return validator.isValid(response);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private void activateFallback(String target, ProviderOutcome reason) {
    metrics.fallbackActivated(target, reason.tag());
    log.info(
        "AI fallback activated target={} reason={} matchId={} triggerType={} triggerPly={}",
        target,
        reason.tag(),
        correlation("matchId"),
        correlation("triggerType"),
        correlation("triggerPly"));
  }

  private AiChatResult selected(String content, AiResponseSource source, String reason) {
    metrics.responseSelected(source, reason);
    log.info(
        "AI response selected source={} reason={} matchId={} triggerType={} triggerPly={}",
        sourceTag(source),
        reason,
        correlation("matchId"),
        correlation("triggerType"),
        correlation("triggerPly"));
    return new AiChatResult(content, source);
  }

  private static boolean isTimeout(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current instanceof SocketTimeoutException
          || current instanceof HttpTimeoutException
          || current instanceof TimeoutException) {
        return true;
      }
    }
    return false;
  }

  private static String correlation(String key) {
    String value = MDC.get(key);
    return value == null ? "-" : value;
  }

  private static String sourceTag(AiResponseSource source) {
    return switch (source) {
      case REMOTE_PRIMARY -> "primary";
      case REMOTE_FALLBACK -> "remote_fallback";
      case DETERMINISTIC_FALLBACK -> "deterministic_fallback";
    };
  }

  private record ProviderAttempt(String response, ProviderOutcome outcome) {}

  private enum ProviderOutcome {
    SUCCESS("success"),
    FAILURE("failure"),
    TIMEOUT("timeout"),
    VALIDATION_FAILURE("validation_failure");

    private final String tag;

    ProviderOutcome(String tag) {
      this.tag = tag;
    }

    String tag() {
      return tag;
    }
  }
}
