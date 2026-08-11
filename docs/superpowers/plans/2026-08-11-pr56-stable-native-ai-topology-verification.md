# PR #56 Stable Native AI Topology Verification Implementation Plan

> **For Luna:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (preferred) or `superpowers:executing-plans` to implement this plan task-by-task. Use `superpowers:test-driven-development` for Task 1. If hosted native CI reaches the stop condition in Task 4, use `superpowers:systematic-debugging` and report evidence instead of redesigning the AI configuration.

**Goal:** Make PR #56's native AI topology verification stable by replacing assertions against Spring Framework's internal BeanFactory DEBUG log wording with a tiny application-owned startup contract emitted by `AiGatewayConfiguration`.

**Architecture:** Keep issue #55's existing Docker/AOT design unchanged. The native production image is still compiled with the AI-enabled Spring AOT topology, while runtime supplies provider keys/models. Add one stable INFO marker when the enabled gateway bean is successfully constructed and one when the disabled gateway bean is constructed. CI starts the real native artifact, waits for health, reads container logs, requires the enabled marker, forbids the disabled marker, and retains the existing final-image environment leak check. Because `enabledAiChatGateway(...)` cannot execute until both qualified `ProviderChatClient` dependencies resolve, its marker is an application-owned proof that the enabled provider/gateway dependency chain was constructible without relying on framework-internal bean creation log text.

**Tech Stack:** Java 25, Spring Boot 4.1.0, SLF4J, Spring Boot test output capture, GitHub Actions, Bash, Docker/Buildx, GraalVM Native Image Community 25, PostgreSQL 17.

---

## Source of Truth and Evidence Boundary

- Pull request: `#56 fix: make native AI topology AOT-safe`
- Branch: `feature/issue-55-native-ai-enablement`
- Current plan-writing head: `566879cc827aa232ce6fc631356a699a24edc72d`
- Issue: `#55 Phase 2: Make AI enablement safe for GraalVM native deployment`
- Original implementation plan: `docs/superpowers/plans/2026-08-09-graalvm-native-ai-enablement.md`
- Previous verifier correction: `docs/superpowers/plans/2026-08-10-pr56-native-topology-verifier-fix.md`
- Latest failing workflow run inspected while writing this plan: run `31352621090`, native job `93346269602`.

### Evidence already established

1. `Backend verification` succeeds.
2. `Frontend verification` succeeds.
3. `Build production native image` succeeds.
4. `Verify AI-enabled topology in native image` fails after the image is built.
5. The current verifier enables DEBUG logging for `org.springframework.beans.factory.support` and greps Spring's internal message text `Creating shared instance of singleton bean ...`.
6. `AiGatewayConfiguration` currently has no application-owned topology marker.
7. Existing `AiConfigurationContextTest` already proves the JVM enabled/disabled bean topologies without making real provider calls.

**Do not reinterpret the current CI failure as proof that the GraalVM AOT topology is wrong.** The observation mechanism itself is still brittle. This plan changes the observation contract first.

### This plan supersedes only the verifier mechanism

Do not rewrite the historical plans. Do not change issue #55's AOT build/runtime contract unless Task 4 produces new concrete evidence after the stable verifier is installed.

---

## Global Constraints

- Keep `server/Dockerfile` unchanged.
- Keep `server/docker-compose.yml` unchanged.
- Keep `server/src/main/resources/application.yaml` unchanged.
- Keep `AiProviderConfiguration` unchanged.
- Do not introduce an Actuator `beans` endpoint or any custom diagnostics HTTP endpoint.
- Do not depend on Spring Framework internal DEBUG log wording.
- Do not introduce a provider registry, plugin abstraction, probe service, listener, extra agent, framework, or new module.
- Do not make any real Groq/Gemini request in tests or CI.
- Do not use real provider credentials in tests, Docker build args, or CI.
- Do not log provider credentials, model values, prompts, or responses in the topology markers.
- Preserve the existing health readiness check, disposable PostgreSQL setup, fake runtime provider configuration, final-image provider env leak assertion, and `always()` cleanup.
- Keep the change backend/CI/documentation only; no frontend, chess, game, dialogue, persistence, or migration changes.

