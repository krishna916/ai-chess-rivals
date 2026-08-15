# PR #56 Native Topology Verifier Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix PR #56's failing native-image CI verification by replacing the AOT-unsafe runtime Actuator `beans` exposure with CI-only BeanFactory startup-log assertions, while leaving the successfully compiling AI-enabled native topology unchanged.

**Architecture:** Keep the existing Docker/AOT contract from issue #55: production native compilation selects the AI-enabled topology at build time and runtime supplies fake/real provider values. Change only the artifact-inspection mechanism: the CI verification container enables DEBUG logging for `org.springframework.beans.factory.support`, waits for health, captures its startup logs, and proves that the expected eager singleton beans were created. Continue inspecting final-image environment metadata to ensure provider key/model values are not baked into the image.

**Tech Stack:** GitHub Actions, Bash, Docker/Buildx, Spring Boot 4.1.0, Spring Framework BeanFactory logging, GraalVM Native Image Community 25, PostgreSQL 17.

## Source of Truth

- Pull request: `#56 fix: make native AI topology AOT-safe`
- Issue: `#55 Phase 2: Make AI enablement safe for GraalVM native deployment`
- Original plan: `docs/superpowers/plans/2026-08-09-graalvm-native-ai-enablement.md`
- Build/verification docs: `docs/BUILD_AND_VERIFY.md`
- Tech-stack docs: `docs/AI Chess Rivals - Tech Stack.md`
- Failed CI run for PR #56: native production image compilation succeeded; `Verify AI-enabled topology in native image` failed afterward.

This corrective plan supersedes only the Actuator-`beans` verification mechanism in the original issue #55 plan. Do not retroactively rewrite the original plan.

## Global Constraints

- Treat the successful `Build production native image` step as evidence that native compilation itself is currently working.
- Do not modify `server/Dockerfile`, `server/docker-compose.yml`, `server/pom.xml`, `server/src/main/resources/application.yaml`, or any Java source/test file as part of this correction.
- In particular, do not modify `AiProviderConfiguration`, `AiGatewayConfiguration`, `FailoverAiChatGateway`, or `DisabledAiChatGateway` unless the corrected verifier later produces concrete evidence that the native artifact topology is actually wrong.
- Do not expose Actuator `beans` at build time or in normal application configuration merely to make CI inspect bean topology.
- Keep the existing `/actuator/health` readiness check.
- Keep runtime AI values fake and CI-local; make no Groq/Gemini request and require no provider secret.
- Keep the final-image metadata assertion that rejects baked `AI_GROQ_*` / `AI_GEMINI_*` key/model environment values.
- Verification must fail with an explanatory message and application logs when it cannot prove the expected topology. Do not rely on silent `grep -q` failures under `set -e`.
- Keep this change infrastructure/documentation-only. No frontend, database migration, chess, game, dialogue, or personality changes.

## File Map

**Modify:**

- `.github/workflows/ci.yml`
  - Remove runtime Actuator `beans` exposure and `/actuator/beans` inspection.
  - Enable CI-only BeanFactory DEBUG logging and assert bean creation from startup logs.
- `docs/BUILD_AND_VERIFY.md`
  - Describe log-based topology verification instead of Actuator `beans` exposure.
- `docs/AI Chess Rivals - Tech Stack.md`
  - Replace the sentence that says CI verifies through a CI-only Actuator `beans` endpoint.

**Do not modify:**

- `server/Dockerfile`
- `server/docker-compose.yml`
- `server/.env.example`
- `server/README.md`
- `server/pom.xml`
- `server/src/main/resources/application.yaml`
- `server/src/main/java/**`
- `server/src/test/java/**`
- `client/**`

---

### Task 1: Replace the AOT-Sensitive Actuator Bean Probe

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: already-built/tagged `ai-chess-rivals:native-ci` image, existing disposable PostgreSQL container, existing runtime fake AI values, existing health endpoint.
- Produces: artifact-level proof that `groqChatModel`, `geminiChatModel`, and `enabledAiChatGateway` were created and `disabledAiChatGateway` was not created.
- Preserves: build args, Docker image tag/load behavior, PostgreSQL startup, health polling, cleanup, and final-image environment metadata inspection.

