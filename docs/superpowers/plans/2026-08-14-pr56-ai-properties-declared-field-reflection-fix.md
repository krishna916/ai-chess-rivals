# PR #56 AI Properties Declared-Field Reflection Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the AI-enabled GraalVM native application advance past Hibernate Validator's reflective access to `AiProperties` record backing fields by adding bounded declared-field runtime hints for the three AI configuration records.

**Architecture:** Keep PR #56's existing native AI topology, Docker build, runtime configuration, gateway marker, and CI verifier unchanged. Extend the existing `AiRuntimeHints` registrations so each `AiProperties` record type keeps its exact validation-method invocation hint and also declares `MemberCategory.ACCESS_DECLARED_FIELDS`. Update the focused runtime-hints test first, then use the existing hosted native-image job as the authoritative artifact-level gate.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Framework 7 AOT `RuntimeHints`, `MemberCategory.ACCESS_DECLARED_FIELDS`, Hibernate Validator / Jakarta Validation, GraalVM Native Image, JUnit 5, AssertJ.

## Global Constraints

- Stay on branch `feature/issue-55-native-ai-enablement` / PR #56.
- Current evidence: the earlier method-invocation reflection error is gone; the fresh native failure is `MissingReflectionRegistrationError` for reflective access to `AiProperties.Groq.timeout`.
- Treat that progression as evidence that the existing `AiRuntimeHints` registrar is imported and contributing to AOT.
- Preserve the existing exact invocation hints for:
  - `AiProperties#isEnabledConfigurationComplete()`
  - `AiProperties.Groq#isTimeoutPositive()`
  - `AiProperties.Gemini#isTimeoutWithinHttpOptionsRange()`
- Add `MemberCategory.ACCESS_DECLARED_FIELDS` for exactly:
  - `AiProperties`
  - `AiProperties.Groq`
  - `AiProperties.Gemini`
- Do not add one-off field hints such as only `Groq.timeout`; cover the complete declared fields of the three bounded configuration records.
- Do not add reflection hints for unrelated application classes.
- Do not add `reachability-metadata.json`.
- Do not change `AiProperties` validation semantics, record components, or method names.
- Do not modify `AiConfig`; it already imports `AiRuntimeHints` with `@ImportRuntimeHints(AiRuntimeHints.class)`.
- Do not modify `AiProviderConfiguration`, `AiGatewayConfiguration`, `FailoverAiChatGateway`, or `DisabledAiChatGateway`.
- Do not modify `server/Dockerfile`, `server/docker-compose.yml`, `.github/workflows/ci.yml`, or the build/runtime AI environment contract.
- Keep the current startup markers, health check, fake runtime provider values, image environment leak assertion, and cleanup logic unchanged.
- Do not address Flyway warnings in this correction.
- This is the last planned widening of the runtime-hints approach. If hosted native CI next reports a Hibernate Validator reflection requirement outside these three `AiProperties` records, stop and report the exact evidence instead of adding more reflection metadata.

---

### Task 1: Extend the runtime-hints regression test to declared fields

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`

**Interfaces:**
- Consumes: the existing `AiRuntimeHints`, `RuntimeHints`, `RuntimeHintsPredicates`, and the `AiProperties` record hierarchy.
- Produces: a failing regression test proving the current method-only registrar does not yet permit reflective access to the declared record fields Hibernate Validator traverses.

- [ ] **Step 1: Keep the existing method-invocation test unchanged**

Retain `registersAiPropertiesValidationMethodsForNativeInvocation()` and all three existing `onMethodInvocation(...)` assertions. They protect the previous fix and must not be replaced by the new field assertions.

- [ ] **Step 2: Add a new field-access test**

Add this second test to `AiRuntimeHintsTest.java`:

```java
@Test
void registersAiPropertiesDeclaredFieldsForNativeAccess() {
  RuntimeHints hints = new RuntimeHints();
  new AiRuntimeHints().registerHints(hints, getClass().getClassLoader());

  assertFieldAccess(hints, AiProperties.class, "enabled");
  assertFieldAccess(hints, AiProperties.class, "groq");
  assertFieldAccess(hints, AiProperties.class, "gemini");

  assertFieldAccess(hints, AiProperties.Groq.class, "apiKey");
  assertFieldAccess(hints, AiProperties.Groq.class, "baseUrl");
  assertFieldAccess(hints, AiProperties.Groq.class, "model");
  assertFieldAccess(hints, AiProperties.Groq.class, "timeout");

  assertFieldAccess(hints, AiProperties.Gemini.class, "apiKey");
  assertFieldAccess(hints, AiProperties.Gemini.class, "model");
  assertFieldAccess(hints, AiProperties.Gemini.class, "timeout");
}

