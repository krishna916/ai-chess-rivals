# Phase 2 AI Observability, Resilience, and Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Phase 2 AI generation inspectable and prove that provider failures, validation failures, timeouts, refresh/reconnect, and stop/resume cannot corrupt dialogue ordering or prevent a chess match from completing.

**Architecture:** Keep observability at the existing AI gateway boundary instead of introducing tracing infrastructure or a new abstraction layer. Wire the manually-created Spring AI chat models to Spring Boot's `ObservationRegistry` so Spring AI can emit its native model latency/token metrics, add three low-cardinality Micrometer metric families around `FailoverAiChatGateway` for application-level provider/fallback outcomes, and scope `matchId`/dialogue trigger correlation through MDC only while synchronous dialogue work runs. Reuse the existing deterministic gateway, match-engine, persistence, and frontend ordering tests rather than creating production-only testing hooks; use one real full-stack forced-failover scenario only in the documented manual acceptance run.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, Micrometer/Actuator already supplied by `spring-boot-starter-actuator`, JUnit 5, AssertJ, Mockito, React 19.2.7, TypeScript 6.0.2, Vitest 4.1.10.

**Spec:** `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`

## Source of Truth

- Issue: `#46 Phase 2: Add AI observability, resilience coverage, and acceptance verification`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Completed dependencies: `#38`, `#39`, `#43`, `#45`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Do not add a Maven dependency, npm dependency, tracing backend, dashboard, OpenTelemetry collector, or Prometheus-specific implementation for this issue.
- Do not add a new provider abstraction, workflow framework, database table, migration, REST endpoint, WebSocket message, or frontend production feature.
- Automated tests must make zero network calls to Groq, Gemini, or any paid provider.
- Keep Groq as the primary provider and Gemini as the only automatic provider fallback.
- Preserve the configured timeout boundaries: Groq `8s`, Gemini `12s`, and no same-provider retry.
- If both providers fail or produce invalid output, return the deterministic fallback and keep the match running.
- Never log prompts, raw model responses, deterministic fallback text, API keys, authorization headers, or other secrets.
- Keep `matchId`, `triggerType`, and `triggerPly` in logs only. Do not put match IDs, personality IDs, prompts, or response text in metric tags; metric tags must stay low-cardinality.
- Keep Spring AI prompt/completion/error-content observation logging explicitly disabled even though the framework defaults are safe.
- Use Spring AI's native `gen_ai.client.operation` / `gen_ai.client.token.usage` observations for model-call timing/token usage when the provider reports usage; do not duplicate token parsing in `ProviderChatClient`.
- Custom metrics for this issue are exactly:
  - `ai.gateway.provider.duration` with tags `provider=groq|gemini` and `outcome=success|failure|timeout|validation_failure`.
  - `ai.gateway.fallback.activations` with tags `target=gemini|deterministic_fallback` and `reason=failure|timeout|validation_failure`.
  - `ai.gateway.responses` with tags `source=groq|gemini|deterministic_fallback` and `reason=primary|fallback|providers_exhausted|ai_disabled`.
- `ai.gateway.provider.duration` is a `Timer`; the other two are `Counter`s.
- Do not introduce arbitrary sleeps in automated tests. Timeout behavior is represented by deterministic exceptions from provider test stubs.
- Do not widen package-private AI/game classes just to create an artificial cross-module integration test.
- Reuse the existing `MatchEngineTest#dialogueFailureDoesNotPreventMatchCompletion` as the engine-level proof that dialogue failure cannot kill chess execution.
- Reuse the #45 store/activity tests as the automated proof for live/hydrated ordering and deduplication; #46 does not rewrite that frontend logic.
- The manual acceptance record must contain only observations actually performed. Never pre-check an acceptance item.
- Apply Java formatting before backend verification.

## File Map