- [ ] **Step 1: Remove runtime Actuator `beans` exposure from the app container**

In the `Verify AI-enabled topology in native image` step, locate the app `docker run` command and remove exactly this environment entry:

```bash
-e MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,beans \
```

Do not replace it with an Actuator exposure setting elsewhere.

- [ ] **Step 2: Enable CI-only BeanFactory support DEBUG logging**

In the same `docker run` command, immediately before the image name, add:

```bash
-e LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BEANS_FACTORY_SUPPORT=DEBUG \
```

The tail of the app container command must therefore be equivalent to:

```bash
            -e AI_GEMINI_API_KEY=ci-runtime-gemini-key \
            -e AI_GEMINI_MODEL=ci-runtime-gemini-model \
            -e LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BEANS_FACTORY_SUPPORT=DEBUG \
            ai-chess-rivals:native-ci
```

Keep all existing fake provider values unchanged. Do not use `SPRING_APPLICATION_JSON` unless this exact package-level logging environment variable is proven ineffective by CI evidence.

- [ ] **Step 3: Keep the existing health/readiness probe unchanged**

Do not alter the current `for attempt in {1..60}` loop or the final health assertion except for formatting if necessary.

The application must become healthy before startup logs are used as topology evidence. If the container exits before health, continue printing `docker logs "$app"` and fail immediately as the workflow already does.

- [ ] **Step 4: Replace `/actuator/beans` and silent greps with startup-log assertions**

Delete this entire block:

```bash
          curl -fsS http://localhost:8081/actuator/beans > /tmp/native-ai-beans.json

          grep -q '"groqChatModel"' /tmp/native-ai-beans.json
          grep -q '"geminiChatModel"' /tmp/native-ai-beans.json
          grep -q '"enabledAiChatGateway"' /tmp/native-ai-beans.json

          if grep -q '"disabledAiChatGateway"' /tmp/native-ai-beans.json; then
            echo "disabledAiChatGateway is present in the AI-enabled native artifact"
            exit 1
          fi
```

Replace it with this block exactly, adjusting indentation only as required by YAML:

```bash
          log_file="/tmp/native-ai-app.log"
          docker logs "$app" > "$log_file" 2>&1

          if ! grep -Fq "Creating shared instance of singleton bean" "$log_file"; then
            echo "BeanFactory DEBUG logging produced no singleton-creation lines; native AI topology cannot be verified"
            docker logs "$app"
            exit 1
          fi

          assert_created() {
            local bean="$1"
            local pattern="Creating shared instance of singleton bean '$bean'"

            if ! grep -Fq "$pattern" "$log_file"; then
              echo "Expected native bean '$bean' was not created"
              echo "Relevant AI bean creation lines:"
              grep -E "groqChatModel|geminiChatModel|enabledAiChatGateway|disabledAiChatGateway" "$log_file" || true
              docker logs "$app"
              exit 1
            fi
          }

          assert_created "groqChatModel"
          assert_created "geminiChatModel"
          assert_created "enabledAiChatGateway"

          if grep -Fq "Creating shared instance of singleton bean 'disabledAiChatGateway'" "$log_file"; then
            echo "disabledAiChatGateway was created in the AI-enabled native artifact"
            docker logs "$app"
            exit 1
          fi
```

Why the first guard is mandatory: if the logging configuration is wrong, all three expected-bean checks would otherwise fail and falsely suggest that the native topology is wrong. The guard distinguishes "verifier cannot observe BeanFactory creation" from "specific AI bean was not created."

- [ ] **Step 5: Preserve the final-image environment assertion unchanged**

Immediately after the bean-creation assertions, retain this existing block:

```bash
          image_env="$(docker image inspect ai-chess-rivals:native-ci --format '{{range .Config.Env}}{{println .}}{{end}}')"
          if grep -Eq '^AI_(GROQ|GEMINI)_(API_KEY|MODEL)=' <<< "$image_env"; then
            echo "Provider credential/model environment values are baked into the final image"
            exit 1
          fi
```

Do not weaken or remove it.

- [ ] **Step 6: Confirm the workflow no longer depends on Actuator `beans`**

From the repository root, run:

