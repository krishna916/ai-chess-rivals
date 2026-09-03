# OpenRouter Cost-Aware Provider Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Groq-primary/Gemini-fallback integration with one OpenRouter/OpenAI-compatible path using a specific free primary model, one ultra-low-cost remote fallback model, and the existing deterministic local fallback while preserving dialogue behavior, resilience, persistence, and match lifecycle semantics.

**Architecture:** Keep the existing `AiChatGateway`/`ProviderChatClient` boundary and one-shot failover flow. Two manually-created Spring AI `OpenAiChatModel` instances share one OpenRouter API key/base URL but use independently configurable model IDs and timeouts; the gateway identifies them by role (`primary`, `remote_fallback`) rather than vendor. Keep the deterministic fallback as the final safety net, keep all automated tests network-free, and use normal match pacing (`7s`–`12s`) instead of adding a rate-limiter subsystem.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0 `OpenAiChatModel`, Micrometer/Actuator, PostgreSQL 17 + Flyway, GraalVM Native Image, Docker/Docker Compose, React 19.2.7, TypeScript 6.0.2, JUnit 5, AssertJ, existing network-free provider fakes.

**Spec:** GitHub issue `#62 Phase 2: Migrate AI provider routing to OpenRouter with cost-aware fallback` is the approved bounded requirements/design for this migration. It extends the existing Phase 2 design in `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md` without introducing a new AI subsystem.

## Source of Truth

- Issue: `#62 Phase 2: Migrate AI provider routing to OpenRouter with cost-aware fallback`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Completed dependency/baseline: `#46 Phase 2: Add AI observability, resilience coverage, and acceptance verification`
- Related future issue: `#61 Phase 3: Standardize AI providers on the OpenAI-compatible client` — do not expand this migration into Phase 3 tool-calling work.
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`
- Current OpenRouter API base URL: `https://openrouter.ai/api/v1`
- Current fallback model ID verified while planning: `~deepseek/deepseek-v4-flash-latest`
- Current free-tier policy assumed by #62: 20 requests/minute; accounts with at least $10 credits receive up to 1,000 free-model requests/day.

## Global Constraints

- Keep the flow exactly: **specific OpenRouter free primary -> exactly one low-cost OpenRouter fallback attempt -> deterministic personality-specific fallback**.
- Do not use `openrouter/free` as the configured/default production primary route; personality voice consistency requires a specific `:free` model ID.
- Use one OpenRouter API key and one OpenRouter base URL for both remote attempts.
- Keep primary and fallback model IDs independently environment-configurable.
- Keep remote timeouts independently configuration-backed. Preserve the current effective boundaries unless a focused test proves a necessary change: primary `8s`, remote fallback `12s`.
- Set Spring AI/OpenAI-compatible retries to zero. Do not retry the same model before falling through to the next stage.
- Preserve structured-output validation before accepting any remote response.
- Preserve deterministic personality-specific fallback when either OpenRouter is unavailable or both configured model attempts fail/timeout/validate poorly.
- Preserve the existing prompt factory, personality behavior, dialogue trigger policy, dialogue persistence ordering, match lifecycle, Stockfish behavior, and chess code.
- Keep logs credential-safe and content-safe: never log API keys, authorization headers, prompts, raw responses, or deterministic fallback text.
- Keep custom metric tags low-cardinality. Do not tag metrics with model IDs, API keys, personality IDs, match IDs, prompts, or response text.
- Rename application-level provider metrics/log labels to role-based values: `primary`, `remote_fallback`, `deterministic_fallback`. Do not emit new Groq/Gemini labels.
- Keep all automated provider/resilience tests network-free. Constructing Spring AI models is allowed; no test may perform a real OpenRouter HTTP request.
- Remove the Google Gen AI chat-model/client wiring and `spring-ai-starter-model-google-genai` dependency after the OpenRouter path is green.
- Do not add a new Maven dependency, provider framework, adapter registry, plugin architecture, rate limiter, Redis, Bucket4j, Resilience4j throttling, distributed counter, retry framework, or additional agent.
- Change normal move-delay defaults from `3s`–`10s` to `7s`–`12s`. `0s` remains an explicit local/test-only override.
- Keep Spring AOT's build-time AI topology contract. Native builds with AI enabled must receive non-secret placeholder OpenRouter values that satisfy configuration validation without making network calls.
- Historical files under `docs/superpowers/plans/` and old dated specs are records of earlier decisions; do not bulk-rewrite them merely to remove the words Groq/Gemini. Update active runtime/configuration/documentation only.
- Apply Java formatting before backend verification and Prettier before frontend verification.

## File Map

### Create

- `server/src/main/resources/db/migration/V5__rename_ai_response_sources.sql` — migrate persisted provider-specific response-source values to provider-neutral role values without changing table shape.

### Modify — AI Runtime

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseSource.java` — replace vendor-specific source enum values with remote-role values.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java` — replace `Groq`/`Gemini` properties with one `OpenRouter` record containing shared credentials/base URL and primary/fallback model+timeout values.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java` — register the new `AiProperties.OpenRouter` fields and validation method for native reflection.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java` — create both remote models/clients through `OpenAiChatModel`; remove Google Gen AI SDK/model code.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java` — inject role-named OpenRouter clients and update the topology marker.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java` — rename provider fields/attempt labels to role-based names while preserving exactly-one-fallback behavior.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java` — map response sources to role-based metric tags.
- `server/pom.xml` — remove `spring-ai-starter-model-google-genai`; keep `spring-ai-starter-model-openai`.

### Modify — Runtime/AOT/Deployment

- `server/src/main/resources/application.yaml` — replace Groq/Gemini properties with OpenRouter properties; set normal move delay to `7s`–`12s`.
- `server/.env.example` — expose one OpenRouter key/base URL, specific free primary model, cheap fallback model, both timeouts, and `7s`–`12s` pacing.
- `server/docker-compose.yml` — pass OpenRouter variables and move-delay variables to the backend container.
- `server/Dockerfile` — replace Groq/Gemini AOT placeholder values with OpenRouter placeholders.
- `.github/workflows/ci.yml` — update AI-enabled native runtime values, topology marker, and baked-environment checks.

### Modify — API Contract / Frontend Type

- `client/src/types/match.ts` — change `AiResponseSource` from `GROQ | GEMINI | DETERMINISTIC_FALLBACK` to `REMOTE_PRIMARY | REMOTE_FALLBACK | DETERMINISTIC_FALLBACK`.

### Modify — Tests

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceServiceTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistencePostgresIT.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchWebSocketIntegrationTest.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GamePropertiesBindingTest.java` — keep the existing range-validation proof green with the new production range.

