# Spring AI Provider Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Spring AI 2.0 provider foundation that uses Groq first, Gemini exactly once as fallback, and a deterministic caller-supplied fallback when AI is disabled or both providers fail.

**Architecture:** Create one small top-level `ai` Spring Modulith module. Build two explicitly named Spring AI `ChatModel`/`ChatClient` pairs because both providers must coexist at runtime: Groq through the OpenAI-compatible model and Gemini through Google GenAI. Keep routing fixed and explicit behind one application-facing `AiChatGateway`; do not build a provider registry, plugin SPI, dynamic strategy framework, persistence layer, prompt system, or dialogue policy in this issue.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring AI 2.0.0, `spring-ai-starter-model-openai`, `spring-ai-starter-model-google-genai`, Google GenAI Java SDK transitively supplied by Spring AI, Jakarta Bean Validation, JUnit 5, AssertJ, Spring Boot `ApplicationContextRunner`, GraalVM Native Image, Docker.

## Source of Truth

- Issue: `#38 Phase 2: Add Spring AI provider foundation with Groq-to-Gemini failover`
- Approved design: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Current dependency inventory: `docs/AI Chess Rivals - Tech Stack.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Spring AI is the required Phase 2 LLM integration layer.
- Use Spring AI `2.0.0`, which is compatible with this repository's Spring Boot `4.1.0` baseline.
- Groq is always the primary provider in this issue; Gemini is always the only automatic fallback. Do not add runtime provider selection, a provider registry, a generic provider interface exposed outside the module, or a plugin framework.
- Groq uses the OpenAI-compatible endpoint `https://api.groq.com/openai/v1` by default.
- Groq request timeout is `8s`; Gemini request timeout is `12s`.
- Disable provider-internal retries. One gateway call means at most one Groq HTTP attempt and at most one Gemini HTTP attempt.
- Invalid Groq output must be treated exactly like Groq failure: invoke Gemini once. Invalid Gemini output must produce the deterministic fallback.
- The gateway must accept an `AiResponseValidator` so issue #42 can later supply structured-output validation without changing failover routing. #38 itself only needs the supplied validator plus a reusable `nonBlank()` validator.
- The deterministic fallback string is supplied by the caller. Do not hard-code character lines here; issue #41 owns personality fallback content and issue #42 owns dialogue prompting/structured output.
- AI is disabled by default. Disabled mode starts without provider credentials and returns the caller-supplied deterministic fallback immediately.
- If `app.ai.enabled=true`, missing Groq/Gemini credentials or model names must fail application startup through validated configuration properties.
- Model names must come from environment-backed configuration. Do not hard-code a Groq or Gemini model ID in Java.
- Do not add a REST controller or test endpoint for AI. Later Phase 2 workflow code will consume the gateway internally.
- Do not add personality prompts, dialogue policy, persistence, Stockfish changes, UI changes, tool calling, chat memory, agents, Advisors, or multi-step workflows in this issue.
- Do not add `SimpleLoggerAdvisor` and do not log prompts or model response bodies. Observability metadata is issue #46.
- Do not add a second retry library, circuit breaker, Resilience4j, scheduler, executor, or async orchestration layer.
- Keep `spring.ai.model.chat=none` so starter auto-configuration does not select one provider and conflict with the two manually named models.
- Because `pom.xml` and backend configuration change, keep `docs/AI Chess Rivals - Tech Stack.md` and `server/.env.example` synchronized in the same implementation.
- Follow repository formatting rules: run Spotless before verification.
- Automated tests must use in-process stubs/fakes and dummy credentials only; they must never contact Groq or Gemini.
- Keep GraalVM Native Image compatibility in scope. Prefer Spring AI/Google SDK built-in runtime hints; add custom runtime hints only if the native build proves they are required.

## File Map