```bash
grep -nE 'actuator/beans|MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE' .github/workflows/ci.yml
```

Expected: no output and exit code `1` because neither string remains.

Then run:

```bash
grep -n 'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BEANS_FACTORY_SUPPORT=DEBUG' .github/workflows/ci.yml
```

Expected: exactly one match in the native topology verification container command.

- [ ] **Step 7: Review the complete shell block for `set -euo pipefail` safety**

Confirm all diagnostic greps that are allowed to find nothing are protected with `|| true` or used as `if` conditions. In particular, this line must keep `|| true`:

```bash
grep -E "groqChatModel|geminiChatModel|enabledAiChatGateway|disabledAiChatGateway" "$log_file" || true
```

Do not add any new bare `grep -q` assertion whose failure would terminate the step without a diagnostic message.

---

### Task 2: Align Documentation with the Corrected Verification Mechanism

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`

**Interfaces:**
- Consumes: Task 1's final CI behavior.
- Produces: documentation that accurately distinguishes the native AOT topology contract from the CI-only observation mechanism.
- Preserves: all existing documentation about production AI-enabled AOT compilation, runtime-only provider credentials/models, Compose rebuild requirements, and no real provider calls.

- [ ] **Step 1: Replace the native topology verification paragraphs in `docs/BUILD_AND_VERIFY.md`**

Under `## Native AI topology verification`, replace the two paragraphs that currently describe temporary Actuator `beans` exposure and bean presence/absence with:

```markdown
The GitHub Actions native job does more than compile the GraalVM image. It builds the production
Dockerfile with the AI-enabled AOT topology, loads the resulting image, starts it against a
disposable PostgreSQL 17 container using fake runtime provider values, and enables Spring
BeanFactory DEBUG logging for that verification container only.

After the native application becomes healthy, CI captures its startup logs and requires creation
of `groqChatModel`, `geminiChatModel`, and `enabledAiChatGateway`, while requiring
`disabledAiChatGateway` not to be created. It also verifies that provider key/model environment
values are not present in the final image configuration. The check performs no real Groq/Gemini
request, requires no provider secret, and does not expose an additional Actuator endpoint.
```

Leave the following paragraph about JVM default AI-disabled mode and Docker Compose rebuild behavior unchanged.

- [ ] **Step 2: Replace the Actuator sentence in `docs/AI Chess Rivals - Tech Stack.md`**

In `### Native AI topology`, replace:

```markdown
local AI-disabled images remain straightforward. CI starts the actual native image and verifies
the application-owned provider/gateway beans through a CI-only Actuator `beans` exposure.
```

with:

```markdown
local AI-disabled images remain straightforward. CI starts the actual native image and verifies
the application-owned provider/gateway bean creation through CI-only BeanFactory DEBUG startup
logs without exposing an additional Actuator endpoint.
```

Do not change the preceding production build/runtime contract text.

- [ ] **Step 3: Search for stale claims introduced by PR #56**

From the repository root, run:

```bash
grep -RniE 'CI-only Actuator|temporarily exposes[[:space:]]+Actuator|Actuator `beans`|actuator/beans' \
  docs/BUILD_AND_VERIFY.md \
  'docs/AI Chess Rivals - Tech Stack.md' \
  .github/workflows/ci.yml
```

Expected: no output.

Do not edit the historical `docs/superpowers/plans/2026-08-09-graalvm-native-ai-enablement.md`; this corrective plan explicitly supersedes only that plan's old observation mechanism.

---

### Task 3: Verify the Correction Without Widening Scope

**Files:**
- Verify only; no additional source files should be modified by this task.

**Interfaces:**
- Consumes: Tasks 1-2.
- Produces: local repository verification plus authoritative GitHub Actions evidence from the actual production native image.

- [ ] **Step 1: Inspect the working-tree scope**

Run:

```bash
git status --short
git diff -- .github/workflows/ci.yml docs/BUILD_AND_VERIFY.md 'docs/AI Chess Rivals - Tech Stack.md'
```

Expected implementation files changed by this correction:

```text
.github/workflows/ci.yml
docs/BUILD_AND_VERIFY.md
docs/AI Chess Rivals - Tech Stack.md
```

