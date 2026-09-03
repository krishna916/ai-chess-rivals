package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;

final class AiGatewayMetrics {

  static final String PROVIDER_DURATION = "ai.gateway.provider.duration";
  static final String FALLBACK_ACTIVATIONS = "ai.gateway.fallback.activations";
  static final String RESPONSES = "ai.gateway.responses";

  private final MeterRegistry meterRegistry;

  AiGatewayMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
  }

  void providerAttempt(String provider, String outcome, Duration duration) {
    Timer.builder(PROVIDER_DURATION)
        .tag("provider", provider)
        .tag("outcome", outcome)
        .register(meterRegistry)
        .record(duration);
  }

  void fallbackActivated(String target, String reason) {
    Counter.builder(FALLBACK_ACTIVATIONS)
        .tag("target", target)
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();
  }

  void responseSelected(AiResponseSource source, String reason) {
    Counter.builder(RESPONSES)
        .tag("source", sourceTag(source))
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();
  }

  private static String sourceTag(AiResponseSource source) {
    return switch (source) {
      case REMOTE_PRIMARY -> "primary";
      case REMOTE_FALLBACK -> "remote_fallback";
      case DETERMINISTIC_FALLBACK -> "deterministic_fallback";
    };
  }
}
