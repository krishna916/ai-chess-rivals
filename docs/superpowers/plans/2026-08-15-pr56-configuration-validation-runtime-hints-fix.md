# PR #56 Configuration Validation Runtime Hints Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan inline, task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the native reflection metadata required by Hibernate Validator for all application configuration-property records, so PR #56 can validate configuration in the GraalVM native runtime without another record-by-record `MissingReflectionRegistrationError` cycle.

**Architecture:** Keep the now-working application-owned `configurationPropertiesValidator` + `AlwaysTraversableResolver` architecture unchanged. Add explicit Spring `RuntimeHints` only for the application configuration records Hibernate Validator reflectively reads: declared-field access for every constrained configuration record, plus exact invocation hints for application `@AssertTrue` methods. Reuse the existing module hint registrars (`AiRuntimeHints`, chess `NativeRuntimeHints`, and `GameNativeRuntimeHints`) rather than creating another global registrar or reachability metadata file.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Framework 7, Spring AOT `RuntimeHints`, `RuntimeHintsPredicates`, Hibernate Validator, Jakarta Bean Validation, GraalVM Native Image, JUnit 5, AssertJ.

## Source of Truth

- Pull request: `#56 fix: make native AI topology AOT-safe`
- Branch: `feature/issue-55-native-ai-enablement`
- Head when this plan was prepared: `e8225760d314c8c819da479b0b6e4f07e9aaae10`
- Latest hosted CI: run `#55` / workflow run `31879332724`
- Latest native evidence:
  - backend verification passes;
  - frontend verification passes;
  - production native image compilation passes;
  - AI-enabled gateway marker is emitted;
  - startup now fails while Hibernate Validator reads `ChessProperties.Stockfish.Evaluation.moveTimeMillis`;
  - the stack no longer contains `ConfigurationPropertiesJsr303Validator`, `JPATraversableResolver`, or `PersistenceUtil`;
  - the current stack is `SpringValidatorAdapter -> Hibernate Validator -> JavaBeanField$FieldAccessor -> Field.get -> MissingReflectionRegistrationError`.
- This proves the previous validator-routing correction worked. Do not undo it.
- Existing AI hint coverage already registers declared-field access for `AiProperties`, `AiProperties.Groq`, and `AiProperties.Gemini`, plus exact invocation hints for the three AI `@AssertTrue` methods.
- Existing chess hint coverage registers only `ChessProperties` and `ChessProperties.Stockfish`; the nested `ChessProperties.Stockfish.Evaluation` record is missing.
- Existing game hint coverage registers fields for `GameProperties` and `GameProperties.MoveDelay`, but does not register exact `@AssertTrue` method invocation and does not cover `MatchGuardProperties`, `OwnerControlProperties`, or package-private `WebSocketProperties`.

## Global Constraints

- Stay on `feature/issue-55-native-ai-enablement` / PR #56.
- Execute this plan inline using `superpowers:executing-plans`.
- Keep `ValidationConfiguration` and `AlwaysTraversableResolver` unchanged.
- Keep the six configuration-property classes' current Jakarta validation annotations unchanged.
- Keep `@Validated` removed; do not reintroduce it.
- Do not add `reachability-metadata.json`.
- Do not add individual field-name entries to JSON metadata.
- Do not create a new global runtime-hints framework or registry.
- Reuse the existing hint registrars by module.
- Use Spring Framework 7 `MemberCategory.ACCESS_DECLARED_FIELDS`; do not add new uses of deprecated `MemberCategory.DECLARED_FIELDS`.
- Register only exact application `@AssertTrue` methods with `ExecutableMode.INVOKE`; do not broadly register all methods on configuration classes.
- Do not modify `AiRuntimeHints` unless a focused verification test proves its current coverage is incomplete. It is currently the reference pattern.
- Do not modify Docker, Docker Compose, `.github/workflows/ci.yml`, application YAML, AI provider/gateway logic, Stockfish behavior, JPA mappings, Flyway, or frontend code.
- Hosted `CI / Native image verification` remains the authoritative native-runtime gate.
- If the next native failure is outside the configuration-validation reflection surface documented here, stop and investigate it as a new root cause instead of broadening hints.

---

## Complete Configuration-Validation Reflection Surface

Treat this list as the bounded scope for this correction.

### AI — already covered, verify only

