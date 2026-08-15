# PR #56 Native Validation Traversable Resolver Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Hibernate Validator's JPA-aware reachability checks from reflectively reading ordinary configuration-record fields in the GraalVM native application, while preserving all existing Bean Validation semantics.

**Architecture:** Keep Spring Boot's normal `ValidationAutoConfiguration` and existing `@Validated` / Jakarta Bean Validation annotations. Add one root-package `ValidationConfiguration` that supplies a `ValidationConfigurationCustomizer`, replacing Hibernate Validator's automatically selected JPA-aware `TraversableResolver` with a tiny application-owned resolver that always marks properties reachable and cascadable. Leave the current AI runtime hints, Docker/AOT topology, CI verifier, and application configuration unchanged until hosted native CI proves this root-cause correction.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Framework 7, Spring Boot `ValidationConfigurationCustomizer`, Jakarta Validation `TraversableResolver`, Hibernate Validator, GraalVM Native Image, JUnit 5, AssertJ, `ApplicationContextRunner`.

## Source of Truth

- Pull request: `#56 fix: make native AI topology AOT-safe`
- Branch: `feature/issue-55-native-ai-enablement`
- Current head at plan creation: `09ba8c727879363970281b678db734d1ad006c21`
- Failed hosted CI run: `#47` / workflow run `31828569444`
- Approved design: `docs/superpowers/specs/2026-08-15-pr56-native-validation-traversable-resolver-design.md`
- Latest native evidence:
  - AI-enabled marker is emitted successfully.
  - native image compilation succeeds.
  - startup then fails while binding `ChessProperties`.
  - root cause is `MissingReflectionRegistrationError` for `ChessProperties.Stockfish.Evaluation.majorGainThresholdCentipawns`.
  - stack passes through Hibernate Validator `JPATraversableResolver` -> Jakarta Persistence `PersistenceUtil` -> Hibernate ORM `PersistenceUtilHelper` reflective field access.

## Global Constraints

- Stay on `feature/issue-55-native-ai-enablement` / PR #56.
- Treat the successful `AI gateway topology: enabled (Groq -> Gemini)` log as evidence that issue #55's AI-enabled topology is now selected and constructed correctly.
- Treat the latest failure as a validator traversability problem, not an AI topology, Docker, Stockfish, Flyway, or provider problem.
- Use Spring Boot's supported `ValidationConfigurationCustomizer` hook.
- Use an application-owned `jakarta.validation.TraversableResolver` that returns `true` for both `isReachable(...)` and `isCascadable(...)`.
- Put the validator configuration in the root package `dev.krishnamurti.ai_chess_rivals` so normal `@SpringBootApplication` component scanning finds it without introducing another top-level Spring Modulith module.
- Preserve every existing `@Validated`, `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Max`, `@AssertTrue`, and other validation annotation.
- Do not add native reflection hints for `ChessProperties`, `GameProperties`, or other configuration records.
- Do not remove, shrink, or otherwise modify the existing `AiRuntimeHints` / `AiRuntimeHintsTest` in this correction. Cleanup is a separate decision after native CI is green.
- Do not add `reachability-metadata.json`.
- Do not modify `server/Dockerfile`, `server/docker-compose.yml`, `.github/workflows/ci.yml`, `server/src/main/resources/application.yaml`, or the build/runtime AI environment contract.
- Do not modify `AiProviderConfiguration`, `AiGatewayConfiguration`, `FailoverAiChatGateway`, `DisabledAiChatGateway`, Stockfish behavior, JPA mappings, Flyway configuration, or frontend code.
- The current JPA entity layer does not use Jakarta Bean Validation constraints. If entity validation with lazy associations is introduced later, this global traversability decision must be revisited.
- Hosted `CI / Native image verification` remains the authoritative artifact-level gate.

---

## File Map

**Create:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java`
  - package-private singleton `TraversableResolver` implementation.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`
  - root-package Spring configuration exposing the `ValidationConfigurationCustomizer` bean.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`
  - proves Spring Boot installs the resolver and nested configuration validation still works.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java`
- any existing `*Properties.java` class
- `.github/workflows/ci.yml`
- `server/Dockerfile`
- `server/docker-compose.yml`

---