The new plan file itself may also be present in the branch history. No server Java/config/build file or client file should be changed by the correction.

If `server/Dockerfile`, `server/docker-compose.yml`, Java source/tests, `application.yaml`, `pom.xml`, or `client/**` contains new corrective edits, stop and revert those edits unless there is separate concrete evidence and a revised plan authorizing them.

- [ ] **Step 2: Run the repository verifier**

On POSIX:

```bash
./scripts/verify.sh
```

On Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

Expected: backend and frontend verification complete successfully.

Do not make unrelated formatting/refactoring changes in response to pre-existing warnings outside this PR's scope.

- [ ] **Step 3: Commit the corrective implementation**

Stage only the three implementation/documentation files:

```bash
git add .github/workflows/ci.yml docs/BUILD_AND_VERIFY.md 'docs/AI Chess Rivals - Tech Stack.md'
git commit -m "fix: verify native AI topology from startup logs"
```

Do not amend the original PR commit unless the working workflow specifically requires it; a focused follow-up commit makes the CI correction auditable.

- [ ] **Step 4: Push the existing PR branch and inspect the new CI run**

Push:

```bash
git push
```

Then inspect PR #56's new `CI / Native image verification` run.

Expected sequence:

1. `Build production native image` succeeds.
2. `Verify AI-enabled topology in native image` starts PostgreSQL and the native app.
3. `/actuator/health` succeeds.
4. BeanFactory DEBUG logs contain singleton creation for `groqChatModel`.
5. BeanFactory DEBUG logs contain singleton creation for `geminiChatModel`.
6. BeanFactory DEBUG logs contain singleton creation for `enabledAiChatGateway`.
7. No singleton-creation line exists for `disabledAiChatGateway`.
8. Final-image environment metadata contains no `AI_GROQ_API_KEY`, `AI_GROQ_MODEL`, `AI_GEMINI_API_KEY`, or `AI_GEMINI_MODEL` entry.
9. Native image verification job passes.

- [ ] **Step 5: Apply the correct stop condition if CI still fails**

Use the failure mode to decide what happens next; do not guess.

**Case A — native build fails before the verification step:**

Capture the native compilation error. This is a different failure from the PR #56 run that motivated this plan. Stop and investigate the build separately before changing the verifier again.

**Case B — app exits or never becomes healthy:**

Use the already-printed container logs to identify the startup failure. Do not modify AI topology merely because health failed; prove the failing subsystem first.

**Case C — health succeeds, but the plan's BeanFactory DEBUG guard reports no singleton-creation lines:**

The observation mechanism is not active. Fix only the logger configuration/observation mechanism. Do not conclude that AI beans are absent.

**Case D — BeanFactory DEBUG logging is present, but one of `groqChatModel`, `geminiChatModel`, or `enabledAiChatGateway` lacks an exact creation line:**

This is the first concrete evidence that the successfully compiled native artifact may not contain/create the expected enabled topology. Stop implementation, preserve the CI logs, and report the exact missing bean before touching Docker/AOT or Java configuration.

**Case E — `disabledAiChatGateway` has an exact creation line:**

This is concrete evidence of an incorrect enabled native topology. Stop implementation, preserve the log evidence, and revise the issue #55 architecture/plan before changing Java configuration.

---

## Completion Criteria

PR #56's corrective work is complete only when all of the following are true:

- `.github/workflows/ci.yml` no longer exposes or calls Actuator `beans`.
- Native verification enables BeanFactory support DEBUG logging only in the short-lived CI app container.
- CI explicitly proves that BeanFactory creation logging is observable before asserting AI bean names.
- CI proves creation of `groqChatModel`, `geminiChatModel`, and `enabledAiChatGateway`.
- CI proves `disabledAiChatGateway` was not created.
- CI still proves provider key/model environment values are not baked into the final image.
- `docs/BUILD_AND_VERIFY.md` and `docs/AI Chess Rivals - Tech Stack.md` describe the log-based verification accurately.
- No Java/provider/AOT build-contract changes were made as part of this correction.
- Root repository verification passes.
- The new PR #56 `CI / Native image verification` run passes, or produces concrete bean-topology evidence that triggers one of the explicit stop conditions above.