- `AiProperties`
  - declared fields: `enabled`, `groq`, `gemini`
  - method: `isEnabledConfigurationComplete()`
- `AiProperties.Groq`
  - declared fields: `apiKey`, `baseUrl`, `model`, `timeout`
  - method: `isTimeoutPositive()`
- `AiProperties.Gemini`
  - declared fields: `apiKey`, `model`, `timeout`
  - method: `isTimeoutWithinHttpOptionsRange()`

### Chess — update

- `ChessProperties`
  - declared field: `stockfish`
- `ChessProperties.Stockfish`
  - declared fields: `path`, `threads`, `hashMb`, `startupTimeoutSeconds`, `moveTimeoutSeconds`, `evaluation`
- `ChessProperties.Stockfish.Evaluation`
  - declared fields: `depth`, `moveTimeMillis`, `majorGainThresholdCentipawns`, `majorMistakeThresholdCentipawns`

### Game — update

- `GameProperties`
  - declared fields: `moveThinkTimeMillis`, `maxPlies`, `moveDelay`
- `GameProperties.MoveDelay`
  - declared fields: `min`, `max`
  - methods: `isMinimumNonNegative()`, `isMaximumNonNegative()`, `isValidRange()`
- `MatchGuardProperties`
  - declared fields: `cooldown`, `dailyStartLimit`
  - method: `isCooldownNonNegative()`
- `OwnerControlProperties`
  - declared field: `controlToken`
- package-private `dev.krishnamurti.ai_chess_rivals.game.websocket.WebSocketProperties`
  - declared field: `allowedOrigins`

---

## File Map

**Modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHints.java`
  - use `ACCESS_DECLARED_FIELDS` for existing chess configuration records;
  - add field-access metadata for `ChessProperties.Stockfish.Evaluation`;
  - replace the stale JPA-traversability comment with the current Hibernate Validator reason.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java`
  - use `ACCESS_DECLARED_FIELDS` for constrained game configuration records;
  - add exact invocation hints for four `@AssertTrue` methods;
  - register package-private `WebSocketProperties` by type name using `registerTypeIfPresent(...)`.

**Create:**

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHintsTest.java`
  - verifies every chess configuration backing field required by validation has reflection access.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java`
  - verifies all game/match/owner/websocket configuration fields and all game/match `@AssertTrue` method invocation hints.