### Task 1: Add a failing validator-customization regression test

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`
- Later create in Task 2:
  - `server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java`
  - `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`

**Interfaces:**
- Consumes: Spring Boot `ValidationAutoConfiguration`, Jakarta `Validator` / `ValidatorFactory`, existing `ChessProperties`.
- Produces: a regression contract that Spring Boot's validator uses `AlwaysTraversableResolver.INSTANCE` and still evaluates nested Bean Validation constraints.

- [ ] **Step 1: Create the failing Spring validation configuration test**

Create `ValidationConfigurationTest.java` exactly as follows:

```java
package dev.krishnamurti.ai_chess_rivals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class ValidationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(ValidationConfiguration.class);

  @Test
  void configuresAlwaysTraversableResolver() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          ValidatorFactory validatorFactory = context.getBean(ValidatorFactory.class);

          assertThat(validatorFactory.getTraversableResolver())
              .isSameAs(AlwaysTraversableResolver.INSTANCE);
        });
  }

  @Test
  void keepsNestedConfigurationValidationActive() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          Validator validator = context.getBean(Validator.class);
          ChessProperties.Stockfish.Evaluation invalidEvaluation =
              new ChessProperties.Stockfish.Evaluation(8, 50, 0, 200);
          ChessProperties properties =
              new ChessProperties(
                  new ChessProperties.Stockfish(
                      "stockfish/stockfish", 1, 16, 10, 30, invalidEvaluation));

          Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

          assertThat(violations)
              .extracting(violation -> violation.getPropertyPath().toString())
              .contains("stockfish.evaluation.majorGainThresholdCentipawns");
        });
  }
}
```

The second test intentionally targets the same nested `ChessProperties.Stockfish.Evaluation` path that the native application was validating when it crashed. It proves that replacing the traversability strategy does **not** bypass cascading or the `@Min` constraint.

- [ ] **Step 2: Run the focused test and verify RED**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=ValidationConfigurationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest test
```

Expected: compilation fails because `ValidationConfiguration` and `AlwaysTraversableResolver` do not exist yet.

Do not weaken the assertions and do not create reflection hints to make the test green.

- [ ] **Step 3: Do not commit the red state**

Continue directly to Task 2. The first implementation commit should contain the regression test and the minimal production fix together in a green state.

---

### Task 2: Install an always-traversable Jakarta Validation resolver

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`

**Interfaces:**
- Consumes: Jakarta Validation `TraversableResolver`, Spring Boot `ValidationConfigurationCustomizer`.
- Produces:
  - singleton `AlwaysTraversableResolver.INSTANCE`;
  - one `ValidationConfigurationCustomizer` bean that calls `configuration.traversableResolver(...)`.

- [ ] **Step 1: Create the resolver**

Create `AlwaysTraversableResolver.java` exactly as follows:

```java
package dev.krishnamurti.ai_chess_rivals;

import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import java.lang.annotation.ElementType;

final class AlwaysTraversableResolver implements TraversableResolver {

  static final AlwaysTraversableResolver INSTANCE = new AlwaysTraversableResolver();

  private AlwaysTraversableResolver() {}

  @Override
  public boolean isReachable(
      Object traversableObject,
      Path.Node traversableProperty,
      Class<?> rootBeanType,
      Path pathToTraversableObject,
      ElementType elementType) {
    return true;
  }

  @Override
  public boolean isCascadable(
      Object traversableObject,
      Path.Node traversableProperty,
      Class<?> rootBeanType,
      Path pathToTraversableObject,
      ElementType elementType) {
    return true;
  }
}
```

Do not inspect JPA state, call `PersistenceUtil`, use reflection, or special-case application types inside this resolver.

- [ ] **Step 2: Create the Spring Boot validator customization**

Create `ValidationConfiguration.java` exactly as follows:

```java
package dev.krishnamurti.ai_chess_rivals;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps configuration-property validation independent of JPA persistence reachability.
 *
 * <p>The application currently uses Jakarta Bean Validation for configuration properties, not for
 * lazy JPA entity associations. Revisit this customization if entity validation is introduced.
 */
@Configuration(proxyBeanMethods = false)
public class ValidationConfiguration {

  @Bean
  ValidationConfigurationCustomizer validationConfigurationCustomizer() {
    return configuration -> configuration.traversableResolver(AlwaysTraversableResolver.INSTANCE);
  }
}
```

Do not replace Spring Boot's validator bean and do not create a separate `ValidatorFactory`. The customizer must modify the validator factory that `ValidationAutoConfiguration` already creates.

- [ ] **Step 3: Run the focused test and verify GREEN**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=ValidationConfigurationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest test
```

Expected:

- `configuresAlwaysTraversableResolver`: PASS
- `keepsNestedConfigurationValidationActive`: PASS

If the first test reports a different resolver, do not add reflection metadata. Verify that `ValidationConfigurationCustomizer` is the Spring Boot 4.1 type from `org.springframework.boot.validation.autoconfigure` and that the configuration class is included in the test context.

- [ ] **Step 4: Apply formatting and rerun the focused test**

POSIX:

```sh
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=ValidationConfigurationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the isolated validator correction**

```sh
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java

git commit -m "fix: avoid JPA traversability for config validation"
```

The commit must not contain runtime-hint cleanup, Docker changes, CI changes, property rewrites, or unrelated formatting.

---

### Task 3: Prove existing validation semantics did not regress

**Files:**
- No planned source changes.
- Existing tests are verification inputs only.

**Interfaces:**
- Consumes: Task 2's application-wide validator customization.
- Produces: JVM evidence that existing AI/chess/game validation behavior is preserved.

- [ ] **Step 1: Run the focused validation/configuration regression set**

POSIX:

```sh
./server/mvnw -f server/pom.xml \
  -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,ChessPropertiesValidationTest,GamePropertiesValidationTest \
  test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,ChessPropertiesValidationTest,GamePropertiesValidationTest test
