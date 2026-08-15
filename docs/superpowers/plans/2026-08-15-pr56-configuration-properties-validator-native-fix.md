# PR #56 Native-Safe Configuration Properties Validator Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan inline, task-by-task. Do not use subagent-driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route all application `@ConfigurationProperties` validation through one application-owned `LocalValidatorFactoryBean` with `AlwaysTraversableResolver`, eliminating Spring Boot's separate JPA-aware `ConfigurationPropertiesJsr303Validator` path while preserving existing Jakarta validation constraints and startup failures.

**Architecture:** Spring Boot 4.1's configuration-properties binder always adds a bean named `configurationPropertiesValidator` when present, but also creates a separate `ConfigurationPropertiesJsr303Validator` for every properties type annotated with `@Validated`. Replace the current general `ValidationConfigurationCustomizer` with a static named `LocalValidatorFactoryBean`, remove only `@Validated` from the six configuration-properties types, and keep all `@Valid`, `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Min`, `@Max`, and `@AssertTrue` annotations unchanged. Regression tests must exercise actual configuration binding rather than the general application `ValidatorFactory`.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Framework 7, Spring Boot `ConfigurationPropertiesBinder`, Spring `LocalValidatorFactoryBean`, Jakarta Validation, Hibernate Validator, GraalVM Native Image, JUnit 5, AssertJ, `ApplicationContextRunner`.

## Source of Truth

- PR: `#56 fix: make native AI topology AOT-safe`
- Branch: `feature/issue-55-native-ai-enablement`
- Head when this plan was prepared: `519538b70e0ed0d0f8ebe07d6df439a6d32ae1dc`
- Design: `docs/superpowers/specs/2026-08-15-pr56-configuration-properties-validator-native-design.md`
- Latest native evidence:
  - native image compilation succeeds;
  - `AI gateway topology: enabled (Groq -> Gemini)` is emitted;
  - startup fails while binding `ChessProperties`;
  - failure remains `MissingReflectionRegistrationError` for `ChessProperties.Stockfish.Evaluation.majorGainThresholdCentipawns`;
  - stack still passes through `ConfigurationPropertiesJsr303Validator -> JPATraversableResolver -> PersistenceUtil -> PersistenceUtilHelper -> Field.get`.
- Spring Boot 4.1 source evidence:
  - `ConfigurationPropertiesJsr303Validator` creates its own internal `LocalValidatorFactoryBean` and initializes it directly;
  - `ValidationConfigurationCustomizer` therefore does not affect that validator;
  - `ConfigurationPropertiesBinder` adds a bean named `configurationPropertiesValidator` when present;
  - `ConfigurationPropertiesBinder` additionally creates `ConfigurationPropertiesJsr303Validator` whenever the target has `@Validated`.

## Global Constraints

- Stay on `feature/issue-55-native-ai-enablement` / PR #56.
- Execute this plan inline with `superpowers:executing-plans` only.
- Treat this as a configuration-properties validation-path correction, not an AI topology, Docker, Stockfish, Flyway, JPA mapping, or provider correction.
- Preserve `AlwaysTraversableResolver` and its `isReachable(...) == true` / `isCascadable(...) == true` behavior.
- Replace the current `ValidationConfigurationCustomizer` approach; do not keep both approaches.
- The named bean must use `EnableConfigurationProperties.VALIDATOR_BEAN_NAME` rather than a duplicated string literal.
- The named validator bean method must be `static` because Spring Boot creates the configuration-properties validator very early.
- Remove `@Validated` and its import from exactly the six application configuration-properties types listed in Task 3.
- Preserve every Jakarta validation constraint and existing validation message exactly.
- Do not replace Jakarta constraints with manual `if` checks, compact-constructor validation, or a custom hand-written Spring `Validator` in this correction.
- Do not add field reflection hints for `ChessProperties`, `GameProperties`, `MatchGuardProperties`, `OwnerControlProperties`, or `WebSocketProperties`.
- Do not remove or modify `AiRuntimeHints`, `AiRuntimeHintsTest`, or `AiConfig` in this correction.
- Do not modify `GameNativeRuntimeHints` in this correction. Its existing hints are outside the single-variable hypothesis being tested here.
- Do not add `reachability-metadata.json`.
- Do not modify `server/Dockerfile`, `server/docker-compose.yml`, `.github/workflows/ci.yml`, `server/src/main/resources/application.yaml`, or the native build/runtime AI environment contract.
- Do not modify provider/gateway logic, Stockfish behavior, JPA entities/repositories, Flyway migrations, or frontend files.
- Hosted `CI / Native image verification` remains the authoritative native artifact-level gate.