### Modify — Active Documentation

- `server/README.md`
- `docs/BUILD_AND_VERIFY.md`
- `docs/AI Chess Rivals - Tech Stack.md`
- `AGENTS.md`
- `.agents/AGENTS.md`

### Explicitly Do Not Modify

- Stockfish/evaluation implementation.
- Prompt templates, output schema, `DialogueOutputCodec`, speaking policy, personality seeds, or deterministic fallback catalog.
- Match lifecycle/state-transition code.
- REST/WebSocket DTO shape other than the `AiResponseSource` enum values already exposed through existing dialogue DTOs.
- Database table shape or dialogue ordering/uniqueness constraints.
- `client/package.json` or frontend production components/stores.
- Historical completed implementation plans merely to rename old provider references.
- Phase 3 tool calling, memory, agents, routing marketplace, dynamic provider discovery, or provider plugins.

---

### Task 1: Make Dialogue Response Sources Provider-Neutral

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseSource.java`
- Create: `server/src/main/resources/db/migration/V5__rename_ai_response_sources.sql`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java`
- Modify: `client/src/types/match.ts`
- Modify tests listed in the File Map that reference `AiResponseSource.GROQ` or `AiResponseSource.GEMINI`.

**Interfaces:**
- Produces enum values `AiResponseSource.REMOTE_PRIMARY`, `AiResponseSource.REMOTE_FALLBACK`, `AiResponseSource.DETERMINISTIC_FALLBACK`.
- Persists those exact enum names as strings in `dialogue_line.response_source`.
- Keeps the existing `source` field in REST/WebSocket dialogue payloads; only its enum values change.
- Keeps historic meaning correct: old Groq rows were the remote primary; old Gemini rows were the remote fallback.

- [ ] **Step 1: Change focused gateway expectations first**

In `FailoverAiChatGatewayTest.java`, change the primary-success and fallback-success assertions to the new role-based enum values before changing production code:

```java
assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_PRIMARY);
```

and:

```java
assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_FALLBACK);
```

Change every remote-provider expectation in this test accordingly; deterministic fallback expectations remain unchanged.

- [ ] **Step 2: Run the focused gateway test and verify the enum change is not implemented yet**

Run from repository root:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=FailoverAiChatGatewayTest test
```

Expected: compilation FAIL because `REMOTE_PRIMARY` / `REMOTE_FALLBACK` do not exist yet.

- [ ] **Step 3: Replace the response-source enum**

Replace `AiResponseSource.java` with:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

public enum AiResponseSource {
  REMOTE_PRIMARY,
  REMOTE_FALLBACK,
  DETERMINISTIC_FALLBACK
}
```

- [ ] **Step 4: Update gateway selection to the new enum without changing failover behavior yet**

In `FailoverAiChatGateway.generate(...)`, change only the selected source constants:

```java
if (groqAttempt.outcome() == ProviderOutcome.SUCCESS) {
  return selected(groqAttempt.response(), AiResponseSource.REMOTE_PRIMARY, "primary");
}
```

and:

```java
if (geminiAttempt.outcome() == ProviderOutcome.SUCCESS) {
  return selected(geminiAttempt.response(), AiResponseSource.REMOTE_FALLBACK, "fallback");
}
```

The provider variables are renamed in Task 3; keep this step deliberately behavioral-only.

- [ ] **Step 5: Make metric source mapping provider-neutral**

In `AiGatewayMetrics.sourceTag(...)`, use:

```java
return switch (source) {
  case REMOTE_PRIMARY -> "primary";
  case REMOTE_FALLBACK -> "remote_fallback";
  case DETERMINISTIC_FALLBACK -> "deterministic_fallback";
};
```

In `FailoverAiChatGateway.sourceTag(...)`, use the same mapping. Task 3 removes the remaining vendor-specific attempt/target labels.

- [ ] **Step 6: Add the Flyway data migration**

Create `server/src/main/resources/db/migration/V5__rename_ai_response_sources.sql` exactly as:

```sql
UPDATE dialogue_line
SET response_source = 'REMOTE_PRIMARY'
WHERE response_source = 'GROQ';

UPDATE dialogue_line
SET response_source = 'REMOTE_FALLBACK'
WHERE response_source = 'GEMINI';
```

Do not alter the table, add a new column, or delete existing dialogue rows.

- [ ] **Step 7: Update backend tests that construct or expect the old enum values**

Run:

```bash
git grep -n -E 'AiResponseSource\.(GROQ|GEMINI)' -- server/src/test server/src/main
```

For every runtime/test hit, replace:

```text
AiResponseSource.GROQ   -> AiResponseSource.REMOTE_PRIMARY
AiResponseSource.GEMINI -> AiResponseSource.REMOTE_FALLBACK
```

The expected affected tests are:

```text
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistenceServiceTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePersistencePostgresIT.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/application/MatchDialogueCoordinatorTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchResponseMapperTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchWebSocketIntegrationTest.java
```

Do not change deterministic fallback logic.

- [ ] **Step 8: Update the frontend source union**

In `client/src/types/match.ts`, replace:

```ts
export type AiResponseSource = "GROQ" | "GEMINI" | "DETERMINISTIC_FALLBACK";
```

with:

```ts
export type AiResponseSource =
  "REMOTE_PRIMARY" | "REMOTE_FALLBACK" | "DETERMINISTIC_FALLBACK";
```

No component change is needed because the current activity UI does not render provider/source names.

- [ ] **Step 9: Format and run the response-source/persistence checks**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=FailoverAiChatGatewayTest,DialogueGenerationServiceTest,DialoguePersistenceServiceTest,DialoguePersistencePostgresIT,MatchDialogueCoordinatorTest,MatchResponseMapperTest,MatchStreamMessageMapperTest,MatchWebSocketIntegrationTest test
cd client
npm.cmd run format
npm.cmd run typecheck
cd ..
```

Expected: PASS. If `DialoguePersistencePostgresIT` is intentionally excluded by the repository's normal unit-test pattern, run its existing documented integration-test command instead; do not delete or weaken it.

- [ ] **Step 10: Verify no active source still compiles against vendor-specific response enums**

Run:

```bash
git grep -n -E 'AiResponseSource\.(GROQ|GEMINI)|"GROQ" \| "GEMINI"' -- server/src client/src || true
```

Expected: no hits.

- [ ] **Step 11: Commit**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseSource.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java server/src/main/resources/db/migration/V5__rename_ai_response_sources.sql server/src/test client/src/types/match.ts
git commit -m "refactor: make AI response sources provider neutral"
```