### Stable marker strings

Use these exact strings everywhere. Treat them as the CI contract:

```text
AI gateway topology: enabled (Groq -> Gemini)
AI gateway topology: disabled
```

Do not add dynamic provider/model values to them.

---

## File Map

**Modify:**

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
  - Lock the two application-owned marker strings with focused output assertions.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java`
  - Add one logger and one INFO marker in each enabled/disabled gateway factory method.
- `.github/workflows/ci.yml`
  - Remove BeanFactory DEBUG configuration and internal singleton-creation assertions.
  - Assert the stable application-owned markers from native container logs.
- `docs/BUILD_AND_VERIFY.md`
  - Replace BeanFactory DEBUG wording with application-owned gateway startup-marker verification.
- `docs/AI Chess Rivals - Tech Stack.md`
  - Make the native topology verification description match CI.

**Verify but normally do not modify:**

- `server/README.md` — its current native build/runtime contract does not describe BeanFactory DEBUG verification.

**Do not modify unless Task 4's stop condition produces separate evidence and a new plan is written:**

- `server/Dockerfile`
- `server/docker-compose.yml`
- `server/.env.example`
- `server/pom.xml`
- `server/src/main/resources/application.yaml`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java`
- provider/gateway implementations other than the two marker lines in `AiGatewayConfiguration`
- `client/**`

---

