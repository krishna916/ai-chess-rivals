# Native WebSocket Record Serialization Hints Implementation Plan

> **For Luna:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the GraalVM native backend serialize every WebSocket record message used by `/ws/match` without `UnsupportedFeatureError`, while adding regression coverage that catches missing record reflection metadata before deployment.

**Architecture:** Extend the existing `GameNativeRuntimeHints` mechanism instead of adding a second native-metadata system. Use Spring's `BindingReflectionHintsRegistrar` for the WebSocket envelope and every concrete WebSocket payload root because Jackson 3 introspects Java record components at runtime and `MatchStreamMessage<T>` cannot reveal erased concrete payload types. Keep package-private WebSocket records package-private by resolving the required roots by class name rather than widening visibility only for AOT metadata. Add focused `RuntimeHintsPredicates` tests plus one native-container WebSocket smoke in the existing native CI job so this exact class of build-passes/runtime-fails regression is exercised against the GraalVM artifact.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Framework AOT `RuntimeHints`, Jackson 3.1.4, GraalVM Native Image, JUnit 5, AssertJ, GitHub Actions, Node.js 22 built-in WebSocket client.

**Spec:** GitHub issue #69 — `https://github.com/krishna916/ai-chess-rivals/issues/69`

## Global Constraints

- Preserve the existing WebSocket endpoint `/ws/match`, message envelope, payload schemas, reconnect behavior, and frontend configuration.
- Do not replace Java records with POJOs to avoid native metadata.
- Do not add `reflect-config.json`, GraalVM reachability metadata JSON, or a second native-hints subsystem.
- Extend the existing `GameNativeRuntimeHints` imported by `AiChessRivalsApplication`.
- Use Spring `BindingReflectionHintsRegistrar` because it registers constructors, properties, fields, and record components for reflection-based binding/serialization and traverses nested record/property types.
- Explicitly register all WebSocket serialization roots because `MatchStreamMessage<T>` has an erased generic payload:
  - `MatchStreamMessage`
  - `MatchStateMessage`
  - `NoMatchMessage`
  - `MatchStartedMessage`
  - `MovePlayedMessage`
  - `DialoguePlayedMessage`
  - `MatchStoppedMessage`
  - `MatchFinishedMessage`
- Do not widen `MatchStateMessage` or `NoMatchMessage` visibility merely so the config package can reference them.
- Preserve existing runtime hints for configuration validation, REST DTOs, enums, controllers, and WebSocket infrastructure unless a registration is directly replaced by the new binding registrar.
- Keep the fix scoped to native WebSocket serialization. Do not touch AI provider timeout/failover, Render port configuration, frontend URLs, or match execution behavior.
- Follow TDD for the hint contract: add the failing focused regression test first, prove it fails against the current hints, then implement the minimum metadata change.
- Do not claim the production bug is fixed until a freshly deployed native image has been exercised through `wss://ai-chess-api.krishnamurti.dev/ws/match` and Render logs are clean of the record-component error.
- Branch created for this plan: `fix/issue-69-native-websocket-record-hints`, based on master commit `afa7d8662f0ec45a72a4bb3cfbc7c81f91c7f4d5`.

---