---

### Task 2: Replace Groq/Gemini Properties With One OpenRouter Configuration

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`

**Interfaces:**
- `AiProperties` becomes `AiProperties(boolean enabled, @Valid @NotNull OpenRouter openrouter)`.
- `AiProperties.OpenRouter` fields are exactly: `apiKey`, `baseUrl`, `primaryModel`, `fallbackModel`, `primaryTimeout`, `fallbackTimeout`.
- Enabled mode requires a nonblank API key/base URL/fallback model and a **specific** primary model ending in `:free`; `openrouter/free` is rejected.
- Both configured timeouts must be greater than zero.

- [ ] **Step 1: Update the focused context runner to the target property shape**

In `AiConfigurationContextTest`, replace the base provider properties with:

```java
.withPropertyValues(
    "app.ai.openrouter.base-url=https://openrouter.ai/api/v1",
    "app.ai.openrouter.primary-timeout=8s",
    "app.ai.openrouter.fallback-timeout=12s");
```

Change enabled-mode properties to:

```java
.withPropertyValues(
    "app.ai.enabled=true",
    "app.ai.openrouter.api-key=test-openrouter-key",
    "app.ai.openrouter.primary-model=inclusionai/ling-3.0-flash:free",
    "app.ai.openrouter.fallback-model=~deepseek/deepseek-v4-flash-latest")
```

Do not change the disabled-mode expectation.

- [ ] **Step 2: Add a failing validation test for the random free router**

Add to `AiConfigurationContextTest`:

```java
@Test
void enabledModeRejectsRandomOpenRouterFreeRoute() {
  contextRunner
      .withPropertyValues(
          "app.ai.enabled=true",
          "app.ai.openrouter.api-key=test-openrouter-key",
          "app.ai.openrouter.primary-model=openrouter/free",
          "app.ai.openrouter.fallback-model=~deepseek/deepseek-v4-flash-latest")
      .run(context -> assertThat(context).hasFailed());
}
```

This locks the issue's voice-consistency rule into startup validation instead of relying only on documentation.

- [ ] **Step 3: Update native-hint tests before production hints**

In `AiRuntimeHintsTest`, change the validation-method expectation to:

```java
assertThat(
        RuntimeHintsPredicates.reflection()
            .onMethodInvocation(AiProperties.OpenRouter.class, "areTimeoutsPositive")
            .test(hints))
    .isTrue();
```

Change declared-field assertions to:

```java
assertFieldAccess(hints, AiProperties.class, "enabled");
assertFieldAccess(hints, AiProperties.class, "openrouter");

assertFieldAccess(hints, AiProperties.OpenRouter.class, "apiKey");
assertFieldAccess(hints, AiProperties.OpenRouter.class, "baseUrl");
assertFieldAccess(hints, AiProperties.OpenRouter.class, "primaryModel");
assertFieldAccess(hints, AiProperties.OpenRouter.class, "fallbackModel");
assertFieldAccess(hints, AiProperties.OpenRouter.class, "primaryTimeout");
assertFieldAccess(hints, AiProperties.OpenRouter.class, "fallbackTimeout");
```

Remove all `AiProperties.Groq` / `AiProperties.Gemini` assertions.

- [ ] **Step 4: Run the focused configuration tests and verify they fail against the old records**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest,AiConfigurationContextTest test
```

Expected: compilation/test FAIL because `OpenRouter` and `app.ai.openrouter.*` do not exist yet.

- [ ] **Step 5: Replace `AiProperties` with the bounded OpenRouter shape**

Use this structure:

```java
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean enabled, @Valid @NotNull OpenRouter openrouter) {

  @AssertTrue(
      message =
          "When app.ai.enabled=true, OpenRouter API key/base URL, a specific :free primary model (not openrouter/free), and fallback model must be configured")
  public boolean isEnabledConfigurationComplete() {
    if (!enabled) {
      return true;
    }
    if (openrouter == null) {
      return false;
    }
    return hasText(openrouter.apiKey())
        && hasText(openrouter.baseUrl())
        && isSpecificFreeModel(openrouter.primaryModel())
        && hasText(openrouter.fallbackModel());
  }

  public record OpenRouter(
      String apiKey,
      String baseUrl,
      String primaryModel,
      String fallbackModel,
      @NotNull Duration primaryTimeout,
      @NotNull Duration fallbackTimeout) {

    @AssertTrue(
        message =
            "app.ai.openrouter.primary-timeout and app.ai.openrouter.fallback-timeout must be greater than zero")
    public boolean areTimeoutsPositive() {
      return isPositive(primaryTimeout) && isPositive(fallbackTimeout);
    }

    private static boolean isPositive(Duration value) {
      return value == null || (!value.isNegative() && !value.isZero());
    }
  }

  private static boolean isSpecificFreeModel(String model) {
    return hasText(model) && model.endsWith(":free") && !"openrouter/free".equals(model);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
```

Keep the existing imports for `@Valid`, `@AssertTrue`, `@NotNull`, `Duration`, and `@ConfigurationProperties`. Remove the old nested `Groq`/`Gemini` records entirely.

- [ ] **Step 6: Replace runtime hints for the old nested records**

Keep the `AiProperties.class` registration and `isEnabledConfigurationComplete` invocation. Replace both old nested-record hint blocks with one:

```java
hints
    .reflection()
    .registerType(
        AiProperties.OpenRouter.class,
        typeHint ->
            typeHint
                .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                .withMethod("areTimeoutsPositive", List.of(), ExecutableMode.INVOKE));
```