## Task 1: Define and Implement the Application-Owned Topology Contract

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java`

### Step 1: Add failing output assertions first

- [ ] Add these imports to `AiConfigurationContextTest`:

```java
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
```

- [ ] Add this annotation to the test class:

```java
@ExtendWith(OutputCaptureExtension.class)
class AiConfigurationContextTest {
```

- [ ] Change the disabled-mode test signature to accept captured output:

```java
void disabledModeCreatesOnlyFallbackGateway(CapturedOutput output) {
```

- [ ] After the existing disabled gateway assertions, add:

```java
assertThat(output.getAll())
    .contains("AI gateway topology: disabled")
    .doesNotContain("AI gateway topology: enabled (Groq -> Gemini)");
```

- [ ] Change the enabled-mode test signature to accept captured output:

```java
void enabledModeCreatesBothNamedProviderModelsAndClientsAndOneGateway(CapturedOutput output) {
```

- [ ] After the existing enabled topology assertions, add:

```java
assertThat(output.getAll())
    .contains("AI gateway topology: enabled (Groq -> Gemini)")
    .doesNotContain("AI gateway topology: disabled");
```

Keep all existing bean/topology assertions. Do not replace them with log assertions.

### Step 2: Prove the new contract test fails before production code changes

- [ ] From repository root, run:

```bash
./server/mvnw -f server/pom.xml -Dtest=AiConfigurationContextTest test
```

On Windows PowerShell use:

```powershell
.\server\mvnw.cmd -f server\pom.xml -Dtest=AiConfigurationContextTest test
```

Expected: the test(s) fail only because the new marker text is absent. If they fail for imports/test infrastructure instead, fix only the focused test setup before proceeding.

### Step 3: Add the minimal production markers

- [ ] In `AiGatewayConfiguration`, add:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

- [ ] Add one class-level logger:

```java
private static final Logger log = LoggerFactory.getLogger(AiGatewayConfiguration.class);
```

- [ ] In `enabledAiChatGateway(...)`, immediately before returning the gateway, add exactly:

```java
log.info("AI gateway topology: enabled (Groq -> Gemini)");
```

The method should be equivalent to:

```java
@Bean
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
AiChatGateway enabledAiChatGateway(
    @Qualifier("groqProviderChatClient") ProviderChatClient groq,
    @Qualifier("geminiProviderChatClient") ProviderChatClient gemini) {
  log.info("AI gateway topology: enabled (Groq -> Gemini)");
  return new FailoverAiChatGateway(groq, gemini);
}
```

- [ ] In `disabledAiChatGateway()`, immediately before returning the fallback gateway, add exactly:

```java
log.info("AI gateway topology: disabled");
```

Do not add any further topology logging elsewhere.

### Step 4: Re-run the focused test

- [ ] Run the same `AiConfigurationContextTest` command.

Expected: PASS.

Why this contract is sufficient: Spring can only invoke `enabledAiChatGateway(groq, gemini)` after resolving both qualified `ProviderChatClient` arguments. Those provider clients are built from their named Spring AI `ChatClient`/`ChatModel` chains in `AiProviderConfiguration`. Therefore an emitted enabled marker proves the application-owned enabled gateway/provider dependency chain successfully reached construction. CI does not need to know Spring's internal singleton logging format or separately inspect every internal bean name.

### Step 5: Keep the Java change deliberately tiny

- [ ] Review the Java diff.

Expected production delta in `AiGatewayConfiguration`:
- 2 SLF4J imports
- 1 logger field
- 2 INFO statements

If additional abstractions/classes/configuration appear, remove them.

---

## Task 2: Replace the BeanFactory DEBUG Native Probe with Marker Assertions

**File:**
- Modify: `.github/workflows/ci.yml`

### Step 1: Remove CI-only Spring internal DEBUG logging

- [ ] In the native app `docker run` command, delete exactly:

```bash
-e LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BEANS_FACTORY_SUPPORT=DEBUG \
```

Do not replace it with another Spring internal logger or `SPRING_APPLICATION_JSON` logging override.

### Step 2: Preserve startup and health verification

- [ ] Keep unchanged:
  - Docker network creation
  - disposable `postgres:17-alpine`
  - database readiness polling
  - fake runtime `AI_*` values
  - `AI_ENABLED=true`
  - owner token
  - native application startup
  - current `/actuator/health` polling loop
  - behavior that prints app logs if the container exits or never becomes healthy

No real provider call is needed merely to construct the provider clients/gateway.

### Step 3: Replace internal singleton log assertions

- [ ] Keep capturing container logs after health:

```bash
log_file="/tmp/native-ai-app.log"
docker logs "$app" > "$log_file" 2>&1
```

- [ ] Delete the entire current verifier logic that depends on any of these strings/functions:

```text
Creating shared instance of singleton bean
assert_created
groqChatModel
geminiChatModel
enabledAiChatGateway
disabledAiChatGateway
```

The CI artifact probe should no longer parse Spring's bean factory implementation logs.

- [ ] Replace it with explicit application-marker assertions equivalent to:

```bash
          enabled_marker="AI gateway topology: enabled (Groq -> Gemini)"
          disabled_marker="AI gateway topology: disabled"

          if ! grep -Fq "$enabled_marker" "$log_file"; then
            echo "Expected AI-enabled native topology marker was not emitted"
            echo "AI topology markers found in application logs:"
            grep -F "AI gateway topology:" "$log_file" || true
            docker logs "$app"
            exit 1
          fi

          if grep -Fq "$disabled_marker" "$log_file"; then
            echo "AI-disabled topology marker was emitted by the AI-enabled native artifact"
            echo "AI topology markers found in application logs:"
            grep -F "AI gateway topology:" "$log_file" || true
            docker logs "$app"
            exit 1
          fi
```

Use exact fixed-string matching. Keep diagnostic greps protected with `|| true` under `set -euo pipefail`.

### Step 4: Preserve final-image environment leak protection

- [ ] Keep this existing behavior unchanged after marker assertions:

```bash
image_env="$(docker image inspect ai-chess-rivals:native-ci --format '{{range .Config.Env}}{{println .}}{{end}}')"
if grep -Eq '^AI_(GROQ|GEMINI)_(API_KEY|MODEL)=' <<< "$image_env"; then
  echo "Provider credential/model environment values are baked into the final image"
  exit 1
fi
```

This is independent of topology observation and remains valuable.

### Step 5: Preserve cleanup

- [ ] Keep `Clean up native topology verification` with `if: ${{ always() }}` and removal of both containers/network.

### Step 6: Static sanity checks

- [ ] From repository root, run:

```bash
grep -nE 'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BEANS_FACTORY_SUPPORT|Creating shared instance of singleton bean|assert_created' .github/workflows/ci.yml
```

Expected: no output.

- [ ] Run:

```bash
grep -nF 'AI gateway topology: enabled (Groq -> Gemini)' .github/workflows/ci.yml
grep -nF 'AI gateway topology: disabled' .github/workflows/ci.yml
```

Expected: both marker contracts are represented in the native verifier.

- [ ] Confirm there is still no `/actuator/beans` or runtime `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,beans` probe:

```bash
grep -nE 'actuator/beans|MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE' .github/workflows/ci.yml
```

Expected: no output.

---

## Task 3: Align Documentation with the Stable Verification Contract

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`
- Verify only: `server/README.md`

### Step 1: Update `docs/BUILD_AND_VERIFY.md`

- [ ] In `## Native AI topology verification`, replace the claim that CI enables Spring BeanFactory DEBUG logging with wording equivalent to:

```markdown
The GitHub Actions native job does more than compile the GraalVM image. It builds the production
Dockerfile with the AI-enabled AOT topology, loads the resulting image, and starts it against a
disposable PostgreSQL 17 container using fake runtime provider values.

`AiGatewayConfiguration` emits a small application-owned startup marker for the topology that was
constructed. After the native application becomes healthy, CI captures its container logs,
requires the AI-enabled gateway marker, and requires the AI-disabled gateway marker to be absent.
Because construction of the enabled gateway requires both qualified provider clients to resolve,
this verifies the enabled provider/gateway chain without depending on Spring Framework internal
bean-creation logs or exposing an additional Actuator endpoint. CI also verifies that provider
key/model environment values are not present in the final image configuration. The check performs
no real Groq/Gemini request and requires no provider secret.
```

Keep the existing JVM default / Docker Compose rebuild paragraph unchanged.

### Step 2: Update `docs/AI Chess Rivals - Tech Stack.md`

- [ ] In `### Native AI topology`, replace the current BeanFactory DEBUG wording with wording equivalent to:

```markdown
local AI-disabled images remain straightforward. CI starts the actual native image and verifies
the enabled application-owned provider/gateway chain through a stable startup marker emitted by
`AiGatewayConfiguration`, without depending on Spring BeanFactory DEBUG log wording or exposing
an additional Actuator endpoint.
```

Do not change the underlying AOT build/runtime contract.

### Step 3: Check `server/README.md`

- [ ] Confirm its `### Native AI build/runtime contract` remains accurate and does not claim CI uses BeanFactory DEBUG logs. No change is expected there.

### Step 4: Search for stale verifier claims in active documentation/workflow

- [ ] Run:

```bash
grep -RniE 'BeanFactory DEBUG|Creating shared instance of singleton bean|CI-only BeanFactory|Actuator `beans`|actuator/beans' \
  .github/workflows/ci.yml \
  docs/BUILD_AND_VERIFY.md \
  'docs/AI Chess Rivals - Tech Stack.md' \
  server/README.md
```

Expected: no stale claim from the active implementation/docs.

Do not edit the historical implementation plan files to erase the troubleshooting history.

---

## Task 4: Verify Locally, Push, and Let Hosted Native CI Decide the Next Step

### Step 1: Run focused Java verification

- [ ] Run:

```bash
./server/mvnw -f server/pom.xml -Dtest=AiConfigurationContextTest test
```

Expected: PASS, including both marker assertions and existing enabled/disabled topology assertions.

### Step 2: Run formatting if required

- [ ] If Spotless reports the Java edits need formatting:

```bash
./server/mvnw -f server/pom.xml spotless:apply
```

Then rerun the focused test.

### Step 3: Run repository verification

- [ ] POSIX:

```bash
./scripts/verify.sh
```

- [ ] Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

Expected: backend and frontend verification pass.

### Step 4: Inspect scope before commit

- [ ] Run:

```bash
git status --short
git diff -- \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java \
  .github/workflows/ci.yml \
  docs/BUILD_AND_VERIFY.md \
  'docs/AI Chess Rivals - Tech Stack.md'
```

Expected implementation scope is exactly those five files. The plan file is already committed separately by Sol.

Do not include opportunistic edits to Dockerfile, Compose, provider configuration, other Java classes, or frontend files.

### Step 5: Commit implementation

- [ ] Stage only the five implementation/docs files and create a focused commit, for example:

```bash
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiGatewayConfiguration.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiConfigurationContextTest.java \
  .github/workflows/ci.yml \
  docs/BUILD_AND_VERIFY.md \
  'docs/AI Chess Rivals - Tech Stack.md'

git commit -m "fix: use stable native AI topology marker"
git push
```

### Step 6: Inspect the new PR #56 CI run

Expected sequence:

1. `Backend verification` passes.
2. `Frontend verification` passes if triggered by the CI workflow change.
3. `Build production native image` passes.
4. Native app starts with fake runtime provider values and becomes healthy.
5. Container logs contain exactly the enabled topology contract at least once:
   `AI gateway topology: enabled (Groq -> Gemini)`.
6. Container logs do not contain:
   `AI gateway topology: disabled`.
7. Final-image metadata check finds no baked Groq/Gemini provider key/model env entries.
8. `Native image verification` passes.

### Step 7: Mandatory stop conditions if hosted CI still fails

After this plan, the verifier is application-owned. **Do not perform another speculative verifier redesign.** Use the failure mode as evidence.

#### Case A — native compilation fails before application startup

Stop and capture the native build error. This is a new failure mode; use `superpowers:systematic-debugging` before touching AOT configuration.

#### Case B — native app exits or does not become healthy

Stop and capture the full application logs already printed by CI. Identify the actual startup subsystem failure first. Do not conclude that AI topology is wrong merely because health failed.

#### Case C — health succeeds, enabled marker is absent, and disabled marker is present

This is concrete evidence that the native artifact selected the disabled gateway topology despite the intended AI-enabled AOT build. **STOP. Do not modify Dockerfile or conditional Java configuration automatically.** Report:
- workflow run/job ID,
- both marker grep results,
- complete relevant startup logs,
- the build args shown in the native image build step.

Then create a new debugging plan for the AOT property propagation itself.

#### Case D — health succeeds, neither enabled nor disabled marker is present

This is unexpected because one `AiChatGateway` topology should be constructed during normal context startup. STOP and capture logs/context evidence. Do not add another observation framework.

#### Case E — enabled marker is present and disabled marker absent, but job still fails

The topology has passed. Inspect the subsequent final-image environment assertion or other exact failing command. Do not touch AI topology.

---

## Definition of Done

- [ ] Existing JVM disabled topology test still passes.
- [ ] Existing JVM enabled topology test still passes.
- [ ] Tests lock both exact application-owned marker strings.
- [ ] `AiGatewayConfiguration` contains only the minimal logger + two marker statements needed for observability.
- [ ] CI no longer enables Spring BeanFactory DEBUG logging.
- [ ] CI no longer parses `Creating shared instance of singleton bean ...`.
- [ ] CI does not expose `/actuator/beans`.
- [ ] Native verifier waits for health before inspecting logs.
- [ ] Native verifier requires `AI gateway topology: enabled (Groq -> Gemini)`.
- [ ] Native verifier rejects `AI gateway topology: disabled`.
- [ ] Failure paths print useful marker/application logs.
- [ ] Final native image is still checked for baked provider key/model environment values.
- [ ] Cleanup remains `always()`.
- [ ] Active docs describe the stable application-owned marker mechanism.
- [ ] No real provider calls or secrets are used.
- [ ] No Docker/AOT/provider architecture changes are made without new evidence.
- [ ] Repository verifier passes locally.
- [ ] Hosted `CI / Native image verification` passes, or Luna stops with the exact evidence required by Task 4 instead of guessing.