**Create:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/package-info.java` — declares the new Spring Modulith `ai` module.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatGateway.java` — narrow application-facing generation boundary.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatRequest.java` — prompt plus deterministic caller fallback.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatResult.java` — accepted text plus source metadata.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseSource.java` — `GROQ`, `GEMINI`, or `DETERMINISTIC_FALLBACK`.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseValidator.java` — tiny validation seam used to trigger provider failover.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java` — activates validated AI configuration properties.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java` — typed `app.ai` configuration.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/ProviderChatClient.java` — package-private test seam around Spring AI `ChatClient`; not an application SPI.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java` — creates the two named provider models, clients, and internal adapters only when AI is enabled.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java` — fixed Groq -> Gemini -> deterministic fallback routing.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java` — exposes exactly one enabled or disabled `AiChatGateway` bean.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/DisabledAiChatGateway.java` — no-provider implementation for local/test/default mode.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java` — startup/binding contract.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java` — timeout/retry/model bean configuration without network calls.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java` — deterministic routing and call-count tests.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java` — enabled/disabled bean topology smoke tests.

**Modify:**

- `server/pom.xml` — Spring AI BOM and provider starters.
- `server/src/main/resources/application.yaml` — disable single-provider auto-configuration and add `app.ai` settings.
- `server/.env.example` — document safe AI environment variables.
- `server/docker-compose.yml` — pass AI environment variables to the backend container.
- `docs/AI Chess Rivals - Tech Stack.md` — move Spring AI/provider starters from planned to current and record version `2.0.0`.

**Do not modify for #38:**

- Game/chess packages.
- Flyway migrations.
- Frontend files.
- `docs/BUILD_AND_VERIFY.md` — issue #46 owns full Phase 2 setup/acceptance documentation.

---

### Task 1: Add Spring AI 2.0 Dependencies and Update the Technology Inventory

**Files:**
- Modify: `server/pom.xml`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`

**Interfaces:**
- Consumes: Spring Boot `4.1.0` and existing `dependencyManagement` in `server/pom.xml`.
- Produces: Spring AI `2.0.0` model classes for Groq/OpenAI-compatible and Google GenAI used by all later tasks.

- [ ] **Step 1: Add the Spring AI version property**

In `server/pom.xml`, add this alongside the existing version properties:

```xml
<spring-ai.version>2.0.0</spring-ai.version>
```

Do not change Spring Boot, Java, or Spring Modulith versions.

- [ ] **Step 2: Import the Spring AI BOM**

Inside the existing `<dependencyManagement><dependencies>` block, keep the Modulith BOM and add:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

- [ ] **Step 3: Add the two provider starters**

Inside `<dependencies>`, add exactly:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-google-genai</artifactId>
</dependency>
```

Do not add OpenAI/Groq/Google SDK versions directly; the Spring AI BOM owns them.

- [ ] **Step 4: Verify dependency resolution before writing application code**

Run:

```bash
./server/mvnw -f server/pom.xml dependency:tree -Dincludes=org.springframework.ai:*
```

Expected: the dependency tree contains Spring AI `2.0.0` artifacts including the OpenAI and Google GenAI model integrations and Maven exits `0`.

- [ ] **Step 5: Update the Tech Stack document in the same change**

In `docs/AI Chess Rivals - Tech Stack.md`:

1. Change `Spring AI planned for Phase 2` to `Spring AI 2.0.0` in the backend summary.
2. Replace the sentence saying Spring AI is not yet a repository dependency with a factual statement that issue #38 adds it as the Phase 2 provider layer.
3. Rename `### Planned Phase 2 Dependencies` to `### Spring AI Phase 2 Dependencies`.
4. Replace each `Planned` status with `Current` and add version `2.0.0 (BOM-managed)` for the two starters.
5. Keep the architecture text stating Groq is primary and Gemini is the only automatic fallback.

The resulting table should read:

```markdown
### Spring AI Phase 2 Dependencies

| Artifact ID | Group ID | Version | Status | Purpose |
|---|---|---|---|---|
| `spring-ai-bom` | `org.springframework.ai` | `2.0.0` | Current | Align Spring AI module versions |
| `spring-ai-starter-model-openai` | `org.springframework.ai` | BOM-managed | Current | OpenAI-compatible model integration used for Groq |
| `spring-ai-starter-model-google-genai` | `org.springframework.ai` | BOM-managed | Current | Google GenAI model integration used for Gemini fallback |
```

- [ ] **Step 6: Run Maven compile**