- [ ] **Step 7: Run the focused configuration/hint tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest,AiConfigurationContextTest test
```

At this point `AiConfigurationContextTest` may still fail while it expects old provider bean names; if so, keep the new property-binding/validation assertions and complete Task 3 immediately. Do not reintroduce old properties merely to make an intermediate commit green.

- [ ] **Step 8: Commit only when the configuration tests are green together with Task 3 provider wiring**

If Task 2 cannot be independently green because `AiProviderConfiguration` still consumes `groq()`/`gemini()`, do **not** create a broken commit. Continue directly into Task 3 and commit Tasks 2+3 together after the focused suite passes.

---

### Task 3: Route Both Remote Attempts Through OpenRouter and Preserve One-Shot Failover/Observability

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayMetrics.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java`
- Modify: `server/pom.xml`

**Interfaces:**
- Beans: `openRouterPrimaryChatModel`, `openRouterPrimaryChatClient`, `openRouterPrimaryProviderChatClient`.
- Beans: `openRouterFallbackChatModel`, `openRouterFallbackChatClient`, `openRouterFallbackProviderChatClient`.
- Both use `AiProperties.OpenRouter.apiKey()` and `.baseUrl()`.
- Primary uses `.primaryModel()` / `.primaryTimeout()`; fallback uses `.fallbackModel()` / `.fallbackTimeout()`.
- `OpenAiChatOptions.maxRetries` is `0` for both.
- Gateway constructor becomes `(ProviderChatClient primary, ProviderChatClient remoteFallback, AiGatewayMetrics metrics)`.
- Metric/log role values are exactly `primary`, `remote_fallback`, `deterministic_fallback`.

- [ ] **Step 1: Replace provider-option tests with OpenRouter-compatible expectations**

Replace the Google-specific imports/tests in `AiProviderConfigurationTest` with tests around one shared helper:

```java
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
```

Delete the `HttpOptions`, `RetryTemplate`, and Gemini SDK retry tests; the OpenAI-compatible path now uses the same `maxRetries(0)` contract for both models.

- [ ] **Step 2: Change enabled-context bean/topology expectations**

In `AiConfigurationContextTest`, expect:

```java
assertThat(context).hasBean("openRouterPrimaryChatModel");
assertThat(context).hasBean("openRouterPrimaryChatClient");
assertThat(context).hasBean("openRouterFallbackChatModel");
assertThat(context).hasBean("openRouterFallbackChatClient");
assertThat(context).hasSingleBean(AiChatGateway.class);
assertThat(context).hasSingleBean(AiGatewayMetrics.class);
assertThat(output.getAll())
    .contains("AI gateway topology: enabled (OpenRouter primary -> OpenRouter fallback -> deterministic fallback)")
    .doesNotContain("AI gateway topology: enabled (Groq -> Gemini)")
    .doesNotContain("AI gateway topology: disabled");
```

Update the disabled-mode negative marker to the new enabled topology string.

- [ ] **Step 3: Rename failover tests to roles and lock role-based metrics**

In `FailoverAiChatGatewayTest`, rename the fixture helper:

```java
private FailoverAiChatGateway gateway(
    ProviderChatClient primary, ProviderChatClient remoteFallback) {
  return new FailoverAiChatGateway(primary, remoteFallback, new AiGatewayMetrics(meterRegistry));
}
```

Rename test methods/variables so they no longer imply Groq/Gemini. At minimum preserve these behavioral cases:

```text
returnsPrimaryResultWithoutCallingRemoteFallback
primaryFailureInvokesRemoteFallbackExactlyOnce
invalidPrimaryResultInvokesRemoteFallbackExactlyOnce
validatorExceptionAlsoFallsThroughToRemoteFallback
bothRemoteAttemptsFailReturnsDeterministicFallback
invalidRemoteFallbackResultReturnsDeterministicFallback
primaryTimeoutIsObservableAndFallsBackToRemoteFallback
malformedPrimaryStructuredOutputFallsThroughToValidRemoteFallback
providerLogsContainOnlySafeMetadata
```

Update metric assertions to:

```java
.tags("provider", "primary", "outcome", "success")
```

```java
.tags("target", "remote_fallback", "reason", "failure")
```

```java
.tags("source", "remote_fallback", "reason", "fallback")
```

Timeout path:

```java
.tags("provider", "primary", "outcome", "timeout")
```

```java
.tags("target", "remote_fallback", "reason", "timeout")
```

Final fallback stays:

```java
.tags("target", "deterministic_fallback", "reason", "failure")
```

and:

```java
.tags("source", "deterministic_fallback", "reason", "providers_exhausted")
```

The safe-log test must contain role labels and reject old provider labels:

```java
assertThat(output.getAll())
    .contains("provider=primary")
    .contains("outcome=validation_failure")
    .contains("target=remote_fallback")
    .contains("source=deterministic_fallback")
    .contains("matchId=match-123")
    .contains("triggerType=MOVE")
    .contains("triggerPly=7")
    .doesNotContain("provider=groq")
    .doesNotContain("provider=gemini")
    .doesNotContain("SECRET_PROMPT_DO_NOT_LOG")
    .doesNotContain("SECRET_RAW_RESPONSE_DO_NOT_LOG")
    .doesNotContain("SECRET_FALLBACK_DO_NOT_LOG");
```

- [ ] **Step 4: Run the focused tests before production provider rewiring**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiProviderConfigurationTest,AiConfigurationContextTest,FailoverAiChatGatewayTest,AiRuntimeHintsTest test
```

Expected: FAIL against the old provider beans/options/labels.

- [ ] **Step 5: Replace `AiProviderConfiguration` with two OpenAI-compatible OpenRouter models**

Remove all `com.google.genai.*`, `GoogleGenAiChatModel`, `GoogleGenAiChatOptions`, and Spring retry imports/helpers.

Keep one package-private helper for deterministic testing:

```java
static OpenAiChatOptions openRouterOptions(
    String apiKey, String baseUrl, String model, Duration timeout) {
  return OpenAiChatOptions.builder()
      .apiKey(apiKey)
      .baseUrl(baseUrl)
      .model(model)
      .timeout(timeout)
      .maxRetries(0)
      .build();
}
```

Create primary model/client/provider-client beans:

```java
@Bean("openRouterPrimaryChatModel")
ChatModel openRouterPrimaryChatModel(
    AiProperties properties, ObservationRegistry observationRegistry) {
  AiProperties.OpenRouter openrouter = properties.openrouter();
  return OpenAiChatModel.builder()
      .options(
          openRouterOptions(
              openrouter.apiKey(),
              openrouter.baseUrl(),
              openrouter.primaryModel(),
              openrouter.primaryTimeout()))
      .observationRegistry(observationRegistry)
      .build();
}