**Preserve unchanged:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHintsTest.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java`
- all `*Properties.java` classes
- `.github/workflows/ci.yml`
- `server/Dockerfile`
- `server/docker-compose.yml`
- `client/**`

---

### Task 1: Complete Chess Configuration Validation Hints

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHintsTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHints.java`

**Interfaces:**
- Consumes: existing `NativeRuntimeHints.registerHints(RuntimeHints, ClassLoader)` and `ChessProperties` record hierarchy.
- Produces: reflective field-access metadata for every chess configuration record Hibernate Validator may inspect.

- [ ] **Step 1: Add the failing chess runtime-hints test**

Create `NativeRuntimeHintsTest.java` with this content:

```java
package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class NativeRuntimeHintsTest {

  @Test
  void registersAllChessConfigurationFieldsForNativeValidation() {
    RuntimeHints hints = new RuntimeHints();
    new NativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertFieldAccess(hints, ChessProperties.class, "stockfish");

    assertFieldAccess(hints, ChessProperties.Stockfish.class, "path");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "threads");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "hashMb");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "startupTimeoutSeconds");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "moveTimeoutSeconds");
    assertFieldAccess(hints, ChessProperties.Stockfish.class, "evaluation");

    assertFieldAccess(hints, ChessProperties.Stockfish.Evaluation.class, "depth");
    assertFieldAccess(hints, ChessProperties.Stockfish.Evaluation.class, "moveTimeMillis");
    assertFieldAccess(
        hints, ChessProperties.Stockfish.Evaluation.class, "majorGainThresholdCentipawns");
    assertFieldAccess(
        hints, ChessProperties.Stockfish.Evaluation.class, "majorMistakeThresholdCentipawns");
  }

  private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
    assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
        .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
        .isTrue();
  }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=NativeRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=NativeRuntimeHintsTest test
```

Expected: FAIL for at least the `ChessProperties.Stockfish.Evaluation` field assertions because that nested record is not currently registered.

Do not weaken the test to assert only `moveTimeMillis`; all four `Evaluation` fields belong to the same missing reflection boundary.

- [ ] **Step 3: Update the chess runtime hints**

In `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHints.java`, preserve the UUID registration and replace the current chess configuration hint block with:

```java
    // Hibernate Validator reflectively reads configuration-record backing fields when evaluating
    // Jakarta Bean Validation constraints in the native runtime.
    hints
        .reflection()
        .registerType(ChessProperties.class, MemberCategory.ACCESS_DECLARED_FIELDS);
    hints
        .reflection()
        .registerType(ChessProperties.Stockfish.class, MemberCategory.ACCESS_DECLARED_FIELDS);
    hints
        .reflection()
        .registerType(
            ChessProperties.Stockfish.Evaluation.class,
            MemberCategory.ACCESS_DECLARED_FIELDS);
```

Do not register individual chess fields one-by-one in production code.

- [ ] **Step 4: Run formatting and the focused test**

POSIX:

```sh
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=NativeRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=NativeRuntimeHintsTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the chess hint correction**

```sh
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHintsTest.java

git commit -m "fix: complete chess validation native hints"
```

The commit must not contain validator, property, Docker, CI, or unrelated changes.

---

### Task 2: Complete Game Configuration Validation Hints

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java`

**Interfaces:**
- Consumes: existing `GameNativeRuntimeHints.registerHints(RuntimeHints, ClassLoader)`, `GameProperties`, `MatchGuardProperties`, `OwnerControlProperties`, and package-private `WebSocketProperties`.
- Produces:
  - reflective field access for all constrained game-related configuration records;
  - exact invocation permission for all game/match `@AssertTrue` methods.

- [ ] **Step 1: Add the failing game runtime-hints test**

Create `GameNativeRuntimeHintsTest.java` with this content:

```java
package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class GameNativeRuntimeHintsTest {

  @Test
  void registersAllGameConfigurationFieldsForNativeValidation() throws ClassNotFoundException {
    RuntimeHints hints = new RuntimeHints();
    new GameNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertFieldAccess(hints, GameProperties.class, "moveThinkTimeMillis");
    assertFieldAccess(hints, GameProperties.class, "maxPlies");
    assertFieldAccess(hints, GameProperties.class, "moveDelay");

    assertFieldAccess(hints, GameProperties.MoveDelay.class, "min");
    assertFieldAccess(hints, GameProperties.MoveDelay.class, "max");

    assertFieldAccess(hints, MatchGuardProperties.class, "cooldown");
    assertFieldAccess(hints, MatchGuardProperties.class, "dailyStartLimit");

    assertFieldAccess(hints, OwnerControlProperties.class, "controlToken");

    Class<?> webSocketProperties =
        Class.forName(
            "dev.krishnamurti.ai_chess_rivals.game.websocket.WebSocketProperties",
            false,
            getClass().getClassLoader());
    assertFieldAccess(hints, webSocketProperties, "allowedOrigins");
  }

  @Test
  void registersGameAssertTrueMethodsForNativeInvocation() {
    RuntimeHints hints = new RuntimeHints();
    new GameNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertMethodInvocation(hints, GameProperties.MoveDelay.class, "isMinimumNonNegative");
    assertMethodInvocation(hints, GameProperties.MoveDelay.class, "isMaximumNonNegative");
    assertMethodInvocation(hints, GameProperties.MoveDelay.class, "isValidRange");
    assertMethodInvocation(hints, MatchGuardProperties.class, "isCooldownNonNegative");
  }

  private static void assertFieldAccess(RuntimeHints hints, Class<?> type, String fieldName) {
    assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(type, fieldName).test(hints))
        .as("expected native reflective field access for %s.%s", type.getName(), fieldName)
        .isTrue();
  }

  private static void assertMethodInvocation(RuntimeHints hints, Class<?> type, String methodName) {
    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(type, methodName).test(hints))
        .as("expected native reflective invocation for %s.%s", type.getName(), methodName)
        .isTrue();
  }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

POSIX:

```sh
./server/mvnw -f server/pom.xml -Dtest=GameNativeRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=GameNativeRuntimeHintsTest test
```

Expected: FAIL because current `GameNativeRuntimeHints` does not cover all listed configuration types and does not register the `@AssertTrue` methods for invocation.

- [ ] **Step 3: Add the required imports to `GameNativeRuntimeHints`**

Add:

```java
import java.util.List;
import org.springframework.aot.hint.ExecutableMode;
```

Keep the existing `MemberCategory`, `RuntimeHints`, and `RuntimeHintsRegistrar` imports.

- [ ] **Step 4: Replace the current game configuration hint block**

At the beginning of `registerHints(...)`, replace the existing two configuration-property registrations with this exact block:

```java
    // Hibernate Validator reflectively reads configuration-record backing fields and invokes
    // application @AssertTrue methods while evaluating Jakarta Bean Validation constraints.
    hints
        .reflection()
        .registerType(GameProperties.class, MemberCategory.ACCESS_DECLARED_FIELDS);

    hints
        .reflection()
        .registerType(
            GameProperties.MoveDelay.class,
            typeHint ->
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod("isMinimumNonNegative", List.of(), ExecutableMode.INVOKE)
                    .withMethod("isMaximumNonNegative", List.of(), ExecutableMode.INVOKE)
                    .withMethod("isValidRange", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(
            MatchGuardProperties.class,
            typeHint ->
                typeHint
                    .withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)
                    .withMethod("isCooldownNonNegative", List.of(), ExecutableMode.INVOKE));

    hints
        .reflection()
        .registerType(OwnerControlProperties.class, MemberCategory.ACCESS_DECLARED_FIELDS);

    hints
        .reflection()
        .registerTypeIfPresent(
            classLoader,
            "dev.krishnamurti.ai_chess_rivals.game.websocket.WebSocketProperties",
            typeHint -> typeHint.withMembers(MemberCategory.ACCESS_DECLARED_FIELDS));
```

Leave all existing controller, websocket-handler, response-model, and enum registrations below this block unchanged.

Do not make `WebSocketProperties` public merely so `GameNativeRuntimeHints` can reference it directly. `registerTypeIfPresent(...)` is the supported AOT mechanism here and preserves its current package visibility.

- [ ] **Step 5: Run formatting and the focused test**

POSIX:

```sh
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=GameNativeRuntimeHintsTest test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml -Dtest=GameNativeRuntimeHintsTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the game hint correction**

```sh
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java

git commit -m "fix: complete game validation native hints"
```

The commit must not modify game behavior, validation rules, validator wiring, websocket visibility, Docker, or CI.

---

### Task 3: Verify the Complete Configuration Validation Hint Contract

**Files:**
- No planned production changes.
- Verification inputs:
  - `AiRuntimeHintsTest`
  - new `NativeRuntimeHintsTest`
  - new `GameNativeRuntimeHintsTest`
  - existing configuration binding/validation tests.

**Interfaces:**
- Consumes: completed module runtime hints from Tasks 1 and 2.
- Produces: JVM evidence that all known application configuration-validation reflection requirements are represented before paying for another hosted native build.

- [ ] **Step 1: Run all runtime-hints tests together**

POSIX:

```sh
./server/mvnw -f server/pom.xml \
  -Dtest=AiRuntimeHintsTest,NativeRuntimeHintsTest,GameNativeRuntimeHintsTest \
  test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiRuntimeHintsTest,NativeRuntimeHintsTest,GameNativeRuntimeHintsTest test
```

Expected: PASS.

- [ ] **Step 2: Run the configuration validation regression set**

POSIX:

```sh
./server/mvnw -f server/pom.xml \
  -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,ChessPropertiesValidationTest,GamePropertiesBindingTest,MatchGuardPropertiesTest,OwnerControlPropertiesTest \
  test
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=ValidationConfigurationTest,AiPropertiesBindingTest,AiConfigurationContextTest,ChessPropertiesValidationTest,GamePropertiesBindingTest,MatchGuardPropertiesTest,OwnerControlPropertiesTest test
```

Expected: PASS with existing validation behavior unchanged.

- [ ] **Step 3: Run full backend verification**

POSIX:

```sh
./server/mvnw -f server/pom.xml verify
```

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: PASS.

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

If the unchanged frontend cannot run in Luna's local environment, record the environment limitation and rely on hosted CI for the unchanged frontend. Do not modify frontend files.

- [ ] **Step 5: Confirm the diff is bounded**

Run:

```sh
git diff HEAD~2 -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/NativeRuntimeHintsTest.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java
```

Confirm the implementation contains only:

1. chess configuration field-access hint completion;
2. game-related configuration field-access hint completion;
3. exact game/match `@AssertTrue` invocation hints;
4. focused RuntimeHints regression tests;
5. replacement of deprecated `DECLARED_FIELDS` usage touched by this change with `ACCESS_DECLARED_FIELDS`.

- [ ] **Step 6: Confirm protected files were not changed**

Run:

```sh
git diff HEAD~2 -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/AlwaysTraversableResolver.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiRuntimeHints.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/MatchGuardProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/OwnerControlProperties.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/WebSocketProperties.java \
  .github/workflows/ci.yml \
  server/Dockerfile
```

Expected: no output.

---

### Task 4: Push and Use Hosted Native CI as the Artifact Gate

**Files:**
- No planned source changes.

**Interfaces:**
- Consumes: the two green hint commits and the existing PR #56 workflow.
- Produces: authoritative proof that the actual GraalVM native artifact starts successfully with AI enabled.

- [ ] **Step 1: Push the branch**

```sh
git push origin feature/issue-55-native-ai-enablement
```

- [ ] **Step 2: Wait for PR #56 hosted CI and inspect the native job**

Required expected results:

```text
CI / Detect changes            PASS
CI / Backend verification      PASS
CI / Frontend verification     PASS
CI / Native image verification PASS
```

The native verification must prove all existing behavior:

- production native image builds;
- PostgreSQL starts;
- native application becomes healthy;
- `AI gateway topology: enabled (Groq -> Gemini)` appears;
- disabled topology marker does not appear;
- provider key/model environment values are not baked into the final image.

- [ ] **Step 3: Apply strict stop conditions if native CI is still red**

If the next native failure is another `MissingReflectionRegistrationError`, classify it before changing anything:

**Case A — one of the configuration types/methods explicitly covered by this plan:**

Stop. Treat that as evidence the RuntimeHints registration or import path is wrong. Do not add more metadata. Inspect why the test passed but the registrar was not effective in the native image.

**Case B — another application configuration-property record not listed in the bounded surface:**

Stop. First prove that the record is actually a `@ConfigurationProperties` type with Jakarta constraints before expanding scope.

**Case C — failure is outside configuration validation:**

Stop. Treat it as a new root cause and return to systematic debugging. Do not broaden configuration runtime hints.

**Case D — an exact `@AssertTrue` method listed in this plan still fails reflective invocation:**

Stop. Compare the generated method invocation hint against the runtime method signature and registrar import path. Do not replace exact method hints with `INVOKE_DECLARED_METHODS` on the whole class.

- [ ] **Step 4: Do not perform cleanup until native CI is green**

Even if some older hints later appear redundant, do not remove `AiRuntimeHints` or other native registrations in the same cycle. Cleanup belongs to a separate, optional follow-up after PR #56 is green.

---

## Final Acceptance Checklist

Before reporting this implementation complete, verify every item:

- [ ] `NativeRuntimeHintsTest` passes.
- [ ] `GameNativeRuntimeHintsTest` passes.
- [ ] `AiRuntimeHintsTest` still passes unchanged.
- [ ] Chess `Evaluation` has `ACCESS_DECLARED_FIELDS` coverage.
- [ ] Game `MoveDelay` has field coverage plus exact invocation hints for all three `@AssertTrue` methods.
- [ ] `MatchGuardProperties` has field coverage plus exact invocation for `isCooldownNonNegative()`.
- [ ] `OwnerControlProperties` has field coverage.
- [ ] package-private `WebSocketProperties` has field coverage without changing its visibility.
- [ ] No new use of deprecated `MemberCategory.DECLARED_FIELDS` is introduced.
- [ ] `ValidationConfiguration` and `AlwaysTraversableResolver` are unchanged.
- [ ] Jakarta validation annotations and messages are unchanged.
- [ ] No `reachability-metadata.json` exists.
- [ ] Full backend verification passes.
- [ ] Repository verification passes, or any environment-only limitation is documented without code changes.
- [ ] Hosted native CI passes before declaring PR #56 fixed.

## Implementation Handoff

Execute this plan inline with `superpowers:executing-plans`, task-by-task, with verification checkpoints after each task. Do not skip the RED/GREEN runtime-hints tests before pushing another native build.