```

Expected: PASS.

Do not modify existing constraints if a test fails. First determine whether the new validator customization changed behavior or whether the test constructs its own standalone validator and is unrelated to the Spring Boot customizer.

- [ ] **Step 2: Run full backend verification**

POSIX:

```sh
./server/mvnw -f server/pom.xml verify
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: Java 25/Maven checks, Spotless, Error Prone compilation, all tests, Spring Modulith verification, and SpotBugs PASS.

- [ ] **Step 3: Run the repository verifier if available**

POSIX:

```sh
./scripts/verify.sh
```

Windows:

```powershell
.\scripts\verify.ps1
```

Expected: PASS.

If unchanged frontend tooling cannot run in Luna's environment, record that limitation and rely on hosted CI for the unchanged frontend. Do not modify frontend files to compensate.

- [ ] **Step 4: Inspect the implementation diff**

```sh
git diff HEAD~1 -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java
```

Confirm the change contains only:

1. the always-true resolver;
2. the Spring Boot `ValidationConfigurationCustomizer` bean;
3. the focused regression test.

- [ ] **Step 5: Confirm existing AI runtime hints remain untouched**

```sh
git diff HEAD~1 -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java
```

Expected: no output.

Do not remove those hints in this correction even if they appear redundant after the resolver change.

---

### Task 4: Push and let hosted native CI answer the root-cause hypothesis

**Files:**
- No planned source changes unless fresh CI evidence identifies a new root cause.

**Interfaces:**
- Consumes: locally green validator correction.
- Produces: artifact-level evidence from the actual production GraalVM native image.

- [ ] **Step 1: Push the feature branch**

```sh
git push origin feature/issue-55-native-ai-enablement
```

- [ ] **Step 2: Inspect the fresh PR #56 CI run**

Expected progression:

- `CI / Detect changes`: PASS
- `CI / Backend verification`: PASS
- `CI / Frontend verification`: PASS or unchanged/skipped according to workflow path logic
- `CI / Native image verification` -> `Build production native image`: PASS
- native app remains running and reaches `/actuator/health`
- application logs contain `AI gateway topology: enabled (Groq -> Gemini)`
- application logs do not contain `AI gateway topology: disabled`
- final-image provider environment leak assertion: PASS

- [ ] **Step 3: Apply these stop conditions mechanically**

Use the first matching condition:

1. **Native stack still contains `JPATraversableResolver` / `PersistenceUtilHelper` while validating application configuration properties** — STOP. Do not add reflection hints. The customizer is not controlling the native validator as intended; use `superpowers:systematic-debugging` to verify whether the `ValidationConfigurationCustomizer` bean reached the AOT-processed application and whether another validator factory is being used.
2. **`JPATraversableResolver` is gone, but there is a different `MissingReflectionRegistrationError`** — capture the exact type/member and first relevant `Caused by:` stack. Do not automatically add metadata; diagnose the new reflection source first.
3. **Application exits for a non-reflection error** — capture the first root-cause `Caused by:` and debug that problem separately. Do not change the resolver unless evidence points back to it.
4. **Application becomes healthy and emits the enabled marker, but the job still fails** — validator/native startup is solved. Inspect only the later verifier assertion, such as final-image environment metadata.
5. **Native job passes** — only then report PR #56's native runtime gate as fixed with fresh hosted evidence.

- [ ] **Step 4: Do not perform cleanup in the same CI-repair cycle**

Even after a green native run, do not immediately remove `AiRuntimeHints` in the same implementation pass. Record a follow-up cleanup candidate instead. The objective of this plan is to make the native artifact reliable, not to minimize metadata simultaneously.

---

## Completion Criteria

This correction is complete only when all of the following are true:

- Spring Boot's default `ValidatorFactory` uses `AlwaysTraversableResolver.INSTANCE`.
- Nested `ChessProperties` validation still detects `stockfish.evaluation.majorGainThresholdCentipawns` violations.
- Existing AI/chess/game validation tests remain green.
- Full backend verification is green.
- No existing configuration-property validation annotations were removed or rewritten.
- No new reflection hints were added for `ChessProperties`, `GameProperties`, or unrelated types.
- Existing `AiRuntimeHints` remain unchanged during this correction.
- Docker, CI workflow, AI topology, provider configuration, Stockfish behavior, JPA mappings, and frontend code remain unchanged.
- A fresh hosted `CI / Native image verification` run starts the actual native application, reaches health, observes the enabled AI topology marker, and passes the existing final-image metadata assertion.

If hosted native CI remains red, do not claim the problem is fixed. Report the new first root cause and the exact evidence before making another design change.