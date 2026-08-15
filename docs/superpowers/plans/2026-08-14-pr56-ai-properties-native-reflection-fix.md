# PR #56 AI Properties Native Reflection Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the AI-enabled GraalVM native application start successfully by registering the exact reflection hints Hibernate Validator needs for the three existing `@AssertTrue` methods in `AiProperties`.

**Architecture:** Keep PR #56's current Docker/AOT topology design unchanged. Add one package-local Spring `RuntimeHintsRegistrar`, import it from `AiConfig`, and register invocation hints only for the three no-argument validation methods that Hibernate Validator can call reflectively. Verify the contract with Spring's `RuntimeHintsPredicates`, then let the existing hosted native-image job remain the authoritative artifact-level test.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Framework AOT `RuntimeHints`, Hibernate Validator / Jakarta Validation, GraalVM Native Image, JUnit 5, AssertJ.

## Global Constraints

- Stay on branch `feature/issue-55-native-ai-enablement` / PR #56.
- Root cause is the observed native `MissingReflectionRegistrationError` for `AiProperties.isEnabledConfigurationComplete()`; do not redesign the AI provider topology.
- Register native invocation hints for exactly these existing methods:
  - `AiProperties#isEnabledConfigurationComplete`
  - `AiProperties.Groq#isTimeoutPositive`
  - `AiProperties.Gemini#isTimeoutWithinHttpOptionsRange`
- Use Spring `RuntimeHints`; do not add `reachability-metadata.json`.
- Do not broaden to all public methods unless exact method hints prove insufficient with fresh native evidence.
- Do not change `AiProperties` validation semantics or method names.
- Do not modify `AiProviderConfiguration`, `AiGatewayConfiguration`, `FailoverAiChatGateway`, or `DisabledAiChatGateway`.
- Do not modify `server/Dockerfile`, `server/docker-compose.yml`, `.github/workflows/ci.yml`, or the current build/runtime AI environment contract.
- Keep the current AI gateway startup markers, health check, fake runtime provider values, final-image environment leak assertion, and cleanup logic unchanged.
- Treat the hosted `CI / Native image verification` job as the authoritative native-runtime gate.
- If the next native run exposes a different missing-reflection type or method, stop and collect that exact evidence before adding more hints.

---

### Task 1: Add a focused runtime-hints contract test

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`
- Later in Task 2 create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`

**Interfaces:**
- Consumes: `AiProperties`, `AiProperties.Groq`, `AiProperties.Gemini`, Spring `RuntimeHints`, and `RuntimeHintsPredicates`.
- Produces: a regression test requiring invocation hints for the three validation methods.

- [ ] **Step 1: Create the failing test**

Create `AiRuntimeHintsTest.java` with this content:

```java
package dev.krishnamurti.ai_chess_rivals.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class AiRuntimeHintsTest {

  @Test
  void registersAiPropertiesValidationMethodsForNativeInvocation() {
    RuntimeHints hints = new RuntimeHints();
    new AiRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AiProperties.class, "isEnabledConfigurationComplete")
                .test(hints))
        .isTrue();
    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AiProperties.Groq.class, "isTimeoutPositive")
                .test(hints))
        .isTrue();
    assertThat(
            RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AiProperties.Gemini.class, "isTimeoutWithinHttpOptionsRange")
                .test(hints))
        .isTrue();
  }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

From repository root run:

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest test
```

Windows equivalent:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest test
```

Expected: compilation/test execution fails because `AiRuntimeHints` does not exist yet. Do not weaken the assertions to make RED easier.

- [ ] **Step 3: Do not commit the red state**

Continue directly to Task 2. The first commit should contain both the regression test and the minimal production fix in a green state.

---

### Task 2: Register exact Spring AOT invocation hints

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`

**Interfaces:**
- Consumes: Spring `RuntimeHintsRegistrar`, `RuntimeHints`, `ExecutableMode`, and the existing `AiProperties` record hierarchy.
- Produces: `AiRuntimeHints` registered by `AiConfig` during Spring AOT processing.

- [ ] **Step 1: Create the minimal registrar**

Create `AiRuntimeHints.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.config;

import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class AiRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints
        .reflection()
        .registerType(
            AiProperties.class,
            typeHint ->
                typeHint.withMethod(
                    "isEnabledConfigurationComplete", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            AiProperties.Groq.class,
            typeHint ->
                typeHint.withMethod("isTimeoutPositive", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            AiProperties.Gemini.class,
            typeHint ->
                typeHint.withMethod(
                    "isTimeoutWithinHttpOptionsRange", List.of(), ExecutableMode.INVOKE));
  }
}
```

Do not add constructors, fields, record accessors, or `MemberCategory.INVOKE_PUBLIC_METHODS`; the observed failure requires invocation of these validation methods only.

- [ ] **Step 2: Import the registrar from the existing AI configuration boundary**

Modify `AiConfig.java` to be exactly:

```java
package dev.krishnamurti.ai_chess_rivals.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/** Activates configuration properties for the AI module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@ImportRuntimeHints(AiRuntimeHints.class)
public class AiConfig {}
```