@Bean("openRouterPrimaryChatClient")
ChatClient openRouterPrimaryChatClient(
    @Qualifier("openRouterPrimaryChatModel") ChatModel chatModel,
    DialogueBoundaryAdvisor dialogueBoundaryAdvisor) {
  return ChatClient.builder(chatModel).defaultAdvisors(dialogueBoundaryAdvisor).build();
}

@Bean("openRouterPrimaryProviderChatClient")
ProviderChatClient openRouterPrimaryProviderChatClient(
    @Qualifier("openRouterPrimaryChatClient") ChatClient chatClient) {
  return prompt -> chatClient.prompt().user(prompt).call().content();
}
```

Create fallback model/client/provider-client beans using the same API key/base URL but `fallbackModel()` / `fallbackTimeout()`:

```java
@Bean("openRouterFallbackChatModel")
ChatModel openRouterFallbackChatModel(
    AiProperties properties, ObservationRegistry observationRegistry) {
  AiProperties.OpenRouter openrouter = properties.openrouter();
  return OpenAiChatModel.builder()
      .options(
          openRouterOptions(
              openrouter.apiKey(),
              openrouter.baseUrl(),
              openrouter.fallbackModel(),
              openrouter.fallbackTimeout()))
      .observationRegistry(observationRegistry)
      .build();
}

@Bean("openRouterFallbackChatClient")
ChatClient openRouterFallbackChatClient(
    @Qualifier("openRouterFallbackChatModel") ChatModel chatModel,
    DialogueBoundaryAdvisor dialogueBoundaryAdvisor) {
  return ChatClient.builder(chatModel).defaultAdvisors(dialogueBoundaryAdvisor).build();
}

@Bean("openRouterFallbackProviderChatClient")
ProviderChatClient openRouterFallbackProviderChatClient(
    @Qualifier("openRouterFallbackChatClient") ChatClient chatClient) {
  return prompt -> chatClient.prompt().user(prompt).call().content();
}
```

Keep the single existing `DialogueBoundaryAdvisor` bean. Do not add a provider factory/registry abstraction for two models.

- [ ] **Step 6: Inject the new provider-client beans into the gateway configuration**

Change enabled gateway construction to:

```java
@Bean
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
AiChatGateway enabledAiChatGateway(
    @Qualifier("openRouterPrimaryProviderChatClient") ProviderChatClient primary,
    @Qualifier("openRouterFallbackProviderChatClient") ProviderChatClient remoteFallback,
    AiGatewayMetrics metrics) {
  log.info(
      "AI gateway topology: enabled (OpenRouter primary -> OpenRouter fallback -> deterministic fallback)");
  return new FailoverAiChatGateway(primary, remoteFallback, metrics);
}
```

Disabled topology remains unchanged.

- [ ] **Step 7: Rename failover internals and role labels without changing the algorithm**

Change fields/constructor to:

```java
private final ProviderChatClient primary;
private final ProviderChatClient remoteFallback;
private final AiGatewayMetrics metrics;

FailoverAiChatGateway(
    ProviderChatClient primary, ProviderChatClient remoteFallback, AiGatewayMetrics metrics) {
  this.primary = Objects.requireNonNull(primary, "primary must not be null");
  this.remoteFallback =
      Objects.requireNonNull(remoteFallback, "remoteFallback must not be null");
  this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
}
```

Use this exact flow in `generate(...)`:

```java
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
```

Do not add loops or retry the primary/fallback client.

- [ ] **Step 8: Remove the Google Gen AI Spring AI starter**

Delete only this dependency from `server/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
```

Keep:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

- [ ] **Step 9: Format and run the complete focused AI suite**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=AiProviderConfigurationTest,AiConfigurationContextTest,FailoverAiChatGatewayTest,AiRuntimeHintsTest,DialogueGenerationServiceTest test
```

Expected: PASS with no network access.

- [ ] **Step 10: Prove the Google SDK is no longer in the active code/dependency graph**

Run:

```bash
git grep -n -E 'com\.google\.genai|GoogleGenAi' -- server/src/main server/src/test || true
```

Expected: no hits.

Run:

```powershell
server\mvnw.cmd -f server\pom.xml dependency:tree "-Dincludes=org.springframework.ai:spring-ai-starter-model-google-genai,com.google.genai:*"
```

Expected: no Google Gen AI starter/SDK dependency is present.

- [ ] **Step 11: Commit Tasks 2 and 3 together if Task 2 could not be green independently**

```bash
git add server/pom.xml server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal
git commit -m "feat: route AI dialogue through OpenRouter"
```

---

### Task 4: Update Runtime Configuration, Native Topology, CI, and Cost-Aware Match Pacing

**Files:**
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/.env.example`
- Modify: `server/docker-compose.yml`
- Modify: `server/Dockerfile`
- Modify: `.github/workflows/ci.yml`
- Verify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GamePropertiesBindingTest.java`

**Interfaces:**
- Runtime variables: `AI_OPENROUTER_API_KEY`, `AI_OPENROUTER_BASE_URL`, `AI_OPENROUTER_PRIMARY_MODEL`, `AI_OPENROUTER_FALLBACK_MODEL`, `AI_OPENROUTER_PRIMARY_TIMEOUT`, `AI_OPENROUTER_FALLBACK_TIMEOUT`.
- Default OpenRouter base URL: `https://openrouter.ai/api/v1`.
- Default remote fallback model: `~deepseek/deepseek-v4-flash-latest`.
- Primary model has no hard-coded production default; enabled deployments must explicitly select a specific `:free` model.
- Normal pacing defaults: `GAME_MOVE_DELAY_MIN=7s`, `GAME_MOVE_DELAY_MAX=12s`.
- Fast verification override remains `0s`/`0s` only when explicitly set.

- [ ] **Step 1: Replace the application AI configuration block**

In `application.yaml`, replace `app.ai.groq` / `app.ai.gemini` with:

```yaml
app:
  ai:
    enabled: ${AI_ENABLED:false}
    openrouter:
      api-key: ${AI_OPENROUTER_API_KEY:}
      base-url: ${AI_OPENROUTER_BASE_URL:https://openrouter.ai/api/v1}
      primary-model: ${AI_OPENROUTER_PRIMARY_MODEL:}
      fallback-model: ${AI_OPENROUTER_FALLBACK_MODEL:~deepseek/deepseek-v4-flash-latest}
      primary-timeout: ${AI_OPENROUTER_PRIMARY_TIMEOUT:8s}
      fallback-timeout: ${AI_OPENROUTER_FALLBACK_TIMEOUT:12s}
```