### Create

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java` — owns the three custom low-cardinality Micrometer metric families.

### Modify

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java` — classify provider outcomes, record duration/fallback/response metrics, and emit safe correlated provider logs.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/DisabledAiChatGateway.java` — record the intentional AI-disabled deterministic response path.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java` — create/inject `AiGatewayMetrics` into enabled and disabled gateways.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java` — pass the Boot `ObservationRegistry` into both manually-created Spring AI chat models.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java` — scope `matchId`, `triggerType`, and `triggerPly` through MDC while dialogue generation/persistence runs.
- `server/src/main/resources/application.yaml` — expose Actuator `metrics` and explicitly disable prompt/completion/error-content observation logging.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java` — keep the existing deterministic provider stubs and add metric, timeout, fallback-reason, and log-leakage assertions.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java` — supply test registries and verify disabled-mode metric behavior / enabled topology still starts without provider calls.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java` — verify match/event MDC exists during dialogue work and is cleared afterwards.
- `docs/BUILD_AND_VERIFY.md` — add Phase 2 credential-safe setup, offline fast mode, forced failover instructions, metrics/log inspection, manual checklist, and the dated observed acceptance record.

### Verify Without Production Changes

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java` — already locks Groq `8s`, Gemini `12s`, and no retries.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java` — existing generation/fallback behavior stays green.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java` — existing engine-level dialogue-failure completion and stop/resume ownership tests stay green.
- `client/src/store/matchViewerStore.test.ts` — existing live/hydration/reconnect state and idempotence coverage stays green.
- `client/src/features/match-viewer/lib/matchActivity.test.ts` — existing chronological reconstruction/deduplication coverage stays green.
- `client/src/features/match-viewer/components/MatchActivityPanel.test.tsx` — existing unified activity rendering remains green.

### Explicitly Do Not Modify