Do not move the hint registration into `AiProperties`, `AiProviderConfiguration`, or global application configuration.

- [ ] **Step 3: Run the focused runtime-hints test and verify GREEN**

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest test
```

Expected: PASS.

If compilation fails because a Spring AOT API signature differs from the code above, check the actual Spring Framework version resolved by this repository and adapt only the API call shape. Preserve the exact three method-invocation hints and do not switch to Graal JSON as a shortcut.

- [ ] **Step 4: Run the existing configuration validation tests**

```sh
./server/mvnw -f server/pom.xml -Dtest=AiPropertiesBindingTest,AiConfigurationContextTest test
```

Expected: both test classes PASS. This confirms that adding native hints did not alter JVM binding, validation, enabled/disabled topology, or gateway construction semantics.

- [ ] **Step 5: Apply formatting if needed**

POSIX:

```sh
./server/mvnw -f server/pom.xml spotless:apply
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
```

Then rerun:

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest,AiPropertiesBindingTest,AiConfigurationContextTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the focused fix**

```sh
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java

git commit -m "fix: register native AI validation reflection hints"
```

---

### Task 3: Run repository verification without widening scope

**Files:**
- No planned code changes.
- Inspect only if verification exposes a real regression caused by Task 2.

**Interfaces:**
- Consumes: the completed runtime-hints fix.
- Produces: fresh local verification evidence before pushing.

- [ ] **Step 1: Run backend verification**

From repository root:

```sh
./server/mvnw -f server/pom.xml verify
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: Java 25/Maven checks, formatting, Error Prone, tests, Modulith verification, and SpotBugs all PASS.

- [ ] **Step 2: If the repository verifier is practical in the current environment, run it too**

POSIX:

```sh
./scripts/verify.sh
```

Windows:

```powershell
.\scripts\verify.ps1
```

Expected: PASS.

If frontend-only tooling is unavailable locally, do not modify frontend code or CI to compensate; record the limitation and rely on hosted CI for that unchanged area.

- [ ] **Step 3: Inspect the final diff**

```sh
git diff HEAD~1 -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java
```

Confirm the implementation contains only:

1. exact method invocation hints,
2. `@ImportRuntimeHints(AiRuntimeHints.class)`, and
3. the focused runtime-hints test.

No Docker, workflow, provider, gateway, or validation-rule changes belong in this fix.

---

### Task 4: Push and use hosted native CI as the decisive test

**Files:**
- No planned source changes unless fresh CI evidence identifies a new root cause.

**Interfaces:**
- Consumes: the green local commit.
- Produces: artifact-level evidence from the actual GraalVM native binary.

- [ ] **Step 1: Push the branch**

```sh
git push origin feature/issue-55-native-ai-enablement
```

- [ ] **Step 2: Inspect the new PR #56 CI run**

Required expectations:

- `CI / Backend verification`: PASS
- `CI / Native image verification` build step: PASS
- native application remains up long enough to become healthy
- log contains `AI gateway topology: enabled (Groq -> Gemini)`
- log does not contain `AI gateway topology: disabled`
- final-image provider environment leak assertion: PASS

- [ ] **Step 3: Apply the stop conditions mechanically**

Use the first matching condition:

1. **Same `MissingReflectionRegistrationError` for one of the three registered methods** — the hints were not contributed to AOT. Do not add more hints. Verify `@ImportRuntimeHints(AiRuntimeHints.class)` is present in the AOT-processed configuration and inspect generated AOT/native metadata using `superpowers:systematic-debugging`.
2. **Different `MissingReflectionRegistrationError`** — capture the exact type/method and stack path. Stop and report it before widening reflection registration.
3. **Application exits for a non-reflection error** — capture the first root-cause `Caused by:` and debug that specific failure. Do not alter the reflection fix unless the evidence points back to it.
4. **Application becomes healthy and emits the enabled marker, but the job still fails** — the reflection/startup problem is solved; inspect the later CI assertion such as final-image environment metadata. Do not change AI AOT topology.
5. **Native job passes** — issue #55's native runtime gate has fresh evidence of success. Only then report the fix as verified.

- [ ] **Step 4: Do not clean up unrelated native warnings in this PR**

The observed Flyway `unsupported protocol: resource` warnings are not part of this fix because migrations subsequently validate and apply successfully. Do not change Flyway configuration unless a future failure proves those warnings are causative.

---

## Completion Criteria

This plan is complete only when all of the following are true:

- `AiRuntimeHintsTest` proves invocation hints exist for all three `@AssertTrue` methods.
- Existing `AiPropertiesBindingTest` and `AiConfigurationContextTest` remain green.
- Backend verification is green.
- No Docker/CI/provider/gateway/validation semantics were changed as part of the reflection fix.
- A fresh hosted `CI / Native image verification` run starts the actual native application successfully and passes its existing enabled-topology and image-metadata checks.

If hosted native CI is still red, do not claim PR #56 is fixed; report the new first root cause and its exact evidence.