```bash
./server/mvnw -f server/pom.xml -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit the dependency baseline**

```bash
git add server/pom.xml "docs/AI Chess Rivals - Tech Stack.md"
git commit -m "build: add spring ai provider dependencies"
```

---

### Task 2: Add the AI Module and Validated Runtime Configuration

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/package-info.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java`
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/.env.example`
- Modify: `server/docker-compose.yml`

**Interfaces:**
- Consumes: Spring Boot configuration-properties infrastructure and existing repository env conventions.
- Produces: `AiProperties` with `enabled()`, `groq()`, and `gemini()` settings used by provider construction in Task 3.

- [ ] **Step 1: Declare the new Modulith module**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule
package dev.krishnamurti.ai_chess_rivals.ai;
```

The AI module has no dependency on `game` or `chess` in #38.

- [ ] **Step 2: Write the failing configuration binding test**

Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class AiPropertiesBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(AiConfig.class)
          .withPropertyValues(
              "app.ai.groq.base-url=https://api.groq.com/openai/v1",
              "app.ai.groq.timeout=8s",
              "app.ai.gemini.timeout=12s");

  @Test
  void disabledModeStartsWithoutProviderCredentials() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(AiProperties.class).enabled()).isFalse();
            });
  }

  @Test
  void enabledModeBindsProviderModelsAndTimeouts() {
    contextRunner
        .withPropertyValues(
            "app.ai.enabled=true",
            "app.ai.groq.api-key=test-groq-key",
            "app.ai.groq.model=test-groq-model",
            "app.ai.gemini.api-key=test-gemini-key",
            "app.ai.gemini.model=test-gemini-model")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AiProperties properties = context.getBean(AiProperties.class);
              assertThat(properties.groq().model()).isEqualTo("test-groq-model");
              assertThat(properties.groq().timeout()).isEqualTo(Duration.ofSeconds(8));
              assertThat(properties.gemini().model()).isEqualTo("test-gemini-model");
              assertThat(properties.gemini().timeout()).isEqualTo(Duration.ofSeconds(12));
            });
  }

  @Test
  void enabledModeRejectsMissingCredentialsAndModels() {
    contextRunner
        .withPropertyValues("app.ai.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsNonPositiveProviderTimeout() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false", "app.ai.groq.timeout=0s")
        .run(context -> assertThat(context).hasFailed());
  }
}
```

- [ ] **Step 3: Run the test and confirm it fails because AI configuration does not exist yet**

```bash
./server/mvnw -f server/pom.xml -Dtest=AiPropertiesBindingTest test
```

Expected: test compilation fails because `AiConfig`/`AiProperties` do not exist.

- [ ] **Step 4: Implement `AiProperties`**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Strongly typed configuration for the bounded Phase 2 AI provider chain. */
@ConfigurationProperties(prefix = "app.ai")
@Validated
public record AiProperties(
    boolean enabled, @Valid @NotNull Groq groq, @Valid @NotNull Gemini gemini) {

  @AssertTrue(
      message =
          "When app.ai.enabled=true, Groq/Gemini API keys and model names must be configured")
  public boolean isEnabledConfigurationComplete() {
    return !enabled
        || (hasText(groq.apiKey())
            && hasText(groq.model())
            && hasText(groq.baseUrl())
            && hasText(gemini.apiKey())
            && hasText(gemini.model()));
  }

  public record Groq(String apiKey, String baseUrl, String model, @NotNull Duration timeout) {
    @AssertTrue(message = "app.ai.groq.timeout must be greater than zero")
    public boolean isTimeoutPositive() {
      return timeout == null || (!timeout.isNegative() && !timeout.isZero());
    }
  }

  public record Gemini(String apiKey, String model, @NotNull Duration timeout) {
    @AssertTrue(message = "app.ai.gemini.timeout must be greater than zero")
    public boolean isTimeoutPositive() {
      return timeout == null || (!timeout.isNegative() && !timeout.isZero());
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
```

- [ ] **Step 5: Activate the configuration properties**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activates configuration properties for the AI module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {}
```

- [ ] **Step 6: Add the application configuration contract**

In `server/src/main/resources/application.yaml`, under `spring:`, add:

```yaml
  ai:
    model:
      chat: none