### Task 1: Add a failing runtime-hints regression test for the complete WebSocket record family

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java`

**Interfaces:**
- Consumes: `GameNativeRuntimeHints.registerHints(RuntimeHints, ClassLoader)`.
- Produces: a regression contract proving every WebSocket record root is registered as a reflective type and every record component accessor is invokable; also proves representative nested DTOs reached from `MatchStateMessage` are covered.

- [ ] **Step 1: Add imports and the explicit expected WebSocket root list**

Add these imports:

```java
import dev.krishnamurti.ai_chess_rivals.game.web.DialogueResponse;
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
import java.lang.reflect.RecordComponent;
import java.util.List;
```

Add this constant inside `GameNativeRuntimeHintsTest` before the test methods:

```java
private static final List<String> WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES =
    List.of(
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStreamMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStateMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.NoMatchMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStartedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MovePlayedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.DialoguePlayedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStoppedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchFinishedMessage");
```

Keep string class names here because `MatchStateMessage` and `NoMatchMessage` are intentionally package-private in `game.websocket` and the hints test lives in `game.config`.

- [ ] **Step 2: Add the focused WebSocket binding-hints test**

Add this test:

```java
@Test
void registersWebSocketRecordBindingHintsForNativeSerialization() throws ClassNotFoundException {
  RuntimeHints hints = new RuntimeHints();
  ClassLoader classLoader = getClass().getClassLoader();
  new GameNativeRuntimeHints().registerHints(hints, classLoader);

  for (String typeName : WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES) {
    Class<?> type = Class.forName(typeName, false, classLoader);
    assertRecordBindingHints(hints, type);
  }

  // MatchStateMessage reaches these DTOs transitively through record components.
  assertRecordBindingHints(hints, MatchPersonalityResponse.class);
  assertRecordBindingHints(hints, DialogueResponse.class);
}
```

This test intentionally checks all eight explicit roots rather than only `MatchStreamMessage`. Registering only the envelope would allow the next native deployment to fail one level deeper when Jackson introspects a concrete payload record.

- [ ] **Step 3: Add a helper that checks both type registration and record accessor invocation**

Add this helper before the existing `assertFieldAccess` helper:

```java
private static void assertRecordBindingHints(RuntimeHints hints, Class<?> type) {
  assertThat(type.isRecord())
      .as("expected %s to remain a Java record", type.getName())
      .isTrue();

  assertThat(RuntimeHintsPredicates.reflection().onType(type).test(hints))
      .as("expected native reflection registration for %s", type.getName())
      .isTrue();

  for (RecordComponent component : type.getRecordComponents()) {
    assertMethodInvocation(hints, type, component.getAccessor().getName());
  }
}
```

The explicit `onType` assertion is required because `NoMatchMessage` is a zero-component record; iterating record components alone would otherwise let an unregistered `NoMatchMessage` pass vacuously.

- [ ] **Step 4: Run only the focused hints test and prove it fails before production code changes**

From `server/` run:

```bash
./mvnw -Dtest=GameNativeRuntimeHintsTest test
```

Expected before the fix:
- test run FAILS;
- the new test reports missing native reflection registration or accessor invocation for `MatchStreamMessage`, `MatchStateMessage`, `NoMatchMessage`, or another currently unregistered WebSocket record;
- existing configuration-validation hint tests remain green.

Do not modify `GameNativeRuntimeHints` until this red result is observed.

---

### Task 2: Register WebSocket records for Jackson binding in `GameNativeRuntimeHints`

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java`

**Interfaces:**
- Consumes: Spring `BindingReflectionHintsRegistrar`, current `RuntimeHints`, and the application class loader.
- Produces: runtime reflection metadata for the WebSocket envelope, all concrete WebSocket payload record roots, and nested record/property types used by Jackson serialization.

- [ ] **Step 1: Import the binding registrar and class resolver**

Add:

```java
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.util.ClassUtils;
```

Keep the existing `List`, `ExecutableMode`, `MemberCategory`, `RuntimeHints`, and `RuntimeHintsRegistrar` imports.

- [ ] **Step 2: Add the explicit WebSocket serialization-root constant**

Add this constant immediately inside `GameNativeRuntimeHints` before `registerHints`:

```java
private static final List<String> WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES =
    List.of(
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStreamMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStateMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.NoMatchMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStartedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MovePlayedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.DialoguePlayedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStoppedMessage",
        "dev.krishnamurti.ai_chess_rivals.game.websocket.MatchFinishedMessage");
```

Do not make package-private WebSocket DTOs public. Resolving by full class name is intentionally the smaller change.

- [ ] **Step 3: Add one helper that registers each root with Spring binding hints**

Add this method at the bottom of `GameNativeRuntimeHints` before the closing brace:

```java
private static void registerWebSocketSerializationHints(
    RuntimeHints hints, ClassLoader classLoader) {
  BindingReflectionHintsRegistrar bindingRegistrar = new BindingReflectionHintsRegistrar();

  for (String typeName : WEBSOCKET_SERIALIZATION_ROOT_TYPE_NAMES) {
    Class<?> type = ClassUtils.resolveClassName(typeName, classLoader);
    bindingRegistrar.registerReflectionHints(hints.reflection(), type);
  }
}
```

Why this shape:
- `BindingReflectionHintsRegistrar` registers record components and their accessor methods in the form GraalVM/Jackson needs;
- it follows nested property/record types, so `MatchPersonalityResponse`, `DialogueResponse`, `MoveResponse`, enums, and other reachable component types are discovered from the concrete roots;
- each concrete payload root is still explicit because `MatchStreamMessage<T>.payload` is erased to a type variable and cannot describe every runtime payload class by itself.

- [ ] **Step 4: Invoke the new helper from `registerHints`**

Immediately after the WebSocket infrastructure registrations (`MatchWebSocketConfig` and `MatchWebSocketHandler`) and before the existing REST JSON DTO registrations, add:

```java
// Jackson 3 introspects Java record components while serializing WebSocket messages in native mode.
registerWebSocketSerializationHints(hints, classLoader);
```

Update the nearby JSON comment so it distinguishes WebSocket binding hints from the remaining explicit REST/native registrations. Do not move or alter unrelated configuration-validation hints.

- [ ] **Step 5: Remove only the now-redundant one-off `MovePlayedMessage` reflection block**

Delete the existing block:

```java
hints
    .reflection()
    .registerType(
        dev.krishnamurti.ai_chess_rivals.game.websocket.MovePlayedMessage.class,
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
        MemberCategory.INVOKE_DECLARED_METHODS);
```

`MovePlayedMessage` is now covered by the WebSocket binding-root list. Leave `MatchResponse`, `MoveResponse`, `ChessPieceType`, `CastlingSide`, controller/config hints, and validation hints unchanged because they also support non-WebSocket/runtime paths and are outside this bug's cleanup scope.

- [ ] **Step 6: Run the focused hint test again**

From `server/`:

```bash
./mvnw -Dtest=GameNativeRuntimeHintsTest test
```

Expected: PASS. The new WebSocket record test and all existing configuration hint tests must be green.

- [ ] **Step 7: Run the existing WebSocket JVM tests to prove protocol behavior did not change**

Run:

```bash
./mvnw -Dtest=MatchWebSocketHandlerTest,MatchWebSocketIntegrationTest test
```

Expected: PASS. No WebSocket message type, JSON shape, initial-state behavior, or broadcast behavior should change.

- [ ] **Step 8: Run formatting/checkstyle through the normal Maven lifecycle for the touched Java files**

Run:

```bash
./mvnw -DskipTests verify
```

Expected: PASS. If this command reveals a repository verification requirement beyond formatting, fix only failures caused by the current changes; do not weaken the verification configuration.

- [ ] **Step 9: Commit the runtime-hints fix and focused tests**

From repository root:

```bash
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java

git commit -m "fix: register websocket records for native serialization"
```

---

### Task 3: Add a native-artifact WebSocket serialization smoke to CI

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: the existing `ai-chess-rivals:native-ci` Docker image, disposable PostgreSQL container, `SERVER_PORT=8080`, and `/ws/match` endpoint.
- Produces: an artifact-level CI failure if the GraalVM native binary accepts a WebSocket connection but closes before serializing the initial `NO_MATCH` frame.

**Why this task belongs in this bug:** the existing native CI proves the image builds, starts, exposes Actuator health, and creates the AI bean topology, but the production defect occurred afterward on an unexercised runtime serialization path. This smoke adds only one narrow runtime assertion and reuses the existing native container; it does not introduce a new testing framework.

- [ ] **Step 1: Set up Node.js 22 in the native job**

Inside the `native` job, after `Set up Docker Buildx`, add:

```yaml
      - name: Set up Node.js 22 for native WebSocket smoke
        uses: actions/setup-node@v7
        with:
          node-version: "22"
```

Do not configure npm caching; the smoke uses Node 22's built-in `WebSocket` client and installs no packages.

- [ ] **Step 2: Expose the native application's HTTP/WebSocket port to the runner**

Inside the existing `docker run -d` command for `native-ai-app`, change the published-port section from:

```bash
-p 8081:8081 \
-e SERVER_PORT=8080 \
```

to:

```bash
-p 8080:8080 \
-p 8081:8081 \
-e SERVER_PORT=8080 \
```

Port `8081` remains the Actuator management port. Port `8080` is exposed only so the CI runner can exercise the actual application WebSocket endpoint in the disposable native container.

- [ ] **Step 3: Add the initial-frame smoke immediately after the existing health check**

Still inside `Verify AI-enabled topology in native image`, after this existing successful health assertion:

```bash
if ! curl -fsS http://localhost:8081/actuator/health; then
  docker logs "$app"
  exit 1
fi
```

add:

```bash
if ! node --input-type=module <<'NODE'
const url = "ws://127.0.0.1:8080/ws/match";

await new Promise((resolve, reject) => {
  let receivedInitialMessage = false;
  const socket = new WebSocket(url);

  const timeout = setTimeout(() => {
    socket.close();
    reject(new Error(`Timed out waiting for the initial WebSocket frame from ${url}`));
  }, 10_000);

  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(String(event.data));
      if (message.type !== "NO_MATCH") {
        throw new Error(
          `Expected initial NO_MATCH frame from an empty CI database, received ${event.data}`,
        );
      }
      if (message.payload === null || typeof message.payload !== "object") {
        throw new Error(`Expected NO_MATCH payload object, received ${event.data}`);
      }

      receivedInitialMessage = true;
      clearTimeout(timeout);
      socket.close();
      resolve();
    } catch (error) {
      clearTimeout(timeout);
      socket.close();
      reject(error);
    }
  });

  socket.addEventListener("close", (event) => {
    if (!receivedInitialMessage) {
      clearTimeout(timeout);
      reject(
        new Error(
          `WebSocket closed before its initial frame: code=${event.code} reason=${event.reason}`,
        ),
      );
    }
  });

  socket.addEventListener("error", () => {
    clearTimeout(timeout);
    reject(new Error(`WebSocket connection failed for ${url}`));
  });
});

console.log("Native WebSocket initial NO_MATCH serialization verified");
NODE
then
  echo "Native WebSocket serialization smoke failed"
  docker logs "$app"
  exit 1
fi
```

The disposable CI database is empty, so `MatchWebSocketHandler.sendInitialMessage` must take its existing no-match branch and serialize:

```text
MatchStreamMessage<NoMatchMessage> -> {"type":"NO_MATCH","payload":{}}
```

The current production bug would fail this smoke because the socket closes during Jackson record introspection before the first frame is delivered.

- [ ] **Step 4: Add an explicit log guard for native record-component failures**

The existing workflow later writes application logs to:

```bash
log_file="/tmp/native-ai-app.log"
docker logs "$app" > "$log_file" 2>&1
```

Immediately after that, add:

```bash
if grep -Fq "Record components not available for record class" "$log_file"; then
  echo "Native application emitted a GraalVM record-component reflection failure"
  grep -F "Record components not available for record class" "$log_file" || true
  exit 1
fi
```

Keep the existing AI topology assertions unchanged.

- [ ] **Step 5: Review the workflow diff for accidental deployment changes**

From repository root run:

```bash
git diff -- .github/workflows/ci.yml
```

Expected changes are limited to:
- Node 22 setup in the native job;
- publishing application port `8080` for the disposable native container;
- the initial WebSocket `NO_MATCH` smoke;
- the native record-component log guard.

There must be no changes to GHCR publishing, Render deploy-hook behavior, image tags, AI credentials, or cleanup logic.

- [ ] **Step 6: Commit the native smoke guard**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: smoke test native websocket serialization"
```

---

### Task 4: Run repository verification and open the bug-fix PR

**Files:**
- Verify only unless a verification failure is directly caused by Tasks 1-3.

**Interfaces:**
- Produces: fresh JVM verification, native-image CI evidence, and a reviewable PR linked to issue #69.

- [ ] **Step 1: Prepare Linux Stockfish exactly like backend CI**

From repository root:

```bash
./server/mvnw -f server/pom.xml generate-resources -Plinux
```

Expected: PASS and `server/stockfish/stockfish` exists for backend tests requiring the engine.

- [ ] **Step 2: Run full backend verification using the CI Stockfish path**

On a POSIX shell from repository root:

```bash
STOCKFISH_PATH="$PWD/server/stockfish/stockfish" \
  ./server/mvnw -f server/pom.xml verify
```

Expected: PASS with zero test/verification failures.

If execution is on Windows and the Linux Stockfish binary cannot run locally, do not substitute unrelated Windows-native behavior into this bug. Run the focused Java tests locally, push the branch, and use the repository's Ubuntu backend CI as the authoritative Linux verification checkpoint.

- [ ] **Step 3: Inspect the final scoped diff**

Run:

```bash
git diff master...HEAD -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHints.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/config/GameNativeRuntimeHintsTest.java \
  .github/workflows/ci.yml

git diff --check master...HEAD
```

Expected:
- WebSocket protocol/handler/DTO source files are unchanged;
- only runtime hints, focused hint tests, and targeted native smoke verification changed;
- no broad AOT refactor;
- no frontend changes;
- no AI-routing changes;
- `git diff --check` reports no whitespace errors.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin fix/issue-69-native-websocket-record-hints
```

- [ ] **Step 5: Open a PR against `master` linked to issue #69**

Use title:

```text
fix: register websocket records for native serialization
```

Use this body:

```markdown
Closes #69

## Summary
- register the complete WebSocket record family with Spring binding reflection hints
- keep package-private WebSocket DTO visibility unchanged
- add focused RuntimeHints regression coverage for record accessors and nested DTOs
- smoke-test the initial WebSocket frame against the actual GraalVM native container in CI

## Verification
- `./mvnw -Dtest=GameNativeRuntimeHintsTest test`
- `./mvnw -Dtest=MatchWebSocketHandlerTest,MatchWebSocketIntegrationTest test`
- backend `verify`
- PR native image verification including the `/ws/match` `NO_MATCH` smoke
```

Do not merge automatically unless the user explicitly authorizes it.

- [ ] **Step 6: Wait for PR CI and inspect the actual native job result**

Required successful PR checks:
- `Backend verification`
- `Native image verification`

Within the native job, verify the log includes:

```text
Native WebSocket initial NO_MATCH serialization verified
```

and does not include:

```text
Record components not available for record class
```

If the native job fails, use `superpowers:systematic-debugging` and inspect the exact native log before proposing another metadata change. Do not add reflection categories or types speculatively.

---

### Task 5: Perform post-merge deployment acceptance without overstating completion

**Files:**
- No repository edits required unless production evidence reveals a separate defect.

**Interfaces:**
- Consumes: merged master CI, published GHCR SHA image, Render deploy hook, production WebSocket endpoint.
- Produces: evidence that issue #69 is actually resolved in the deployed GraalVM runtime.

- [ ] **Step 1: After the user merges the PR, verify master native CI publishes and triggers Render**

The master native job must complete these existing stages successfully:

```text
Build production native image
Verify AI-enabled topology in native image
Publish verified backend image
Trigger Render image deployment
```

Record the merged commit SHA. The Render deploy hook is passed that exact SHA-tagged GHCR image; do not treat a successful PR build alone as production deployment evidence.

- [ ] **Step 2: Wait until Render reports the SHA deployment as live**

Verify the image-backed Render service finishes deployment and remains healthy. Do not test while Render is still restarting or updating network configuration.

If Luna cannot inspect Render directly, stop and report the exact merged SHA plus the remaining manual Render check to the user rather than claiming deployment success.

- [ ] **Step 3: Verify the production WebSocket handshake and initial frame**

Open:

```text
https://ai-chess.krishnamurti.dev/#/
```

In browser DevTools -> Network -> WS, select:

```text
wss://ai-chess-api.krishnamurti.dev/ws/match
```

Acceptance evidence:
- handshake succeeds (`101 Switching Protocols` in browser tooling where shown);
- the socket remains open after establishment rather than immediately reconnecting;
- the first frame is either:
  - `NO_MATCH` when no match exists; or
  - `MATCH_STATE` when a match exists;
- there is no request to `ws://localhost:8082/ws/match`;
- normal REST calls remain on `https://ai-chess-api.krishnamurti.dev/api/v1/...`.

Do not start an extra AI match solely to prove this bug if a `NO_MATCH` frame already proves initial native serialization and the automated hint test covers all concrete payload roots. If a match is already active, additionally confirm a later `MOVE_PLAYED` or `DIALOGUE_PLAYED` frame arrives without disconnecting.

- [ ] **Step 4: Inspect Render logs while making a fresh WebSocket connection**

Production logs must not contain either of these after the fresh connection:

```text
UnsupportedFeatureError: Record components not available for record class
```

```text
Record components not available for record class dev.krishnamurti.ai_chess_rivals.game.websocket.MatchStreamMessage
```

Also confirm there is no new Jackson/native reflection failure for one of the concrete payload records. A moved failure such as `MatchStateMessage` or `NoMatchMessage` means the acceptance criteria are not met.

- [ ] **Step 5: Report acceptance evidence and only then close #69**

Report:
- PR number and merged commit SHA;
- PR native CI result;
- master publish/deploy result;
- observed production WebSocket URL;
- observed initial message type (`NO_MATCH` or `MATCH_STATE`);
- whether the socket remained open;
- confirmation that Render logs contain no record-component reflection error.

Only after those checks pass should issue #69 be considered resolved. If Luna lacks browser or Render access, leave the issue open and give the user the exact remaining acceptance checklist instead of claiming completion.

---

## Plan Self-Review Notes

- **Issue coverage:** The plan covers native runtime hints, all eight WebSocket record roots, representative nested DTOs, focused regression tests, existing JVM WebSocket tests, actual native-image runtime smoke, and production deployment acceptance from issue #69.
- **Scope discipline:** No WebSocket schema changes, no DTO visibility changes, no frontend changes, no AI routing changes, no Render-port changes, and no second metadata system.
- **Native-specific regression protection:** The focused hints test proves the metadata contract and the CI smoke exercises `ObjectMapper.writeValueAsString` through the real GraalVM `/ws/match` path, which is the path that escaped the existing build/start-only native verification.
- **Execution rule:** Implement inline with `superpowers:executing-plans`; never use `superpowers:subagent-driven-development`.