Keep the existing `spring.ai.chat.observations` safety settings and `spring.ai.model.*=none` settings unchanged.

- [ ] **Step 2: Change normal move-delay defaults in the same YAML**

Change:

```yaml
move-delay:
  min: ${GAME_MOVE_DELAY_MIN:3s}
  max: ${GAME_MOVE_DELAY_MAX:10s}
```

To:

```yaml
move-delay:
  min: ${GAME_MOVE_DELAY_MIN:7s}
  max: ${GAME_MOVE_DELAY_MAX:12s}
```

Do not alter move-think time, max plies, Stockfish timing, or match guard limits.

- [ ] **Step 3: Update `.env.example` to the target operator contract**

Change the match pacing lines to:

```text
GAME_MOVE_DELAY_MIN=7s
GAME_MOVE_DELAY_MAX=12s
```

Replace the Phase 2 provider variables with:

```text
# Phase 2 OpenRouter provider configuration.
# Select one specific :free model for consistent character voice; do not use openrouter/free.
AI_ENABLED=false
AI_OPENROUTER_API_KEY=<openrouter-api-key>
AI_OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
AI_OPENROUTER_PRIMARY_MODEL=inclusionai/ling-3.0-flash:free
AI_OPENROUTER_FALLBACK_MODEL=~deepseek/deepseek-v4-flash-latest
AI_OPENROUTER_PRIMARY_TIMEOUT=8s
AI_OPENROUTER_FALLBACK_TIMEOUT=12s
```

The concrete primary model in `.env.example` is an example deployment choice, not a Java/YAML hard-coded default; it may be changed later when the free catalog changes.

- [ ] **Step 4: Pass OpenRouter and pacing variables through Docker Compose**

In `server/docker-compose.yml`, remove the `AI_GROQ_*` / `AI_GEMINI_*` entries and add:

```yaml
AI_ENABLED: ${AI_ENABLED:-false}
AI_OPENROUTER_API_KEY: ${AI_OPENROUTER_API_KEY:-}
AI_OPENROUTER_BASE_URL: ${AI_OPENROUTER_BASE_URL:-https://openrouter.ai/api/v1}
AI_OPENROUTER_PRIMARY_MODEL: ${AI_OPENROUTER_PRIMARY_MODEL:-}
AI_OPENROUTER_FALLBACK_MODEL: ${AI_OPENROUTER_FALLBACK_MODEL:-~deepseek/deepseek-v4-flash-latest}
AI_OPENROUTER_PRIMARY_TIMEOUT: ${AI_OPENROUTER_PRIMARY_TIMEOUT:-8s}
AI_OPENROUTER_FALLBACK_TIMEOUT: ${AI_OPENROUTER_FALLBACK_TIMEOUT:-12s}
GAME_MOVE_DELAY_MIN: ${GAME_MOVE_DELAY_MIN:-7s}
GAME_MOVE_DELAY_MAX: ${GAME_MOVE_DELAY_MAX:-12s}
```

Keep `AI_NATIVE_BUILD_ENABLED: ${AI_ENABLED:-false}` unchanged so Compose still selects the matching AOT bean topology at build time.

- [ ] **Step 5: Replace Dockerfile AOT placeholders**

Replace the Groq/Gemini build-time environment assignments in the native compile `RUN` with:

```dockerfile
RUN AI_ENABLED="${AI_NATIVE_BUILD_ENABLED}" \
    AI_OPENROUTER_API_KEY="aot-build-placeholder-openrouter-key" \
    AI_OPENROUTER_PRIMARY_MODEL="aot-build-placeholder-primary:free" \
    AI_OPENROUTER_FALLBACK_MODEL="aot-build-placeholder-fallback" \
    ./mvnw clean package native:compile -B -DskipTests -Pnative -Plinux
```

Do not add real provider secrets as Docker `ARG` or `ENV` values.

- [ ] **Step 6: Update native CI runtime configuration and topology marker**

In `.github/workflows/ci.yml`, replace runtime provider env values with:

```bash
-e AI_ENABLED=true \
-e AI_OPENROUTER_API_KEY=ci-runtime-openrouter-key \
-e AI_OPENROUTER_PRIMARY_MODEL=ci-runtime-primary:free \
-e AI_OPENROUTER_FALLBACK_MODEL=ci-runtime-fallback \
```

Change:

```bash
enabled_marker="AI gateway topology: enabled (Groq -> Gemini)"
```

To:

```bash
enabled_marker="AI gateway topology: enabled (OpenRouter primary -> OpenRouter fallback -> deterministic fallback)"
```

Replace the baked provider check with:

```bash
if grep -Eq '^AI_OPENROUTER_(API_KEY|PRIMARY_MODEL|FALLBACK_MODEL)=' <<< "$image_env"; then
  echo "OpenRouter credential/model environment values are baked into the final image"
  exit 1
fi
```

No real HTTP provider call belongs in CI.

- [ ] **Step 7: Run configuration binding and AI context tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=GamePropertiesBindingTest,AiConfigurationContextTest,AiRuntimeHintsTest,AiProviderConfigurationTest test
```

Expected: PASS. Existing `GamePropertiesBindingTest` range validation must still accept `7s <= 12s` and reject negative/reversed ranges.

- [ ] **Step 8: Scan active deployment/runtime files for obsolete provider variables**

Run:

```bash
git grep -n -E 'AI_GROQ_|AI_GEMINI_|app\.ai\.groq|app\.ai\.gemini' -- server .github || true
```

Expected: no hits outside historical plan/spec documentation that is intentionally not part of this command.

- [ ] **Step 9: Commit**

```bash
git add server/src/main/resources/application.yaml server/.env.example server/docker-compose.yml server/Dockerfile .github/workflows/ci.yml
git commit -m "chore: configure OpenRouter runtime and match pacing"
```

---

### Task 5: Update Active Documentation and Agent Guidance

**Files:**
- Modify: `server/README.md`
- Modify: `docs/BUILD_AND_VERIFY.md`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`
- Modify: `AGENTS.md`
- Modify: `.agents/AGENTS.md`

