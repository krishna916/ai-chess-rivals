# PR #56 AI Properties Native Reflection Fix Design

## Problem

PR #56 now builds the production GraalVM native image successfully and starts the AI-enabled AOT topology, but the native process exits during `AiProperties` validation.

The failing runtime path is:

`dialogueGenerationService -> enabledAiChatGateway -> groqProviderChatClient -> groqChatClient -> groqChatModel -> AiProperties`

The root cause from the GitHub Actions log is `org.graalvm.nativeimage.MissingReflectionRegistrationError` for `AiProperties.isEnabledConfigurationComplete()`. The same configuration-properties record also contains two other `@AssertTrue` validation methods that Hibernate Validator may invoke reflectively:

- `AiProperties.isEnabledConfigurationComplete()`
- `AiProperties.Groq.isTimeoutPositive()`
- `AiProperties.Gemini.isTimeoutWithinHttpOptionsRange()`

The JVM configuration-binding tests pass because ordinary JVM reflection does not require GraalVM reachability metadata.

## Goal

Allow Hibernate Validator to invoke the three existing `@AssertTrue` methods in the native image without changing the AI provider architecture, AOT topology selection, Docker build strategy, runtime environment contract, or validation semantics.

## Approaches Considered

### 1. Raw GraalVM `reachability-metadata.json`

This would directly follow the GraalVM error message, but it introduces hand-maintained native metadata outside Spring's AOT model and is harder to test at the application level.

### 2. Broad `@RegisterReflection`

Registering all public methods on `AiProperties`, `AiProperties.Groq`, and `AiProperties.Gemini` would be small, but it grants more reflective surface than the failure requires and does not give us as clean a focused unit test for the exact methods.

### 3. Spring `RuntimeHintsRegistrar` with exact method invocation hints — selected

Create one tiny package-local `RuntimeHintsRegistrar`, import it from `AiConfig`, and register only the three validation methods with `ExecutableMode.INVOKE`. Test the registrar with Spring's `RuntimeHintsPredicates` so the native-reflection contract is explicit and regression-resistant.

## Design

### `AiRuntimeHints`

Add `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`.

The class remains package-private and implements `RuntimeHintsRegistrar`. It registers invocation hints for exactly these no-argument methods:

- `AiProperties#isEnabledConfigurationComplete`
- `AiProperties.Groq#isTimeoutPositive`
- `AiProperties.Gemini#isTimeoutWithinHttpOptionsRange`

Use Spring's `RuntimeHints` / `ExecutableMode.INVOKE`; do not add GraalVM JSON metadata.

### `AiConfig`

Keep `AiConfig` as the existing configuration-properties activation point and add `@ImportRuntimeHints(AiRuntimeHints.class)`. This keeps the native hint registration colocated with the AI configuration and lets Spring AOT contribute the hints only when that configuration participates in the application.

### Tests

Add `AiRuntimeHintsTest` beside `AiPropertiesBindingTest`. Instantiate `RuntimeHints`, invoke the registrar directly, and assert with `RuntimeHintsPredicates.reflection().onMethodInvocation(...)` that all three validation methods are invokable.

Existing `AiPropertiesBindingTest` remains unchanged and continues to verify validation behavior on the JVM.

The hosted `CI / Native image verification` job remains the authoritative artifact-level test because it builds and runs the actual GraalVM binary.

## Scope Boundaries

Do not change:

- `AiProperties` validation rules or method names
- `AiProviderConfiguration`
- `AiGatewayConfiguration`
- `FailoverAiChatGateway`
- `DisabledAiChatGateway`
- `server/Dockerfile`
- `server/docker-compose.yml`
- `.github/workflows/ci.yml`
- build-time `AI_NATIVE_BUILD_ENABLED=true`
- runtime fake provider values used by CI
- gateway startup markers or their assertions
- final-image environment leak assertion

Do not add:

- provider registries or factories
- new diagnostics endpoints
- broad reflection metadata when exact method hints are sufficient
- native tracing-agent infrastructure
- new dependencies unless compilation proves Spring's existing AOT APIs are unavailable

## Verification

1. Add a focused failing runtime-hints test first.
2. Add the minimal registrar and import.
3. Run the focused runtime-hints and AI properties tests.
4. Run the repository backend verification gate.
5. Push and use the hosted `Native image verification` job as the authoritative native-runtime check.

If the next native run fails with a different `MissingReflectionRegistrationError`, stop and inspect that exact method/type before adding more hints. Do not widen reflection registration speculatively.