```

This intentionally disables single-provider Spring AI chat auto-configuration because #38 manually creates both providers.

Under the existing `app:` section, add:

```yaml
  ai:
    enabled: ${AI_ENABLED:false}
    groq:
      api-key: ${AI_GROQ_API_KEY:}
      base-url: ${AI_GROQ_BASE_URL:https://api.groq.com/openai/v1}
      model: ${AI_GROQ_MODEL:}
      timeout: 8s
    gemini:
      api-key: ${AI_GEMINI_API_KEY:}
      model: ${AI_GEMINI_MODEL:}
      timeout: 12s
```

Keep the timeouts fixed in application configuration for this issue; only provider credentials/model identifiers and Groq's compatible base URL are environment-backed.

- [ ] **Step 7: Synchronize `server/.env.example`**

Append:

```dotenv
# Phase 2 AI provider configuration (AI stays disabled unless explicitly enabled)
AI_ENABLED=false
AI_GROQ_API_KEY=<groq-api-key>
AI_GROQ_BASE_URL=https://api.groq.com/openai/v1
AI_GROQ_MODEL=<groq-model-name>
AI_GEMINI_API_KEY=<gemini-api-key>
AI_GEMINI_MODEL=<gemini-model-name>
```

Do not put real credentials or realistic-looking secrets in the example file.

- [ ] **Step 8: Pass AI environment settings into the Docker backend**

Add these entries to `server/docker-compose.yml` under `backend.environment`:

```yaml
      AI_ENABLED: ${AI_ENABLED:-false}
      AI_GROQ_API_KEY: ${AI_GROQ_API_KEY:-}
      AI_GROQ_BASE_URL: ${AI_GROQ_BASE_URL:-https://api.groq.com/openai/v1}
      AI_GROQ_MODEL: ${AI_GROQ_MODEL:-}
      AI_GEMINI_API_KEY: ${AI_GEMINI_API_KEY:-}
      AI_GEMINI_MODEL: ${AI_GEMINI_MODEL:-}
```

Do not add API keys to the Docker image or Dockerfile.

- [ ] **Step 9: Format and run the binding test**

```bash
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=AiPropertiesBindingTest test
```

Expected: all four tests pass.

- [ ] **Step 10: Commit the AI configuration contract**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai \
  server/src/main/resources/application.yaml server/.env.example server/docker-compose.yml
git commit -m "feat: add validated ai provider configuration"
```

---

### Task 3: Build Explicit Groq and Gemini Spring AI Clients with Retries Disabled

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/ProviderChatClient.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java`

**Interfaces:**
- Consumes: `AiProperties` from Task 2.
- Produces: named beans `groqChatModel`, `groqChatClient`, `geminiChatModel`, `geminiChatClient`, plus package-private `groqProviderChatClient` and `geminiProviderChatClient` adapters used by Task 4.

- [ ] **Step 1: Write the failing provider configuration test**

Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.HttpOptions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.retry.RetryTemplate;

class AiProviderConfigurationTest {

  @Test
  void groqOptionsUseConfiguredEndpointModelTimeoutAndNoRetries() {
    OpenAiChatOptions options =
        AiProviderConfiguration.groqOptions(
            "test-groq-key",
            "https://api.groq.com/openai/v1",
            "test-groq-model",
            Duration.ofSeconds(8));

    assertThat(options.getApiKey()).isEqualTo("test-groq-key");
    assertThat(options.getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
    assertThat(options.getModel()).isEqualTo("test-groq-model");
    assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(8));
    assertThat(options.getMaxRetries()).isZero();
  }

  @Test
  void geminiHttpOptionsUseTwelveSecondTimeoutAndSingleSdkAttempt() {
    HttpOptions options = AiProviderConfiguration.geminiHttpOptions(Duration.ofSeconds(12));

    assertThat(options.timeout()).contains(12_000);
    assertThat(options.retryOptions()).isPresent();
    assertThat(options.retryOptions().orElseThrow().attempts()).contains(1);
  }

  @Test
  void springRetryTemplateDoesNotRetryGeminiCall() {
    RetryTemplate retryTemplate = AiProviderConfiguration.noRetryTemplate();
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                retryTemplate.execute(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("provider failure");
                    }))
        .isInstanceOf(RuntimeException.class);

    assertThat(attempts).hasValue(1);
  }
}
```

- [ ] **Step 2: Run the test and confirm the provider configuration class is missing**

```bash
./server/mvnw -f server/pom.xml -Dtest=AiProviderConfigurationTest test
```

Expected: test compilation fails because `AiProviderConfiguration` does not exist.

- [ ] **Step 3: Add the tiny internal ChatClient adapter seam**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/ProviderChatClient.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.internal;

/** Internal seam that keeps provider failover tests independent of real network clients. */
@FunctionalInterface
interface ProviderChatClient {
  String complete(String prompt);
}
```

This is deliberately package-private. Do not turn it into a public provider SPI, registry, or dynamic plugin API.

- [ ] **Step 4: Implement the explicit provider configuration**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.internal;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.krishnamurti.ai_chess_rivals.ai.config.AiProperties;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
class AiProviderConfiguration {

  @Bean("groqChatModel")
  ChatModel groqChatModel(AiProperties properties) {
    AiProperties.Groq groq = properties.groq();
    return OpenAiChatModel.builder()
        .options(groqOptions(groq.apiKey(), groq.baseUrl(), groq.model(), groq.timeout()))
        .build();
  }

  @Bean("groqChatClient")
  ChatClient groqChatClient(@Qualifier("groqChatModel") ChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }

  @Bean("groqProviderChatClient")
  ProviderChatClient groqProviderChatClient(
      @Qualifier("groqChatClient") ChatClient chatClient) {
    return prompt -> chatClient.prompt().user(prompt).call().content();
  }

  @Bean("geminiChatModel")
  ChatModel geminiChatModel(AiProperties properties) {
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
        .build();
  }

  @Bean("geminiChatClient")
  ChatClient geminiChatClient(@Qualifier("geminiChatModel") ChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }

  @Bean("geminiProviderChatClient")
  ProviderChatClient geminiProviderChatClient(
      @Qualifier("geminiChatClient") ChatClient chatClient) {
    return prompt -> chatClient.prompt().user(prompt).call().content();
  }

  static OpenAiChatOptions groqOptions(
      String apiKey, String baseUrl, String model, Duration timeout) {
    return OpenAiChatOptions.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .model(model)
        .timeout(timeout)
        .maxRetries(0)
        .build();
  }

  static HttpOptions geminiHttpOptions(Duration timeout) {
    return HttpOptions.builder()
        .timeout(Math.toIntExact(timeout.toMillis()))
        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
        .build();
  }

  static RetryTemplate noRetryTemplate() {
    return new RetryTemplate(RetryPolicy.builder().maxRetries(0).build());
  }
}
```

Why both retry controls exist for Gemini: the Google GenAI SDK has its own HTTP retry policy and `GoogleGenAiChatModel` also accepts a Spring retry template. Set the SDK to one total attempt and Spring retry to zero retries so the gateway, not provider internals, owns fallback.

- [ ] **Step 5: Format and run the provider configuration tests**

```bash
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=AiProviderConfigurationTest test
```

Expected: all three tests pass.

- [ ] **Step 6: Commit the provider clients**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfigurationTest.java
git commit -m "feat: configure groq and gemini chat clients"
```

---

### Task 4: Implement the Narrow Validation-Aware Failover Gateway

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatGateway.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatRequest.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatResult.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseSource.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseValidator.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/DisabledAiChatGateway.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java`
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java`

**Interfaces:**
- Consumes: `groqProviderChatClient` and `geminiProviderChatClient` from Task 3.
- Produces: one public `AiChatGateway.generate(AiChatRequest, AiResponseValidator)` boundary for later Phase 2 dialogue workflow code.

- [ ] **Step 1: Write the failing routing tests**

Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java`:

```java
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
        new FailoverAiChatGateway(
            failing(groqCalls), returning("gemini response", geminiCalls));

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
        new FailoverAiChatGateway(
            returning("invalid", groqCalls), returning("valid", geminiCalls));
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
        new FailoverAiChatGateway(
            returning("", groqCalls), returning("", geminiCalls));

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
```

- [ ] **Step 2: Run the test and confirm the public gateway types are missing**

```bash
./server/mvnw -f server/pom.xml -Dtest=FailoverAiChatGatewayTest test
```

Expected: test compilation fails because the `ai.api` types and gateway implementation do not exist.

- [ ] **Step 3: Add the public API records and enum**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatRequest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

public record AiChatRequest(String prompt, String deterministicFallback) {
  public AiChatRequest {
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("prompt must not be blank");
    }
    if (deterministicFallback == null || deterministicFallback.isBlank()) {
      throw new IllegalArgumentException("deterministicFallback must not be blank");
    }
  }
}
```

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseSource.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

public enum AiResponseSource {
  GROQ,
  GEMINI,
  DETERMINISTIC_FALLBACK
}
```

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatResult.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Objects;

public record AiChatResult(String content, AiResponseSource source) {
  public AiChatResult {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    Objects.requireNonNull(source, "source must not be null");
  }
}
```

- [ ] **Step 4: Add the validation seam**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiResponseValidator.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

@FunctionalInterface
public interface AiResponseValidator {
  boolean isValid(String response);

  static AiResponseValidator nonBlank() {
    return response -> response != null && !response.isBlank();
  }
}
```

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/AiChatGateway.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.api;

public interface AiChatGateway {
  AiChatResult generate(AiChatRequest request, AiResponseValidator validator);
}
```

Do not add provider names, model IDs, Spring AI types, retries, prompts, or persistence to the public API.

- [ ] **Step 5: Implement the fixed failover chain**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java`:

```java
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
    this.groq = Objects.requireNonNull(groq);
    this.gemini = Objects.requireNonNull(gemini);
  }

  @Override
  public AiChatResult generate(AiChatRequest request, AiResponseValidator validator) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(validator);

    String groqResponse = attempt(groq, request.prompt());
    if (isValid(validator, groqResponse)) {
      return new AiChatResult(groqResponse, AiResponseSource.GROQ);
    }

    String geminiResponse = attempt(gemini, request.prompt());
    if (isValid(validator, geminiResponse)) {
      return new AiChatResult(geminiResponse, AiResponseSource.GEMINI);
    }

    return new AiChatResult(
        request.deterministicFallback(), AiResponseSource.DETERMINISTIC_FALLBACK);
  }

  private static String attempt(ProviderChatClient provider, String prompt) {
    try {
      return provider.complete(prompt);
    } catch (RuntimeException providerFailure) {
      return null;
    }
  }

  private static boolean isValid(AiResponseValidator validator, String response) {
    try {
      return validator.isValid(response);
    } catch (RuntimeException validationFailure) {
      return false;
    }
  }
}
```

Do not log `prompt`, `response`, `providerFailure`, or `validationFailure` in #38. Issue #46 will add safe metadata/metrics without leaking content.

- [ ] **Step 6: Implement disabled mode**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/DisabledAiChatGateway.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import java.util.Objects;

final class DisabledAiChatGateway implements AiChatGateway {

  @Override
  public AiChatResult generate(AiChatRequest request, AiResponseValidator validator) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(validator);
    return new AiChatResult(
        request.deterministicFallback(), AiResponseSource.DETERMINISTIC_FALLBACK);
  }
}
```

Disabled mode intentionally does not validate the deterministic fallback with the model-output validator; the fallback is trusted application-owned content and `AiChatRequest` already enforces nonblank text.

- [ ] **Step 7: Expose exactly one gateway bean for enabled or disabled mode**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AiGatewayConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
  AiChatGateway enabledAiChatGateway(
      @Qualifier("groqProviderChatClient") ProviderChatClient groq,
      @Qualifier("geminiProviderChatClient") ProviderChatClient gemini) {
    return new FailoverAiChatGateway(groq, gemini);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "app.ai",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  AiChatGateway disabledAiChatGateway() {
    return new DisabledAiChatGateway();
  }
}
```