**Interfaces:**
- Active documentation describes OpenRouter primary/fallback roles, not Groq -> Gemini.
- Documentation states that the specific primary `:free` model is deployment configuration and may change.
- Documentation records `~deepseek/deepseek-v4-flash-latest` as the initial cheap fallback candidate/default, not as a quality-maximizing choice.
- Documentation explains `7s`–`12s` pacing as both a readability choice and a hobby-budget/rate-limit choice.
- Documentation labels `0s` pacing explicitly as local/test-only fast verification.

- [ ] **Step 1: Update the server README match-pacing section**

Replace the old `3s`/`10s` defaults with `7s`/`12s` and add these two reasons in plain language:

```text
1. viewers have enough time to read/react to dialogue;
2. normal gameplay stays comfortably below OpenRouter's 20 RPM free-model limit and reduces fallback spend.
```

Keep the existing statement that setting both values to `0s` is for fast local/integration-style verification only.

- [ ] **Step 2: Replace the server README native AI provider contract**

Document that:

```text
- Native AOT builds compile the AI-enabled topology with non-secret OpenRouter placeholders.
- Runtime uses one AI_OPENROUTER_API_KEY and AI_OPENROUTER_BASE_URL.
- AI_OPENROUTER_PRIMARY_MODEL must be a specific :free model, not openrouter/free.
- AI_OPENROUTER_FALLBACK_MODEL defaults to ~deepseek/deepseek-v4-flash-latest and is selected for low cost/reliability, not benchmark leadership.
- Primary/fallback timeouts default to 8s/12s and each model gets no same-model retry.
```

Replace the Render environment list with the six OpenRouter variables from Task 4. Remove Groq/Gemini runtime instructions.

- [ ] **Step 3: Rewrite the Phase 2 provider sections in `docs/BUILD_AND_VERIFY.md`**

Keep the existing repository verification commands. Replace Groq/Gemini setup/failover instructions with OpenRouter equivalents:

```text
AI_ENABLED=true
AI_OPENROUTER_API_KEY=<secret supplied only in the local shell or deployment secret store>
AI_OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
AI_OPENROUTER_PRIMARY_MODEL=inclusionai/ling-3.0-flash:free
AI_OPENROUTER_FALLBACK_MODEL=~deepseek/deepseek-v4-flash-latest
AI_OPENROUTER_PRIMARY_TIMEOUT=8s
AI_OPENROUTER_FALLBACK_TIMEOUT=12s
```

Do not put a real key in the file.

Document two manual acceptance modes:

```text
Primary-success run:
- use a currently available specific :free primary model;
- start one short provider-enabled match;
- confirm provider=primary outcome=success and source=primary/REMOTE_PRIMARY;
- confirm no fallback activation is emitted for a successful primary response.

Controlled remote-fallback run:
- keep the real OpenRouter key and real fallback model;
- set AI_OPENROUTER_PRIMARY_MODEL=invalid/forced-primary-failure:free;
- restart the backend;
- start one short match;
- confirm primary failure activates target=remote_fallback exactly once;
- confirm remote_fallback succeeds and the match/dialogue continue;
- restore the real primary model after the run.
```

The intentionally invalid primary ID ends in `:free`, so startup policy validation passes while OpenRouter rejects the model at request time. Do not add a production-only failure flag.

- [ ] **Step 4: Update the Tech Stack document**

In `docs/AI Chess Rivals - Tech Stack.md`:

```text
- Keep Spring AI 2.0.0 and spring-ai-starter-model-openai.
- Remove spring-ai-starter-model-google-genai from the dependency inventory.
- Describe OpenRouter as the single OpenAI-compatible remote API surface.
- Describe the remote policy as specific :free primary -> ultra-low-cost fallback -> deterministic local fallback.
- Update match pacing defaults from 3s–10s to 7s–12s.
```

Do not alter unrelated dependency versions.

- [ ] **Step 5: Update both active agent-guideline files**

In `AGENTS.md` and `.agents/AGENTS.md`, replace active statements like:

```text
Groq is primary ... Gemini is the only automatic fallback
```

with:

```text
Phase 2 uses Spring AI through OpenRouter's OpenAI-compatible API. A specific configurable :free model is the remote primary, one configurable ultra-low-cost model is the single remote fallback, and deterministic personality-specific dialogue is the final fallback. LLMs still never choose chess moves.
```

Keep all simplicity/no-framework rules.

- [ ] **Step 6: Confirm historical plans were not rewritten**

Run:

```bash
git status --short docs/superpowers/plans docs/superpowers/specs
```

Expected: only this issue's new plan is present under `docs/superpowers/plans`; no old completed plan/spec was mass-edited.

- [ ] **Step 7: Commit active documentation updates**

```bash
git add server/README.md docs/BUILD_AND_VERIFY.md "docs/AI Chess Rivals - Tech Stack.md" AGENTS.md .agents/AGENTS.md
git commit -m "docs: document OpenRouter provider policy"
```

---

### Task 6: Run Repository/Native Verification and Record Real OpenRouter Acceptance

**Files:**
- Modify after observations: `docs/BUILD_AND_VERIFY.md` — append/update the dated Issue #62 acceptance record with only checks actually performed.
- No production code should be introduced in this task unless verification exposes a real defect.

**Interfaces:**
- Automated verification proves no network call is required.
- Native verification proves the AI-enabled AOT topology starts with placeholder values and does not bake provider credentials/models into the final image.
- Manual acceptance proves one real primary OpenRouter call and one forced-primary-failure -> paid remote fallback success path.

- [ ] **Step 1: Format everything before verification**

From repository root:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
cd client
npm.cmd run format
cd ..
```

- [ ] **Step 2: Run the focused backend regression suite**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest,AiProviderConfigurationTest,AiConfigurationContextTest,FailoverAiChatGatewayTest,DialogueGenerationServiceTest,DialoguePersistenceServiceTest,MatchDialogueCoordinatorTest,GamePropertiesBindingTest test
```

Expected: PASS, zero real provider requests.

- [ ] **Step 3: Run whole-repository verification**

Run:

```powershell
.\scripts\verify.ps1
```

Expected: backend and frontend verification PASS.

POSIX equivalent:

```bash
./scripts/verify.sh
```

- [ ] **Step 4: Build the production native image with the AI-enabled topology**

Run from `server/`:

```powershell
docker build --build-arg AI_NATIVE_BUILD_ENABLED=true -t ai-chess-rivals:issue-62 .
```

Expected: native image build PASS without real provider credentials and without any provider HTTP call.