---

## File Map

**Modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`
  - replace `ValidationConfigurationCustomizer` with the named static configuration-properties validator bean.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java`
  - remove only `@Validated` and its import.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java`
  - remove only `@Validated` and its import.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java`
  - remove only `@Validated` and its import.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java`
  - remove only `@Validated` and its import.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java`
  - remove only `@Validated` and its import.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java`
  - remove only `@Validated` and its import.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`
  - replace the misleading general-`ValidatorFactory` test with real configuration-binding tests.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java`
  - include `ValidationConfiguration` in its isolated context.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
  - include `ValidationConfiguration` in its isolated context.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GamePropertiesBindingTest.java`
  - include `ValidationConfiguration` in its isolated context.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardPropertiesTest.java`
  - include `ValidationConfiguration` in its isolated context.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlPropertiesTest.java`
  - include `ValidationConfiguration` in its isolated context.

**Preserve unchanged:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java`
- existing standalone Jakarta validation tests such as `ChessPropertiesValidationTest`
- Docker / CI / frontend / DB files

---

### Task 1: Replace the Misleading Validator Test with the Real Binding Path

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`

**Interfaces:**
- Consumes: `ValidationConfiguration`, `ChessConfig`, `ChessProperties`, Spring Boot configuration-properties auto-configuration.
- Produces: a RED regression that requires the named `configurationPropertiesValidator` and exercises the same nested chess property that fails in native startup.

- [ ] **Step 1: Replace `ValidationConfigurationTest` completely**

Use this content:

```java
package dev.krishnamurti.ai_chess_rivals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessConfig;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ValidationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(ValidationConfiguration.class, ChessConfig.class)
          .withPropertyValues(
              "app.chess.stockfish.path=stockfish/stockfish",
              "app.chess.stockfish.threads=1",
              "app.chess.stockfish.hash-mb=16",
              "app.chess.stockfish.startup-timeout-seconds=10",
              "app.chess.stockfish.move-timeout-seconds=30",
              "app.chess.stockfish.evaluation.depth=8",
              "app.chess.stockfish.evaluation.move-time-millis=50",
              "app.chess.stockfish.evaluation.major-gain-threshold-centipawns=200",
              "app.chess.stockfish.evaluation.major-mistake-threshold-centipawns=200");

  @Test
  void exposesNativeSafeConfigurationPropertiesValidator() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          LocalValidatorFactoryBean validator =
              context.getBean(
                  EnableConfigurationProperties.VALIDATOR_BEAN_NAME,
                  LocalValidatorFactoryBean.class);

          assertThat(validator.getTraversableResolver())
              .isSameAs(AlwaysTraversableResolver.INSTANCE);
        });
  }

  @Test
  void bindsValidNestedChessConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          ChessProperties properties = context.getBean(ChessProperties.class);
          assertThat(properties.stockfish().evaluation().majorGainThresholdCentipawns())
              .isEqualTo(200);
        });
  }

  @Test
  void rejectsInvalidNestedChessThresholdThroughConfigurationBinding() {
    contextRunner
        .withPropertyValues(
            "app.chess.stockfish.evaluation.major-gain-threshold-centipawns=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
```

Do not use `context.getBean(ValidatorFactory.class)` in this regression. That tests the wrong Spring Boot validator path for this bug.

- [ ] **Step 2: Run the focused test and verify RED**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=ValidationConfigurationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest test
```

Expected: FAIL because the current `ValidationConfiguration` exposes a `ValidationConfigurationCustomizer`, not a bean named `configurationPropertiesValidator` of type `LocalValidatorFactoryBean`.

The failure must come from `exposesNativeSafeConfigurationPropertiesValidator`. If the test fails earlier for unrelated property binding, stop and correct the test fixture before touching production code.

- [ ] **Step 3: Do not commit the RED state**

Continue directly to Task 2.

---

### Task 2: Replace the General Validator Customizer with Boot's Configuration-Properties Validator Hook

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`

**Interfaces:**
- Consumes: `AlwaysTraversableResolver.INSTANCE`.
- Produces: Spring bean named `EnableConfigurationProperties.VALIDATOR_BEAN_NAME` whose concrete type is `LocalValidatorFactoryBean` and whose traversable resolver is `AlwaysTraversableResolver.INSTANCE`.

- [ ] **Step 1: Replace `ValidationConfiguration.java` completely**

Use this content:

```java
package dev.krishnamurti.ai_chess_rivals;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Provides the validator used directly by Spring Boot configuration-properties binding.
 *
 * <p>Configuration properties are ordinary application configuration, not lazy JPA entities, so
 * validation must not consult persistence reachability. Revisit this boundary if configuration
 * validation is ever replaced with entity validation.
 */
@Configuration(proxyBeanMethods = false)
public class ValidationConfiguration {

  @Bean(name = EnableConfigurationProperties.VALIDATOR_BEAN_NAME)
  static LocalValidatorFactoryBean configurationPropertiesValidator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setTraversableResolver(AlwaysTraversableResolver.INSTANCE);
    return validator;
  }
}
```

Important:

- the method must remain `static`;
- use the Boot constant for the bean name;
- do not call `afterPropertiesSet()` manually; Spring manages the bean lifecycle;
- do not also expose `ValidationConfigurationCustomizer`;
- do not create a second validator bean.

- [ ] **Step 2: Run the focused test**

```sh
./server/mvnw -f server/pom.xml -Dtest=ValidationConfigurationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest test
```

Expected at this intermediate point: all three tests may pass, but the production properties still have `@Validated`, so Boot can still add its separate `ConfigurationPropertiesJsr303Validator`. **Do not stop here and do not push.** Task 3 is required to remove that second path.

---

### Task 3: Remove Only the `@Validated` Trigger from All Configuration-Properties Types

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`

**Interfaces:**
- Consumes: named validator from Task 2.
- Produces: configuration-properties types that remain constrained by Jakarta Validation but no longer trigger Boot's separate per-type `ConfigurationPropertiesJsr303Validator`.

- [ ] **Step 1: Remove exactly these imports**

From each of the six files, remove:

```java
import org.springframework.validation.annotation.Validated;
```

Do not remove any `jakarta.validation...` import.

- [ ] **Step 2: Remove exactly these annotations**

Remove the class/record-level:

```java
@Validated
```

from each of the six properties types.

Do not remove or rewrite:

```java
@Valid
@NotNull
@NotBlank
@NotEmpty
@Min
@Max
@AssertTrue
```

Do not alter their messages, bounds, record components, helper methods, or constructor logic.

- [ ] **Step 3: Verify no production configuration-properties type still triggers Boot's built-in JSR-303 validator**

POSIX:

```sh
for file in \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java; do
  if grep -nE 'validation\.annotation\.Validated|@Validated' "$file"; then
    echo "Unexpected @Validated trigger remains in $file"
    exit 1
  fi
done
```

Windows PowerShell:

```powershell
$files = @(
  'server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java',
  'server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java',
  'server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java',
  'server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java',
  'server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java',
  'server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java'
)
foreach ($file in $files) {
  if (Select-String -Path $file -Pattern 'validation\.annotation\.Validated|@Validated') {
    throw "Unexpected @Validated trigger remains in $file"
  }
}
```

Expected: no matches.

- [ ] **Step 4: Rerun the real binding-path regression**

```sh
./server/mvnw -f server/pom.xml -Dtest=ValidationConfigurationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest test
```

Expected: PASS.

This is the important transition: the invalid nested chess threshold must still fail even though `ChessProperties` no longer has `@Validated`. That proves the named `configurationPropertiesValidator` is now enforcing the Jakarta constraints.

---

### Task 4: Update Isolated Binding Tests to Use the New Validation Path

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GamePropertiesBindingTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardPropertiesTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlPropertiesTest.java`

**Interfaces:**
- Consumes: root-package `dev.krishnamurti.ai_chess_rivals.ValidationConfiguration`.
- Produces: isolated `ApplicationContextRunner` tests that continue to exercise startup validation after `@Validated` is removed.

- [ ] **Step 1: Update `AiPropertiesBindingTest`**

Add:

```java
import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
```

Change:

```java
.withUserConfiguration(AiConfig.class)
```

to:

```java
.withUserConfiguration(ValidationConfiguration.class, AiConfig.class)
```

Do not change the existing valid/invalid property cases.

- [ ] **Step 2: Update `AiConfigurationContextTest`**

Add:

```java
import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
```

Change the existing user configuration from:

```java
.withUserConfiguration(
    AiConfig.class, AiProviderConfiguration.class, AiGatewayConfiguration.class)
```

to:

```java
.withUserConfiguration(
    ValidationConfiguration.class,
    AiConfig.class,
    AiProviderConfiguration.class,
    AiGatewayConfiguration.class)
```

Do not change provider beans, fake values, gateway assertions, or startup markers.