- [ ] **Step 8: Format and run gateway tests**

```bash
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=FailoverAiChatGatewayTest test
```

Expected: all routing tests pass and call counts prove no gateway-level same-provider retry.

- [ ] **Step 9: Commit the gateway**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGatewayTest.java
git commit -m "feat: add bounded ai provider failover gateway"
```

---

### Task 5: Verify Enabled/Disabled Spring Context Topology Without Real Credentials

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ApplicationModulesTest.java` (existing; do not modify unless it exposes a real module-boundary defect)

**Interfaces:**
- Consumes: all AI configuration and gateway beans from Tasks 2-4.
- Produces: executable proof that disabled mode is credential-free, enabled mode creates both Spring AI providers, exactly one application gateway exists, and no network call is made during startup.

- [ ] **Step 1: Write the context topology test**

Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseValidator;
import dev.krishnamurti.ai_chess_rivals.ai.config.AiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class AiConfigurationContextTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(
              AiConfig.class, AiProviderConfiguration.class, AiGatewayConfiguration.class)
          .withPropertyValues(
              "app.ai.groq.base-url=https://api.groq.com/openai/v1",
              "app.ai.groq.timeout=8s",
              "app.ai.gemini.timeout=12s");

  @Test
  void disabledModeCreatesOnlyFallbackGateway() {
    contextRunner
        .withPropertyValues("app.ai.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
              assertThat(context.getBeansOfType(ChatClient.class)).isEmpty();
              assertThat(context).hasSingleBean(AiChatGateway.class);

              var result =
                  context
                      .getBean(AiChatGateway.class)
                      .generate(
                          new AiChatRequest("test prompt", "offline fallback"),
                          AiResponseValidator.nonBlank());
              assertThat(result.content()).isEqualTo("offline fallback");
              assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
            });
  }

  @Test
  void enabledModeCreatesBothNamedProviderModelsAndClientsAndOneGateway() {
    contextRunner
        .withPropertyValues(
            "app.ai.enabled=true",
            "app.ai.groq.api-key=test-groq-key",
            "app.ai.groq.model=test-groq-model",
            "app.ai.gemini.api-key=test-gemini-key",
            "app.ai.gemini.model=test-gemini-model")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasBean("groqChatModel");
              assertThat(context).hasBean("groqChatClient");
              assertThat(context).hasBean("geminiChatModel");
              assertThat(context).hasBean("geminiChatClient");
              assertThat(context).hasSingleBean(AiChatGateway.class);
            });
  }
}
```

The enabled test constructs clients only. Do not invoke the gateway in enabled mode because that would make a real HTTP call with dummy credentials.

- [ ] **Step 2: Run the AI context tests**

```bash
./server/mvnw -f server/pom.xml -Dtest=AiConfigurationContextTest,AiPropertiesBindingTest,AiProviderConfigurationTest,FailoverAiChatGatewayTest test
```

Expected: all tests pass without internet access or real provider credentials.

- [ ] **Step 3: Run the Spring Modulith structure test**

```bash
./server/mvnw -f server/pom.xml -Dtest=ApplicationModulesTest test
```

Expected: `ApplicationModulesTest` passes with the new top-level `ai` module and no forbidden dependency from `ai` into `game` or `chess`.

- [ ] **Step 4: Verify no prompt/raw-response logger was introduced**

Run:

```bash
git grep -n "SimpleLoggerAdvisor" -- server/src/main/java || true
git grep -n "log.*prompt\|log.*response" -- server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai || true
```

Expected: no matches in the new AI module.

- [ ] **Step 5: Format and commit the context coverage**

```bash
./server/mvnw -f server/pom.xml spotless:apply
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java
git commit -m "test: cover ai provider configuration modes"
```

---

### Task 6: Full Verification and Native-Image Check

**Files:**
- Verify only; no planned production file changes.

**Interfaces:**
- Consumes: complete #38 implementation.
- Produces: evidence for every issue #38 acceptance criterion and a clean implementation branch ready for review.

- [ ] **Step 1: Apply backend formatting**

```bash
./server/mvnw -f server/pom.xml spotless:apply
```

Expected: command exits `0`.

- [ ] **Step 2: Run the complete backend verification lifecycle**

```bash
./server/mvnw -f server/pom.xml verify
```

Expected: formatting, Error Prone compilation, tests, Spring Modulith verification, and SpotBugs all pass.

- [ ] **Step 3: Run the repository-level verifier**

On POSIX:

```bash
./scripts/verify.sh
```

On Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

Expected: backend and frontend checks both pass.

- [ ] **Step 4: Build the production-native Docker image**

Run from repository root:

```bash
docker build --progress=plain -t ai-chess-rivals-native-check ./server
```

Expected: the Docker builder reaches its existing `native:compile` step successfully and produces the final image. This is the native-image-relevant acceptance check for #38; do not add handwritten runtime hints preemptively.

- [ ] **Step 5: If native compilation reports missing reflection/resources, fix only the proven missing hints**

Only if Step 4 fails with a specific native metadata error:

1. Add the smallest Spring `RuntimeHintsRegistrar` needed under the `ai` module.
2. Register it through `@ImportRuntimeHints` using the existing application pattern.
3. Add a focused test for the specific hint if practical.
4. Re-run Steps 1-4.

Do not add broad package reflection or blanket `registerType(..., MemberCategory.values())` hints.

- [ ] **Step 6: Map implementation evidence to issue #38**

Confirm each acceptance criterion has direct evidence:

```text
Groq through Spring AI
  -> groqChatModel = OpenAiChatModel + groqChatClient