- [ ] **Step 5: Inspect the final image environment**

Run:

```powershell
docker image inspect ai-chess-rivals:issue-62 --format '{{range .Config.Env}}{{println .}}{{end}}'
```

Expected: no `AI_OPENROUTER_API_KEY`, `AI_OPENROUTER_PRIMARY_MODEL`, or `AI_OPENROUTER_FALLBACK_MODEL` value is baked into the runtime image.

- [ ] **Step 6: Perform one real primary-success acceptance run**

Use the normal local topology documented in `docs/BUILD_AND_VERIFY.md`. Put the real OpenRouter key only in the shell/session or untracked `.env`.

For the primary model, `inclusionai/ling-3.0-flash:free` was verified as a specific free OpenRouter model when this plan was written on 2026-08-23. If it is no longer available when executing the plan, choose another currently available specific `:free` model, record its exact ID in the acceptance record, and do **not** use `openrouter/free`.

For a fast manual verification only, override:

```text
GAME_MOVE_DELAY_MIN=0s
GAME_MOVE_DELAY_MAX=0s
```

Start a match from `/admin`. Confirm in logs/metrics:

```text
provider=primary outcome=success
ai.gateway.responses source=primary reason=primary
```

Confirm dialogue is persisted/streamed and the match continues. Do not copy prompts, raw responses, or the API key into the acceptance record.

- [ ] **Step 7: Perform one controlled paid-fallback acceptance run**

Restart the backend with:

```text
AI_OPENROUTER_PRIMARY_MODEL=invalid/forced-primary-failure:free
AI_OPENROUTER_FALLBACK_MODEL=~deepseek/deepseek-v4-flash-latest
```

Keep the same real OpenRouter key. Start a short match. Confirm the sequence:

```text
provider=primary outcome=failure
fallback activated target=remote_fallback reason=failure
provider=remote_fallback outcome=success
response selected source=remote_fallback reason=fallback
```

Confirm the returned/persisted dialogue has `source=REMOTE_FALLBACK` and the match continues. Confirm there is exactly one primary call and one remote-fallback call per failed primary dialogue generation; there must be no same-model retry loop.

Restore the real primary model immediately after the run.

- [ ] **Step 8: Verify deterministic fallback remains covered without spending money**

Do not deliberately burn provider requests just to make both remote calls fail if the network-free `FailoverAiChatGatewayTest#bothRemoteAttemptsFailReturnsDeterministicFallback` remains green. That automated test plus the existing match/dialogue resilience test is sufficient proof for the final fallback behavior unless a real runtime discrepancy is observed.

- [ ] **Step 9: Scan active code/config/docs for stale provider policy**

Run:

```bash
git grep -n -E 'AI_GROQ_|AI_GEMINI_|app\.ai\.groq|app\.ai\.gemini|enabled \(Groq -> Gemini\)' -- server .github AGENTS.md .agents docs/BUILD_AND_VERIFY.md "docs/AI Chess Rivals - Tech Stack.md" || true
```

Expected: no hits.

Run:

```bash
git grep -n -E 'com\.google\.genai|GoogleGenAi|spring-ai-starter-model-google-genai' -- server/pom.xml server/src || true
```

Expected: no hits.

- [ ] **Step 10: Record only observed acceptance results**

Add a dated `2026-08-23` (or actual execution date) Issue #62 acceptance subsection in `docs/BUILD_AND_VERIFY.md`. Record:

```text
- exact specific free primary model ID used;
- fallback model ID used;
- primary-success observed/not observed;
- controlled primary failure -> remote fallback success observed/not observed;
- match continued after fallback;
- JVM verification result;
- native image verification result;
- confirmation that secrets/prompts/raw responses were not logged;
- normal deployment pacing remains 7s–12s; 0s was used only for the acceptance run if applicable.
```

Do not pre-check anything that was not actually run.

- [ ] **Step 11: Commit the acceptance record**

```bash
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: record OpenRouter acceptance"
```

- [ ] **Step 12: Final clean-state verification**

Run:

```bash
git status --short
git log --oneline -6
```

Expected: working tree clean; commits show the response-source neutralization, OpenRouter migration, runtime/pacing update, documentation update, and acceptance record.

---

## Definition of Done

- [ ] One OpenRouter API key/base URL powers both remote model attempts.
- [ ] Primary is a specific configurable `:free` model; `openrouter/free` is rejected in enabled mode.
- [ ] Remote fallback is independently configurable and defaults to `~deepseek/deepseek-v4-flash-latest` unless deployment deliberately selects another ultra-low-cost model.
- [ ] Primary and fallback use Spring AI `OpenAiChatModel` with `maxRetries(0)` and independently configured `8s` / `12s` timeout defaults.
- [ ] Google Gen AI SDK/chat-model dependency and code path are absent from active source/dependencies.
- [ ] Structured-output validation still gates accepted dialogue.
- [ ] Primary failure/timeout/validation failure triggers exactly one remote fallback attempt.
- [ ] Both remote attempts failing still returns deterministic personality-specific dialogue and does not stop the match.
- [ ] Metrics/logs use `primary`, `remote_fallback`, and `deterministic_fallback`, with no new Groq/Gemini provider tags.
- [ ] Persisted/API response sources use `REMOTE_PRIMARY`, `REMOTE_FALLBACK`, and `DETERMINISTIC_FALLBACK`; existing rows are migrated without deletion.
- [ ] Automated provider/resilience tests make no real network calls.
- [ ] Normal move pacing defaults to `7s`–`12s`; fast verification can explicitly override both to `0s`.
- [ ] Docker Compose, Dockerfile AOT placeholders, CI native topology verification, `.env.example`, and active docs use the OpenRouter variables.
- [ ] Whole-repository JVM/frontend verification passes.
- [ ] AI-enabled GraalVM native image builds/starts with placeholder topology values and no baked OpenRouter credential/model environment values.
- [ ] One real primary-success match and one controlled primary-failure -> paid-fallback-success run are recorded without leaking credentials/prompt/response content.

## Scope Guard Reminder

If implementation reveals a temptation to add rate limiting, provider registries, runtime model discovery, retries, multi-agent orchestration, or a new persistence abstraction, stop. Those are not required by #62. The intended implementation is a small provider-plumbing simplification that lowers hobby-project cost and maintenance while keeping the entertaining dialogue flow unchanged.