- [ ] **Step 3: Update `GamePropertiesBindingTest`**

Add:

```java
import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
```

Change:

```java
.withUserConfiguration(GameConfig.class)
```

to:

```java
.withUserConfiguration(ValidationConfiguration.class, GameConfig.class)
```

Keep all current negative/reversed/missing move-delay cases unchanged.

- [ ] **Step 4: Update `MatchGuardPropertiesTest`**

Add:

```java
import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
```

Change:

```java
.withUserConfiguration(GameConfig.class)
```

to:

```java
.withUserConfiguration(ValidationConfiguration.class, GameConfig.class)
```

Keep all current missing/negative/zero assertions unchanged.

- [ ] **Step 5: Update `OwnerControlPropertiesTest`**

Add:

```java
import dev.krishnamurti.ai_chess_rivals.ValidationConfiguration;
```

Change:

```java
.withUserConfiguration(GameConfig.class)
```

to:

```java
.withUserConfiguration(ValidationConfiguration.class, GameConfig.class)
```

Keep missing and blank token cases unchanged.

- [ ] **Step 6: Run the focused binding/context suite**

POSIX:

```sh
./server/mvnw -f server/pom.xml \
  -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,GamePropertiesBindingTest,MatchGuardPropertiesTest,OwnerControlPropertiesTest \
  test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,GamePropertiesBindingTest,MatchGuardPropertiesTest,OwnerControlPropertiesTest test
```

Expected: PASS.

If one of the existing invalid configuration cases unexpectedly starts successfully, do not weaken or delete the assertion. Check first that `ValidationConfiguration.class` is included in that test's `ApplicationContextRunner`.

---

### Task 5: Preserve Direct Jakarta Constraint Coverage

**Files:**
- No planned source changes.
- Existing tests are verification inputs.

**Interfaces:**
- Consumes: unchanged Jakarta annotations on the properties records.
- Produces: evidence that removing `@Validated` did not remove the underlying Bean Validation constraints.

- [ ] **Step 1: Run existing direct validation tests**

Run at least:

```sh
./server/mvnw -f server/pom.xml -Dtest=ChessPropertiesValidationTest,GamePropertiesValidationTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ChessPropertiesValidationTest,GamePropertiesValidationTest test
```

Expected: PASS.

These tests use Jakarta Validation directly and should remain unchanged. Their purpose is constraint semantics, not Spring Boot binder wiring.

- [ ] **Step 2: Re-run AI runtime-hints coverage unchanged**

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest test
```

Expected: PASS with no edits to `AiRuntimeHints` or `AiRuntimeHintsTest`.

---

### Task 6: Format, Verify, and Commit the Single Root-Cause Correction

**Files:**
- Only files listed in Tasks 1-4 should have planned changes.

**Interfaces:**
- Consumes: green focused tests.
- Produces: one locally verified correction ready for the hosted native gate.

- [ ] **Step 1: Apply formatting**

POSIX:

```sh
./server/mvnw -f server/pom.xml spotless:apply
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
```

- [ ] **Step 2: Re-run the focused suite after formatting**

```sh
./server/mvnw -f server/pom.xml \
  -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,AiRuntimeHintsTest,ChessPropertiesValidationTest,GamePropertiesBindingTest,GamePropertiesValidationTest,MatchGuardPropertiesTest,OwnerControlPropertiesTest \
  test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,AiRuntimeHintsTest,ChessPropertiesValidationTest,GamePropertiesBindingTest,GamePropertiesValidationTest,MatchGuardPropertiesTest,OwnerControlPropertiesTest test
```

Expected: PASS.

- [ ] **Step 3: Run full backend verification**

```sh
./server/mvnw -f server/pom.xml verify
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: PASS for compilation, tests, formatting checks, Modulith verification, and SpotBugs.

- [ ] **Step 4: Run the repository verifier**

POSIX:

```sh
./scripts/verify.sh
```

Windows:

```powershell
.\scripts\verify.ps1
```

Expected: PASS.

If unchanged frontend tooling cannot run locally, record that limitation and rely on hosted frontend CI. Do not modify frontend files.

- [ ] **Step 5: Inspect the diff for scope**

```sh
git diff -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GamePropertiesBindingTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardPropertiesTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlPropertiesTest.java
```

Confirm the production diff contains only:

1. `ValidationConfigurationCustomizer` replaced by the named static `LocalValidatorFactoryBean`;
2. `@Validated` removed from exactly six configuration-properties types;
3. no Jakarta constraints removed;
4. actual binding-path regression test;
5. isolated context tests importing `ValidationConfiguration`.