private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
  assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
      .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
      .isTrue();
}
```

Do not add assertions for unrelated fields/classes.

- [ ] **Step 3: Run the focused test and verify RED**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest test
```

Expected: FAIL in `registersAiPropertiesDeclaredFieldsForNativeAccess()` because the current registrar only contains validation-method invocation hints and does not register declared-field access.

The existing validation-method test should remain green inside the same run.

- [ ] **Step 4: Do not commit the red state**

Continue directly to Task 2. The commit should contain the failing test and minimal production correction together in green state.

---

### Task 2: Add bounded declared-field access to the existing registrar

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`

**Interfaces:**
- Consumes: Spring `MemberCategory.ACCESS_DECLARED_FIELDS` and the existing three type registrations.
- Produces: one combined type hint per AI configuration record that permits declared-field access while preserving the existing exact validation-method invocation contract.

- [ ] **Step 1: Import `MemberCategory`**

Add:

```java
import org.springframework.aot.hint.MemberCategory;
```

Keep the existing `ExecutableMode`, `RuntimeHints`, and `RuntimeHintsRegistrar` imports.

- [ ] **Step 2: Extend each existing type registration with declared-field access**

Replace the body of `registerHints(...)` with this implementation:

```java
@Override
public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
  hints
      .reflection()
      .registerType(
          AiProperties.class,
          typeHint ->
              typeHint
                  .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                  .withMethod(
                      "isEnabledConfigurationComplete", List.of(), ExecutableMode.INVOKE));

  hints
      .reflection()
      .registerType(
          AiProperties.Groq.class,
          typeHint ->
              typeHint
                  .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                  .withMethod("isTimeoutPositive", List.of(), ExecutableMode.INVOKE));

  hints
      .reflection()
      .registerType(
          AiProperties.Gemini.class,
          typeHint ->
              typeHint
                  .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                  .withMethod(
                      "isTimeoutWithinHttpOptionsRange", List.of(), ExecutableMode.INVOKE));
}
```

Do not use deprecated `MemberCategory.DECLARED_FIELDS`. Use `ACCESS_DECLARED_FIELDS` exactly.

Do not add `INVOKE_DECLARED_METHODS`, constructor categories, or broader reflection categories.

- [ ] **Step 3: Run the focused hints test and verify GREEN**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest test
```

Expected: PASS for both:

- `registersAiPropertiesValidationMethodsForNativeInvocation`
- `registersAiPropertiesDeclaredFieldsForNativeAccess`

- [ ] **Step 4: Run the existing AI configuration regression tests**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest,AiPropertiesBindingTest,AiConfigurationContextTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest,AiPropertiesBindingTest,AiConfigurationContextTest test
```

Expected: PASS. Adding hints must not change JVM configuration binding, validation behavior, enabled/disabled topology, or gateway construction.

- [ ] **Step 5: Apply formatting if required and rerun focused tests**

POSIX:

```sh
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=AiRuntimeHintsTest,AiPropertiesBindingTest,AiConfigurationContextTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest,AiPropertiesBindingTest,AiConfigurationContextTest test
```

Expected: PASS.

- [ ] **Step 6: Commit only the focused correction**

```sh
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java

git commit -m "fix: register AI config fields for native validation"
```

Do not include Docker, CI, gateway, provider, Flyway, frontend, or unrelated cleanup changes in this commit.

---

### Task 3: Verify the bounded correction locally

**Files:**
- No planned source changes.

**Interfaces:**
- Consumes: the green Task 2 commit.
- Produces: fresh local evidence that the correction does not regress the backend before using the expensive hosted native build.

- [ ] **Step 1: Run backend verification**

POSIX:

```sh
./server/mvnw -f server/pom.xml verify
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: PASS for formatting, compilation/Error Prone, tests, Modulith verification, and SpotBugs.

- [ ] **Step 2: Run the repository verifier if available in the execution environment**