Gemini through Spring AI
  -> geminiChatModel = GoogleGenAiChatModel + geminiChatClient

Models selected through configuration
  -> AI_GROQ_MODEL / AI_GEMINI_MODEL -> AiProperties

8s / 12s bounded provider waits
  -> OpenAiChatOptions timeout + Google HttpOptions timeout

No same-provider retry
  -> OpenAI maxRetries(0) + Google SDK attempts(1) + Spring RetryPolicy maxRetries(0)

Groq failure/invalid output -> Gemini exactly once
  -> FailoverAiChatGatewayTest call counters

Both providers fail -> deterministic result
  -> caller-supplied AiChatRequest.deterministicFallback()

Missing credentials behavior
  -> default app.ai.enabled=false + startup validation when true

No prompt/raw response logging by default
  -> no logging advisor/content logging in ai module

Tests need no provider credentials
  -> ProviderChatClient stubs + dummy construction-only context test

Native compatibility
  -> production Docker native build
```

- [ ] **Step 7: Ensure the working tree is clean and commits are scoped**

```bash
git status --short
git log --oneline -5
```

Expected: no uncommitted changes. The recent commits should correspond to dependency setup, AI configuration, provider clients, failover gateway, and context tests rather than unrelated refactors.

## Definition of Done

Issue #38 is ready for review when all of the following are true:

- Spring AI `2.0.0` is BOM-managed and both required provider starters are present.
- The application has distinct named Groq and Gemini `ChatModel`/`ChatClient` beans when AI is enabled.
- AI remains disabled by default and the existing chess application can start without provider credentials.
- Enabling AI without complete provider credentials/models fails configuration binding clearly.
- Groq is attempted once, then Gemini at most once, then deterministic fallback.
- Provider-internal retries are disabled at every known retry layer.
- Groq uses an 8-second timeout and Gemini a 12-second timeout.
- A caller-provided validator can reject Groq output and trigger Gemini, enabling issue #42's later structured-output validation without changing routing.
- Both-provider failure and disabled mode return caller-owned deterministic fallback text instead of throwing into the chess flow.
- No personality prompt, persistence, dialogue policy, tools, memory, agents, UI, or generic provider framework has leaked into #38.
- Automated tests make no real Groq/Gemini calls.
- Tech Stack and `.env.example` match the new dependency/configuration contract.
- Spring Modulith verification, root verification, and the production-native Docker build pass.

## Luna Execution Notes

- Execute tasks in order; later tasks rely on exact types/bean names from earlier tasks.
- Prefer `superpowers:subagent-driven-development` if available; otherwise use `superpowers:executing-plans` and stop at task checkpoints.
- Do not redesign the provider boundary while implementing. If an exact Spring AI 2.0 API differs from this plan, verify the current 2.0.0 reference/Javadocs, make the smallest compile-correct adjustment, and preserve the behavioral contract: two named clients, 8s/12s timeouts, zero same-provider retries, Groq -> Gemini once -> deterministic fallback.
- Do not broaden scope to #39-#46 even if adjacent code looks easy to add.