- [ ] **Step 6: Confirm forbidden files are untouched by this implementation**

```sh
git diff -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java \
  server/Dockerfile \
  server/docker-compose.yml \
  .github/workflows/ci.yml
```

Expected: no implementation diff from this correction.

- [ ] **Step 7: Commit**

```sh
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiPropertiesBindingTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GamePropertiesBindingTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardPropertiesTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlPropertiesTest.java

git commit -m "fix: use native-safe configuration properties validator"
```

Do not include plan/spec commits or unrelated working-tree changes in this implementation commit if Luna already has them locally from the branch.

---

### Task 7: Push and Use Hosted Native CI as the Decisive Test

**Files:**
- No planned source changes unless fresh CI provides a new, specific root cause.

**Interfaces:**
- Consumes: locally verified implementation commit.
- Produces: native-runtime evidence from the actual production Docker/GraalVM artifact.

- [ ] **Step 1: Push**

```sh
git push origin feature/issue-55-native-ai-enablement
```

- [ ] **Step 2: Inspect the fresh PR #56 workflow**

Expected:

- `CI / Backend verification`: PASS
- `CI / Frontend verification`: PASS or unchanged according to path filtering
- `CI / Native image verification / Build production native image`: PASS
- native application starts against disposable PostgreSQL 17
- `AI gateway topology: enabled (Groq -> Gemini)` appears
- `AI gateway topology: disabled` does not appear
- final image contains no baked provider key/model environment values

- [ ] **Step 3: Apply these stop conditions mechanically**

Use the first matching condition:

1. **Stack still contains `ConfigurationPropertiesJsr303Validator` and `JPATraversableResolver` while binding one of the six properties types** — STOP. Do not add reflection hints. Verify that no `@Validated` remains on that exact type and verify the native AOT build includes the updated source. This would mean the intended binder-path change did not reach the artifact.
2. **Failure is still a reflective field read through `PersistenceUtilHelper` / `JPATraversableResolver`** — STOP. Do not add field hints. The root-cause hypothesis is not yet implemented correctly.
3. **Failure changes to reflective invocation of an existing `@AssertTrue` method** such as `GameProperties.MoveDelay.isMinimumNonNegative()`, `isMaximumNonNegative()`, `isValidRange()`, or `MatchGuardProperties.isCooldownNonNegative()` — record the exact method and stack. This is a separate bounded method-invocation metadata problem; do not revert the configuration-properties validator design and do not add field-access hints.
4. **Failure is a normal binding/validation error for a CI value** — inspect the rejected property and fix only the invalid CI/runtime fixture if the application constraint is correct.
5. **Failure is unrelated to validation/reflection** — capture the first root-cause `Caused by:` and debug that subsystem separately with `superpowers:systematic-debugging`.
6. **Application becomes healthy and topology/image checks pass** — only then report the native validation/startup issue as fixed.

- [ ] **Step 4: Do not perform cleanup in the same run**

Even if native CI passes, do not immediately remove `AiRuntimeHints`, `AlwaysTraversableResolver`, or existing game native hints in this implementation. Any hint cleanup is a separate follow-up with its own evidence and tests.

---

## Completion Criteria

This correction is complete only when all of the following are true:

- `ValidationConfiguration` exposes a static bean named `EnableConfigurationProperties.VALIDATOR_BEAN_NAME`.
- That bean is a `LocalValidatorFactoryBean` using `AlwaysTraversableResolver.INSTANCE`.
- The old `ValidationConfigurationCustomizer` bean is gone.
- `AiProperties`, `ChessProperties`, `GameProperties`, `MatchGuardProperties`, `OwnerControlProperties`, and `WebSocketProperties` no longer import or use `@Validated`.
- All existing Jakarta validation annotations and messages remain intact.
- `ValidationConfigurationTest` exercises actual `ChessProperties` binding and rejects `major-gain-threshold-centipawns=0` without `@Validated`.
- Existing AI/game/match/owner invalid startup tests remain green after explicitly including `ValidationConfiguration`.
- Direct constraint tests remain green.
- `AiRuntimeHints` remains unchanged and its tests remain green.
- Backend verification passes.
- Repository verification passes, or any unchanged frontend environment limitation is explicitly recorded.
- Fresh hosted native CI no longer enters `ConfigurationPropertiesJsr303Validator -> JPATraversableResolver` for application configuration properties.
- The actual native application becomes healthy and the existing AI topology and final-image secret-leak checks pass before the issue is called fixed.