POSIX:

```sh
./scripts/verify.sh
```

Windows:

```powershell
.\scripts\verify.ps1
```

Expected: PASS.

If unchanged frontend tooling cannot run locally, record that limitation; do not modify frontend or CI to compensate.

- [ ] **Step 3: Inspect the correction diff**

```sh
git diff HEAD~1 -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java
```

Confirm the diff contains only:

1. `MemberCategory.ACCESS_DECLARED_FIELDS` added to the existing three record registrations;
2. preservation of all three exact method invocation hints;
3. field-access assertions covering all declared record components.

- [ ] **Step 4: Confirm `AiConfig` still imports the registrar without editing it**

Inspect:

```sh
grep -n "ImportRuntimeHints\|AiRuntimeHints" \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiConfig.java
```

Expected output includes:

```text
@ImportRuntimeHints(AiRuntimeHints.class)
```

Do not edit `AiConfig` if that import is already present.

---

### Task 4: Push and use hosted native CI as the decisive test

**Files:**
- No planned source changes unless the new CI run provides a new, specific root cause.

**Interfaces:**
- Consumes: the locally verified commit.
- Produces: artifact-level evidence from the actual GraalVM native executable built by PR #56.

- [ ] **Step 1: Push the feature branch**

```sh
git push origin feature/issue-55-native-ai-enablement
```

- [ ] **Step 2: Inspect the fresh PR #56 workflow**

Expected progression:

- `CI / Backend verification`: PASS
- `CI / Frontend verification`: PASS or unchanged/skipped according to path filtering
- `CI / Native image verification` → `Build production native image`: PASS
- native application remains running long enough to become healthy
- logs contain `AI gateway topology: enabled (Groq -> Gemini)`
- logs do not contain `AI gateway topology: disabled`
- final-image provider environment leak assertion: PASS

- [ ] **Step 3: Apply these stop conditions mechanically**

Use the first matching condition:

1. **Same field-reflection failure for `AiProperties`, `AiProperties.Groq`, or `AiProperties.Gemini`** — do not add individual field hints. Verify the new `ACCESS_DECLARED_FIELDS` registrations reached AOT/native metadata using `superpowers:systematic-debugging`.
2. **Different field-reflection failure, but still inside one of the same three record types** — do not widen categories automatically. Capture the exact member and inspect whether Spring's generated native metadata contains `ACCESS_DECLARED_FIELDS` for that type.
3. **Any new Hibernate Validator reflection failure outside the three `AiProperties` record types** — STOP. Do not add more runtime hints. Report the exact type/member/stack path and reconsider the configuration validation design (Option B: explicit Java validation instead of Bean Validation for this properties path).
4. **Application exits for a non-reflection error** — capture the first root-cause `Caused by:` and debug that failure separately. Do not change the reflection correction without evidence.
5. **Application becomes healthy and emits the enabled marker but the job still fails** — the startup/reflection problem is solved; inspect only the later CI assertion such as final-image environment metadata.
6. **Native job passes** — only then report PR #56's native runtime gate as fixed with fresh evidence.

- [ ] **Step 4: Do not perform unrelated cleanup**

Specifically leave these untouched during this correction:

- Flyway `unsupported protocol: resource` warnings
- explicit PostgreSQL dialect warning
- Docker image structure
- AI provider failover design
- CI topology marker mechanism
- provider credentials/model environment contract

---

## Completion Criteria

The correction is complete only when all of the following are true:

- `AiRuntimeHintsTest` still proves all three validation methods are invokable reflectively.
- `AiRuntimeHintsTest` proves reflective field access for every declared component of `AiProperties`, `AiProperties.Groq`, and `AiProperties.Gemini`.
- `AiPropertiesBindingTest` and `AiConfigurationContextTest` remain green.
- Backend verification is green.
- The implementation uses `MemberCategory.ACCESS_DECLARED_FIELDS`, not deprecated/broader reflection categories.
- No Docker, CI, provider, gateway, configuration-validation semantics, Flyway, or frontend behavior changed.
- A fresh hosted `CI / Native image verification` run starts the actual native application successfully and passes the existing topology and final-image metadata checks.

If hosted native CI remains red because validation requires reflection outside the three bounded AI configuration records, stop the runtime-hints expansion and report the new evidence before any further design change.