- `server/pom.xml`
- `client/package.json`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/**`
- database migrations or persisted dialogue schema
- frontend production files
- Stockfish/evaluation behavior
- provider retry policy

---

### Task 1: Wire Native Spring AI Observability and Keep Content Logging Off

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java`
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`

**Interfaces:**
- Consumes Boot's `io.micrometer.observation.ObservationRegistry`.
- Preserves existing `groqChatModel` and `geminiChatModel` bean names/types.
- Produces Spring AI's native model observations, including `gen_ai.client.operation` and `gen_ai.client.token.usage` when provider usage metadata is available.
- Does not expose prompt/completion content.

- [ ] **Step 1: Make the AI context test provide the registries that production Boot supplies**

In `AiConfigurationContextTest.java`, add:

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
```

Extend the `contextRunner` chain immediately after `.withUserConfiguration(...)` with:

```java
.withBean(ObservationRegistry.class, ObservationRegistry::create)
.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
```

This keeps the focused context runner representative after the gateway/model configuration starts requiring Micrometer infrastructure.

- [ ] **Step 2: Run the focused context test before changing production configuration**

From repository root run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiConfigurationContextTest test
```

Expected: PASS. This is a baseline check; if it is not green on `master`, stop and investigate rather than burying an unrelated failure inside #46.

- [ ] **Step 3: Inject the observation registry into both manually-created chat models**

In `AiProviderConfiguration.java`, add:

```java
import io.micrometer.observation.ObservationRegistry;
```

Change the Groq bean signature and builder to:

```java
@Bean("groqChatModel")
ChatModel groqChatModel(AiProperties properties, ObservationRegistry observationRegistry) {
  AiProperties.Groq groq = properties.groq();
  return OpenAiChatModel.builder()
      .options(groqOptions(groq.apiKey(), groq.baseUrl(), groq.model(), groq.timeout()))
      .observationRegistry(observationRegistry)
      .build();
}
```

Change the Gemini bean signature and builder to:

```java
@Bean("geminiChatModel")
ChatModel geminiChatModel(AiProperties properties, ObservationRegistry observationRegistry) {
  AiProperties.Gemini gemini = properties.gemini();
  Client client =
      Client.builder()
          .apiKey(gemini.apiKey())
          .httpOptions(geminiHttpOptions(gemini.timeout()))
          .build();

  return GoogleGenAiChatModel.builder()
      .genAiClient(client)
      .options(GoogleGenAiChatOptions.builder().model(gemini.model()).build())
      .retryTemplate(noRetryTemplate())
      .observationRegistry(observationRegistry)
      .build();
}
```

Do not change `ProviderChatClient` or switch from `.content()` to a new response wrapper merely to obtain token counts; Spring AI already reports usage through model observations when the provider supplies it.

- [ ] **Step 4: Expose the generic Actuator metrics endpoint and explicitly keep AI content observation logging disabled**

In `application.yaml`, change:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

to:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

Under the existing `spring.ai.chat` block, keep `client.enabled: false` and add:

```yaml
      observations:
        log-prompt: false
        log-completion: false
        include-error-logging: false
```

The resulting shape is:

```yaml
  ai:
    chat:
      client:
        enabled: false
      observations:
        log-prompt: false
        log-completion: false
        include-error-logging: false
```

Do not enable tracing or add a registry dependency.

- [ ] **Step 5: Format and run the focused configuration tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=AiProviderConfigurationTest,AiConfigurationContextTest test
```

Expected: PASS. `AiProviderConfigurationTest` must continue proving the `8s` Groq timeout, `12s` Gemini timeout, and no-retry policy.

- [ ] **Step 6: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java server/src/main/resources/application.yaml server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java
git commit -m "feat: wire Spring AI observability"
```

---

### Task 2: Add Low-Cardinality Gateway Metrics and Explicit Provider Outcome Classification

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java`

**Interfaces:**
- Produces `AiGatewayMetrics.providerAttempt(String provider, String outcome, Duration duration)`.
- Produces `AiGatewayMetrics.fallbackActivated(String target, String reason)`.
- Produces `AiGatewayMetrics.responseSelected(AiResponseSource source, String reason)`.
- `FailoverAiChatGateway` constructor becomes `(ProviderChatClient groq, ProviderChatClient gemini, AiGatewayMetrics metrics)`.
- Provider outcomes are exactly `success`, `failure`, `timeout`, `validation_failure`.

- [ ] **Step 1: Adapt the gateway test fixture to a real in-memory meter registry**

In `FailoverAiChatGatewayTest.java`, add imports:

```java
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
```

Add fields/helper:

```java
private SimpleMeterRegistry meterRegistry;

@BeforeEach
void setUpMetrics() {
  meterRegistry = new SimpleMeterRegistry();
}

private FailoverAiChatGateway gateway(ProviderChatClient groq, ProviderChatClient gemini) {
  return new FailoverAiChatGateway(groq, gemini, new AiGatewayMetrics(meterRegistry));
}
```

Replace each `new FailoverAiChatGateway(groq, gemini)` in this test with `gateway(groq, gemini)` before adding new assertions.

- [ ] **Step 2: Add failing metric/fallback assertions to the existing behavior tests**

Extend `returnsGroqResultWithoutCallingGemini()` with:

```java
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
```

Extend `groqFailureInvokesGeminiExactlyOnce()` with:

```java
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
```

Extend `invalidGroqResultInvokesGeminiExactlyOnce()` with an assertion for:

```text
ai.gateway.fallback.activations{target="gemini",reason="validation_failure"} == 1
```

Extend `bothProvidersFailReturnsDeterministicFallback()` with assertions for:

```text
ai.gateway.fallback.activations{target="gemini",reason="failure"} == 1
ai.gateway.fallback.activations{target="deterministic_fallback",reason="failure"} == 1
ai.gateway.responses{source="deterministic_fallback",reason="providers_exhausted"} == 1
```

Use the same `meterRegistry.get(...).tags(...).counter().count()` form shown above; do not add a custom test DSL.

- [ ] **Step 3: Add a deterministic timeout classification test**

Add:

```java
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
```

Add this test-local provider stub beside `returning(...)` / `failing(...)`:

```java
private static ProviderChatClient timingOut(AtomicInteger calls) {
  return prompt -> {
    calls.incrementAndGet();
    throw new IllegalStateException(new SocketTimeoutException("read timed out"));
  };
}
```

This simulates timeout classification instantly; do not sleep for 8 or 12 seconds in a unit test.

- [ ] **Step 4: Run the focused test and confirm RED**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=FailoverAiChatGatewayTest test
```

Expected: FAIL because `AiGatewayMetrics` and the three-argument gateway constructor do not exist yet.

- [ ] **Step 5: Create the minimal metrics wrapper**

Create `AiGatewayMetrics.java`:

```java
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
      case GROQ -> "groq";
      case GEMINI -> "gemini";
      case DETERMINISTIC_FALLBACK -> "deterministic_fallback";
    };
  }
}
```

Do not add match/personality/prompt values as tags.

- [ ] **Step 6: Refactor `FailoverAiChatGateway` around one classified provider attempt**

Add fields/imports:

```java
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
```

Add:

```java
private static final Logger log = LoggerFactory.getLogger(FailoverAiChatGateway.class);

private final AiGatewayMetrics metrics;
```

Change the constructor to:

```java
FailoverAiChatGateway(
    ProviderChatClient groq, ProviderChatClient gemini, AiGatewayMetrics metrics) {
  this.groq = Objects.requireNonNull(groq, "groq must not be null");
  this.gemini = Objects.requireNonNull(gemini, "gemini must not be null");
  this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
}
```

Replace `generate(...)`, `attempt(...)`, and `isValid(...)` with this shape:

```java
@Override
public AiChatResult generate(AiChatRequest request, AiResponseValidator validator) {
  Objects.requireNonNull(request, "request must not be null");
  Objects.requireNonNull(validator, "validator must not be null");

  ProviderAttempt groqAttempt = attempt("groq", groq, request.prompt(), validator);
  if (groqAttempt.outcome() == ProviderOutcome.SUCCESS) {
    return selected(groqAttempt.response(), AiResponseSource.GROQ, "primary");
  }

  activateFallback("gemini", groqAttempt.outcome());
  ProviderAttempt geminiAttempt = attempt("gemini", gemini, request.prompt(), validator);
  if (geminiAttempt.outcome() == ProviderOutcome.SUCCESS) {
    return selected(geminiAttempt.response(), AiResponseSource.GEMINI, "fallback");
  }

  activateFallback("deterministic_fallback", geminiAttempt.outcome());
  return selected(
      request.deterministicFallback(),
      AiResponseSource.DETERMINISTIC_FALLBACK,
      "providers_exhausted");
}

private ProviderAttempt attempt(
    String provider,
    ProviderChatClient client,
    String prompt,
    AiResponseValidator validator) {
  long startedAt = System.nanoTime();
  String response = null;
  ProviderOutcome outcome;
  try {
    response = client.complete(prompt);
    outcome = validates(response, validator) ? ProviderOutcome.SUCCESS : ProviderOutcome.VALIDATION_FAILURE;
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
    case GROQ -> "groq";
    case GEMINI -> "gemini";
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
```

Keep `ProviderOutcome`, `ProviderAttempt`, and helpers private. Do not expose provider diagnostic types through `ai.api`.

- [ ] **Step 7: Run the focused test and confirm GREEN**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=FailoverAiChatGatewayTest test
```

Expected: PASS for Groq success, Groq failure -> Gemini, malformed/invalid Groq -> Gemini, timeout -> Gemini, both-provider failure -> deterministic fallback, and metric assertions.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java
git commit -m "feat: observe AI provider failover"
```

---

### Task 3: Wire Metrics Into Both Gateway Topologies and Add Match/Event Log Correlation

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/DisabledAiChatGateway.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`

**Interfaces:**
- `AiGatewayConfiguration` creates one `AiGatewayMetrics` bean from the Boot `MeterRegistry`.
- Disabled mode records `ai.gateway.responses{source="deterministic_fallback",reason="ai_disabled"}` exactly once per generated fallback.
- `MatchDialogueCoordinator` exposes `matchId`, `triggerType`, and `triggerPly` through MDC only for the synchronous dialogue action.

- [ ] **Step 1: Add a failing disabled-mode metric assertion**

In `AiConfigurationContextTest.disabledModeCreatesOnlyFallbackGateway(...)`, after calling `generate(...)`, add:

```java
MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
assertThat(
        meterRegistry
            .get(AiGatewayMetrics.RESPONSES)
            .tags("source", "deterministic_fallback", "reason", "ai_disabled")
            .counter()
            .count())
    .isEqualTo(1.0);
```

Also add to `enabledModeCreatesBothNamedProviderModelsAndClientsAndOneGateway(...)`:

```java
assertThat(context).hasSingleBean(AiGatewayMetrics.class);
```

- [ ] **Step 2: Add a failing MDC lifecycle test to `MatchDialogueCoordinatorTest`**

Add:

```java
import static org.assertj.core.api.Assertions.assertThat;

import org.slf4j.MDC;
```

`assertThat` already exists in this class; do not duplicate the static import if present.

Add this test:

```java
@Test
void scopesMatchAndEventCorrelationThroughDialogueAndClearsItAfterward() {
  when(dialogueGenerator.generateMove(any()))
      .thenAnswer(
          invocation -> {
            assertThat(MDC.get("matchId")).isEqualTo(MATCH_ID.toString());
            assertThat(MDC.get("triggerType")).isEqualTo("MOVE");
            assertThat(MDC.get("triggerPly")).isEqualTo("1");
            return Optional.empty();
          });

  try {
    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    assertThat(MDC.get("matchId")).isNull();
    assertThat(MDC.get("triggerType")).isNull();
    assertThat(MDC.get("triggerPly")).isNull();
  } finally {
    MDC.clear();
  }
}
```

- [ ] **Step 3: Run both focused tests and confirm RED**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiConfigurationContextTest,MatchDialogueCoordinatorTest test
```

Expected: FAIL because gateway metrics are not wired into configuration/disabled mode and the coordinator does not yet populate MDC.

- [ ] **Step 4: Create and inject the shared `AiGatewayMetrics` bean**

In `AiGatewayConfiguration.java`, add:

```java
import io.micrometer.core.instrument.MeterRegistry;
```

Add this bean before the enabled/disabled gateway beans:

```java
@Bean
AiGatewayMetrics aiGatewayMetrics(MeterRegistry meterRegistry) {
  return new AiGatewayMetrics(meterRegistry);
}
```

Change the enabled bean signature/body to:

```java
@Bean
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
AiChatGateway enabledAiChatGateway(
    @Qualifier("groqProviderChatClient") ProviderChatClient groq,
    @Qualifier("geminiProviderChatClient") ProviderChatClient gemini,
    AiGatewayMetrics metrics) {
  log.info("AI gateway topology: enabled (Groq -> Gemini)");
  return new FailoverAiChatGateway(groq, gemini, metrics);
}
```

Change the disabled bean to accept the same metrics object:

```java
AiChatGateway disabledAiChatGateway(AiGatewayMetrics metrics) {
  log.info("AI gateway topology: disabled");
  return new DisabledAiChatGateway(metrics);
}
```

Retain the existing `@ConditionalOnProperty` annotations exactly.

- [ ] **Step 5: Instrument the intentional AI-disabled path without pretending it is provider failover**

Change `DisabledAiChatGateway` to:

```java
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
```

Do **not** increment `ai.gateway.fallback.activations` in disabled mode; no provider call failed, so calling this a failover would make the metric misleading.

- [ ] **Step 6: Scope correlation in `MatchDialogueCoordinator.safeRun`**

Add:

```java
import org.slf4j.MDC;
```

Change each call site from `safeRun(matchId, ply, ...)` to carry the existing trigger type:

```java
safeRun(matchId, DialogueTriggerType.GAME_START, 0, () -> { ... });
safeRun(matchId, DialogueTriggerType.MOVE, move.ply(), () -> { ... });
safeRun(matchId, DialogueTriggerType.GAME_END, totalPlies, () -> { ... });
```

Replace `safeRun(...)` with:

```java
private void safeRun(
    UUID matchId, DialogueTriggerType triggerType, int triggerPly, Runnable action) {
  try (MDC.MDCCloseable ignoredMatch = MDC.putCloseable("matchId", matchId.toString());
      MDC.MDCCloseable ignoredTrigger = MDC.putCloseable("triggerType", triggerType.name());
      MDC.MDCCloseable ignoredPly = MDC.putCloseable("triggerPly", Integer.toString(triggerPly))) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      log.warn(
          "Dialogue unavailable matchId={} triggerType={} triggerPly={}",
          matchId,
          triggerType,
          triggerPly,
          exception);
    }
  }
}
```

The `MDC` values exist while `DialogueGenerationService -> AiChatGateway` executes synchronously, so the gateway logs inherit match/event correlation without changing `AiChatRequest` or any public API.

- [ ] **Step 7: Run focused tests and confirm GREEN**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=AiConfigurationContextTest,MatchDialogueCoordinatorTest,FailoverAiChatGatewayTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/DisabledAiChatGateway.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinator.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java
git commit -m "feat: correlate AI observability with match events"
```

---

### Task 4: Prove Logs Do Not Leak Prompt/Response Content and Lock the Resilience Matrix

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java`
- Verify existing: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java`
- Verify existing: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java`
- Verify existing: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchEngineTest.java`
- Verify existing frontend tests listed below.

**Interfaces:**
- No new production interface.
- The automated resilience matrix must remain provider-network-free.

- [ ] **Step 1: Capture gateway logs in the existing gateway test**

Add imports:

```java
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
```

Annotate the class:

```java
@ExtendWith(OutputCaptureExtension.class)
class FailoverAiChatGatewayTest {
```

- [ ] **Step 2: Add a leakage regression test using unmistakable secret-like content**

Add:

```java
@Test
void providerLogsContainOnlySafeMetadata(CapturedOutput output) {
  AtomicInteger groqCalls = new AtomicInteger();
  AtomicInteger geminiCalls = new AtomicInteger();
  AiChatRequest request =
      new AiChatRequest("SECRET_PROMPT_DO_NOT_LOG", "SECRET_FALLBACK_DO_NOT_LOG");
  FailoverAiChatGateway gateway =
      gateway(
          returning("SECRET_RAW_RESPONSE_DO_NOT_LOG", groqCalls),
          failing(geminiCalls));

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
```

This test is deliberately about normal application logs. Do not switch framework logging to DEBUG to make the test pass.

- [ ] **Step 3: Run the complete backend resilience slice**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=FailoverAiChatGatewayTest,AiProviderConfigurationTest,AiConfigurationContextTest,DialogueGenerationServiceTest,MatchDialogueCoordinatorTest,MatchEngineTest test
```

Expected: PASS. Together these tests prove, without real provider calls:

1. Groq valid response -> Groq selected; Gemini untouched.
2. Groq exception -> Gemini selected exactly once.
3. Groq invalid/malformed structured response -> Gemini selected exactly once.
4. Groq timeout-classified exception -> Gemini selected exactly once.
5. Both providers failing/invalid -> deterministic fallback selected.
6. Groq remains bounded at `8s`; Gemini remains bounded at `12s`; neither retries itself.
7. AI-disabled topology returns deterministic fallback and records `reason=ai_disabled` without creating provider models/clients.
8. Match/event correlation exists during dialogue work and does not leak to the next operation.
9. Dialogue-generation failure cannot prevent `MatchEngine` from reaching a finished match.
10. Logs expose safe operational metadata but not prompt/response/fallback content.

- [ ] **Step 4: Run the focused frontend ordering/hydration regression suite**

From `client/` run:

```powershell
npm.cmd test -- src/store/matchViewerStore.test.ts src/features/match-viewer/lib/matchActivity.test.ts src/features/match-viewer/components/MatchActivityPanel.test.tsx
```

Expected: PASS. These are the existing #45 contracts for live append, hydrated reconstruction, stable deduplication/order, speaker rendering, and long feed behavior. Do not add a second #46 frontend implementation of the same logic.

- [ ] **Step 5: Commit the leakage/resilience test addition**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java
git commit -m "test: lock AI resilience observability"
```

---

### Task 5: Document Phase 2 Setup, Offline Fast Mode, Forced Failover, and Acceptance Procedure

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Documentation only.
- Automated repository verification remains credential-free with `AI_ENABLED=false` / default disabled mode.
- Provider-enabled acceptance credentials remain local and ignored by git.

- [ ] **Step 1: Add a `Phase 2 AI verification` section before the acceptance records**

Add the following operational content, adapting only formatting to the existing document style:

```markdown
## Phase 2 AI verification

Automated repository verification does not require Groq or Gemini credentials. `AI_ENABLED` defaults to `false`; in that mode the normal dialogue workflow stays active but `DisabledAiChatGateway` returns the deterministic character fallback instead of making a network call.

### Safe local provider credentials

When manually checking real providers, put credentials only in `server/.env` or process environment variables. `.env` files are git-ignored. Never paste real keys into committed configuration, test fixtures, logs, screenshots, issue comments, or this document.

Required provider-enabled variables:

```text
AI_ENABLED=true
AI_GROQ_API_KEY=<local secret>
AI_GROQ_MODEL=<configured Groq model id>
AI_GEMINI_API_KEY=<local secret>
AI_GEMINI_MODEL=<configured Gemini model id>
```

Before committing, run `git status --short` and confirm `server/.env` is not staged or listed.

### Fast local Phase 2 mode

For a short credential-free UI/lifecycle run, start the backend with:

```text
AI_ENABLED=false
GAME_MOVE_THINK_TIME_MILLIS=0
GAME_MOVE_DELAY_MIN=0s
GAME_MOVE_DELAY_MAX=0s
GAME_MAX_PLIES=12
```

This keeps the production dialogue/persistence/event path active while replacing provider calls with deterministic personality fallbacks and ending the test match after at most 12 plies.

### Forced Groq -> Gemini failover

To exercise real fallback without intentionally spending a Groq call, keep a valid local Gemini key/model and point Groq at a closed local port for that run:

```text
AI_ENABLED=true
AI_GROQ_API_KEY=forced-failure-not-a-secret
AI_GROQ_BASE_URL=http://127.0.0.1:9/v1
AI_GROQ_MODEL=forced-failure
AI_GEMINI_API_KEY=<local secret>
AI_GEMINI_MODEL=<configured Gemini model id>
GAME_MOVE_THINK_TIME_MILLIS=0
GAME_MOVE_DELAY_MIN=0s
GAME_MOVE_DELAY_MAX=0s
GAME_MAX_PLIES=12
```

The expected path is Groq `failure` -> fallback activation targeting `gemini` -> Gemini response. Restore the normal Groq base URL after the check.

### Inspecting Phase 2 metrics

The management server remains on port `8081`. List AI metrics with:

```powershell
(Invoke-RestMethod http://localhost:8081/actuator/metrics).names | Select-String "ai.gateway|gen_ai"
```

Inspect the application-level metrics with:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.provider.duration | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.fallback.activations | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.responses | ConvertTo-Json -Depth 8
```

Spring AI model metrics include `gen_ai.client.operation` and, when the provider reports usage, `gen_ai.client.token.usage`. Token usage is provider-dependent; its absence for a provider response is not an application failure.

Normal application logs may include provider, outcome, duration, match ID, trigger type, and trigger ply. They must not contain prompt text, raw provider responses, deterministic fallback text, or credentials.

### Phase 2 manual acceptance checklist

- [ ] Run a provider-enabled Blaze vs Vesper match and observe distinct character voices.
- [ ] Run a provider-enabled Gremlin vs Regent match and observe distinct character voices.
- [ ] Use Random Rivalry once and confirm it selects two distinct personalities.
- [ ] Observe at least one contextual line that responds to the current move/event or recent banter.
- [ ] Refresh after dialogue exists and confirm the same move/dialogue order returns without duplication.
- [ ] While the backend remains running, temporarily toggle the browser network offline then online; confirm reconnect clears the connection warning and hydrates the same dialogue order without duplicates.
- [ ] Stop an in-progress match, then resume it; confirm previously persisted dialogue remains ordered and new dialogue continues after the authoritative ply.
- [ ] Complete at least one credential-free `AI_ENABLED=false` fast-mode match; deterministic fallback dialogue must not block completion.
- [ ] Complete at least one forced Groq -> Gemini failover match; the match must finish and Gemini/fallback metrics must be visible.
- [ ] Inspect normal logs and confirm `matchId`, `triggerType`, `triggerPly`, provider/outcome metadata are present while prompts, raw responses, fallback text, and credentials are absent.
- [ ] Inspect `ai.gateway.provider.duration`, `ai.gateway.fallback.activations`, and `ai.gateway.responses`; confirm Groq, Gemini fallback, and deterministic fallback outcomes are distinguishable across the acceptance runs.
- [ ] Check `gen_ai.client.token.usage` when listed by Actuator and record whether provider token usage was available.
- [ ] Run the root verifier successfully before recording Phase 2 acceptance.
```

The `<local secret>` / `<configured ... model id>` values above are documentation placeholders intentionally representing developer-owned environment values; never replace them in committed documentation with real credentials.

- [ ] **Step 2: Do not add a Phase 2 acceptance record yet**

At this point the document contains instructions/checklist only. Leave the checklist unchecked until Task 6 is actually performed.

- [ ] **Step 3: Commit the verification instructions**

```bash
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: add phase 2 verification workflow"
```

---

### Task 6: Run Full Verification and Record the Observed Phase 2 Acceptance

**Files:**
- Modify after observation: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- No production code changes in this task.
- A dated acceptance record is evidence, not a prediction; mark only what was actually observed.

- [ ] **Step 1: Run the root repository verifier with AI credentials disabled**

From repository root on Windows:

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

Or on POSIX:

```sh
AI_ENABLED=false ./scripts/verify.sh
```

Expected: backend verify and frontend verify both PASS. Do not continue to a final acceptance record while the root verifier is red.

- [ ] **Step 2: Start the normal local topology for the manual checks**

Use PostgreSQL in Docker, backend locally so management port `8081` is reachable, and the Vite frontend on `5173`, following the existing local-development commands in `BUILD_AND_VERIFY.md`.

First run one credential-free fast-mode match with:

```text
AI_ENABLED=false
GAME_MOVE_THINK_TIME_MILLIS=0
GAME_MOVE_DELAY_MIN=0s
GAME_MOVE_DELAY_MAX=0s
GAME_MAX_PLIES=12
```

Expected: deterministic personality fallback dialogue persists/renders and the match completes without any provider credential or network call.

- [ ] **Step 3: Run the four-personality/provider-enabled acceptance**

With normal Groq and Gemini configuration restored locally:

1. Run Blaze vs Vesper.
2. Run Gremlin vs Regent.
3. Use Random Rivalry at least once and confirm the selected keys are distinct.
4. During one match, refresh after dialogue exists and compare the restored feed with the pre-refresh sequence.
5. During one match, toggle browser networking offline then online while leaving the backend process running; wait for the application's reconnect path and verify hydration does not duplicate/reorder dialogue.
6. During one match, use Stop and Resume after dialogue has been persisted; verify history stays ordered and the match continues from the authoritative state.
7. Observe at least one line whose wording is grounded in the current move/event or recent rivalry dialogue.

Do not call a personality "verified" merely because its name rendered; its dialogue style must visibly match its seeded identity.

- [ ] **Step 4: Run the forced Groq -> Gemini acceptance**

Use the documented closed-local-port Groq configuration and a valid local Gemini configuration. Start a short match and let it complete.

Expected evidence:

```text
AI provider attempt provider=groq outcome=failure ... matchId=... triggerType=... triggerPly=...
AI fallback activated target=gemini reason=failure ...
AI response selected source=gemini reason=fallback ...
```

Exact duration/match/ply values vary. The log must not contain the generated prompt or raw Gemini response.

Inspect Actuator and verify at minimum:

```text
ai.gateway.provider.duration -> groq/failure and gemini/success series exist
ai.gateway.fallback.activations -> target=gemini,reason=failure count >= 1
ai.gateway.responses -> source=gemini,reason=fallback count >= 1
```

Across the credential-free and provider-enabled runs, verify `ai.gateway.responses` also distinguishes deterministic fallback and direct Groq success when Groq succeeded during the normal provider run.

- [ ] **Step 5: Inspect Spring AI native metrics**

Run:

```powershell
(Invoke-RestMethod http://localhost:8081/actuator/metrics).names | Select-String "gen_ai"
```

Record whether `gen_ai.client.token.usage` is present for the real provider response. If absent, record `token usage metric not reported by provider in this acceptance run`; do not fabricate zero-token values and do not add custom token estimation.

- [ ] **Step 6: Inspect logs specifically for leakage**

Search the normal backend output from the acceptance run. Confirm operational entries contain provider/outcome plus `matchId`, `triggerType`, and `triggerPly` where dialogue was triggered.

Confirm no normal log line contains:

- full prompt sections/personality prompt traits
- generated raw dialogue response before validation
- deterministic fallback line text
- `AI_GROQ_API_KEY`
- `AI_GEMINI_API_KEY`
- Authorization header values

If any content leak exists, stop acceptance and fix it before recording success.

- [ ] **Step 7: Append the dated Phase 2 acceptance record**

Get the execution date instead of guessing it:

```powershell
Get-Date -Format "yyyy-MM-dd"
```

or:

```sh
date +%F
```

Under the Phase 2 checklist in `BUILD_AND_VERIFY.md`, append `### Acceptance record — <the date returned above>` and record concise observed facts for:

- root verifier result
- four personalities exercised (Blaze, Vesper, Gremlin, Regent)
- random rivalry observed
- contextual dialogue observed
- refresh result
- reconnect result
- stop/resume result
- AI-disabled match completion
- forced Groq -> Gemini match completion
- Groq/Gemini/deterministic metric evidence
- Spring AI token metric availability/not-reported status
- log correlation/leakage inspection

Use `[x]` only for observations that passed. If something was not exercised, leave it `[ ]` and state why; do not silently convert an untested item into success.

- [ ] **Step 8: Run documentation-sensitive verification once more**

Run from repository root:

```powershell
.\scripts\verify.ps1
```

Expected: PASS.

- [ ] **Step 9: Commit the observed acceptance record**

```bash
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: record phase 2 acceptance"
```

---

## Final Self-Review Before Opening the PR

- [ ] `git diff master...HEAD -- server/pom.xml client/package.json` is empty; #46 added no dependencies.
- [ ] No public `ai.api` contract changed just to carry observability metadata.
- [ ] `ProviderChatClient` still has the small existing string-completion contract.
- [ ] Groq timeout remains `8s`; Gemini timeout remains `12s`; no same-provider retry was added.
- [ ] Custom metric tags are low-cardinality and contain no `matchId`, prompt, response text, or personality key.
- [ ] Spring AI prompt/completion/error-content observation logging is explicitly false.
- [ ] Gateway logs contain safe provider/outcome/duration + match/event correlation only.
- [ ] Groq success, Groq failure -> Gemini, malformed Groq -> Gemini, timeout -> Gemini, both-provider failure, and AI-disabled mode are all covered without real provider calls in tests.
- [ ] Existing `MatchEngineTest#dialogueFailureDoesNotPreventMatchCompletion` passes.
- [ ] Existing frontend live/hydration/deduplication tests pass; no duplicate #46 frontend logic was introduced.
- [ ] Root verification passes.
- [ ] `BUILD_AND_VERIFY.md` contains the actually observed dated Phase 2 acceptance record before the issue is closed.

## Expected PR Scope

A good #46 PR should be mostly backend instrumentation/tests plus verification documentation. If implementation starts adding tracing infrastructure, dashboards, new provider APIs, frontend features, persistence tables, or a generic observability framework, stop: that is scope creep and contradicts both the issue and the project's showcase-first architecture.
