# GraalVM Native AI Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the production GraalVM native image reliably contain the AI-enabled Groq/Gemini bean topology while keeping real provider credentials runtime-only and preserving straightforward AI-disabled JVM/local development.

**Architecture:** Keep the existing Java AI configuration, provider chain, and `AiChatGateway` implementations unchanged. Treat `app.ai.enabled` as an AOT/build-time topology choice for native Docker images: production Docker builds compile with the enabled topology using fixed non-secret placeholders, while Docker Compose maps the build-time choice from its existing `AI_ENABLED` setting so local disabled mode remains convenient. CI must load and run the actual production native image, expose Actuator `beans` only for the verification container, and assert that the provider beans plus `enabledAiChatGateway` exist and `disabledAiChatGateway` does not.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, GraalVM Native Image Community 25, Docker/BuildKit, Docker Compose, GitHub Actions, PostgreSQL 17, Spring Boot Actuator.

## Source of Truth

- Issue: `#55 Phase 2: Make AI enablement safe for GraalVM native deployment`
- Discovered while reviewing: PR `#54 feat: implement contextual dialogue generation workflow`
- Phase 2 parent: `#4`
- Deployment/acceptance follow-up: `#46`
- Existing provider foundation plan: `docs/superpowers/plans/2026-08-07-spring-ai-provider-foundation.md`
- Existing native CI plan: `docs/superpowers/plans/2026-08-08-github-actions-ci-native-verification.md`
- Build instructions: `docs/BUILD_AND_VERIFY.md`
- Project constraints: `AGENTS.md`, `docs/AI Chess Rivals - Constitution.md`, `docs/AI Chess Context.md`

## Global Constraints

- Do not change `AiProviderConfiguration`, `AiGatewayConfiguration`, `FailoverAiChatGateway`, `DisabledAiChatGateway`, or the public AI API unless the primary build-time approach is proven impossible with concrete native-build/runtime evidence.
- Do not introduce a provider registry, plugin system, new AI framework, microservice, orchestration layer, or dynamic provider-selection abstraction.
- Preserve the existing Groq -> Gemini -> deterministic-fallback behavior exactly.
- Preserve `application.yaml` JVM/local default `AI_ENABLED=false`.
- Production native Docker builds must select the AI-enabled bean topology during Spring AOT processing.
- Real Groq/Gemini credentials must never be passed as Docker build args, Docker build secrets, Maven properties, or Dockerfile `ENV` values.
- Build-time provider values must be obvious fixed placeholders, not usable credentials.
- Runtime Groq/Gemini API keys and model names must continue to come from `AI_GROQ_API_KEY`, `AI_GROQ_MODEL`, `AI_GEMINI_API_KEY`, and `AI_GEMINI_MODEL`.
- `AI_ENABLED` may still be changed freely for normal JVM execution; for a native image its bean topology is fixed by the value selected during AOT compilation.
- Docker Compose must keep AI disabled by default and must rebuild the backend image when `AI_ENABLED` changes so build-time topology and runtime intent stay aligned.
- CI must prove the topology by running the actual native production image; native compilation success by itself is insufficient.
- CI verification must not make real Groq/Gemini network calls and must not require repository/provider secrets.
- Do not expose Actuator `beans` in the normal application configuration. Enable it only through the environment of the short-lived CI verification container.
- Keep the change backend/infrastructure-only. Do not modify frontend, database migrations, game logic, chess logic, dialogue behavior, or personality behavior.
- If the primary approach fails because Spring AI/provider construction cannot tolerate build-time placeholders or the expected beans still cannot be made reachable to AOT, stop and report the exact native build/runtime evidence on issue #55. Do not improvise the fallback architecture inside this implementation; revise the plan first.

## File Map

**Modify:**

- `server/Dockerfile`
  - Add one native-AOT topology build argument and scope fixed non-secret provider placeholders to the Maven native compilation command only.
- `server/docker-compose.yml`
  - Convert the backend `build` entry to the object form and map the native build-time topology argument from the existing `AI_ENABLED` value.
- `.github/workflows/ci.yml`
  - Load/tag the native image and run an artifact-level AI bean-topology verification against a disposable PostgreSQL container.
- `server/.env.example`
  - Explain that `AI_ENABLED` remains off by default and, for Docker Compose/native execution, changing it requires rebuilding the backend image.
- `server/README.md`
  - Document the native build-time/runtime contract and Render deployment variables.
- `docs/BUILD_AND_VERIFY.md`
  - Document the automated native AI topology gate and the no-real-provider-calls verification model.
- `docs/AI Chess Rivals - Tech Stack.md`
  - Record the Spring AOT constraint and the production native build contract.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/**`
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/**`
- `server/src/main/resources/application.yaml`
- `server/pom.xml`
- `server/src/main/resources/db/migration/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/**`
- `client/**`

---

### Task 1: Make Native AI Topology an Explicit Docker Build Contract

**Files:**
- Modify: `server/Dockerfile`
- Modify: `server/docker-compose.yml`

**Interfaces:**
- Consumes: existing runtime property `AI_ENABLED`, existing Groq/Gemini environment property names, existing Maven `-Pnative -Plinux` build.
- Produces: Docker build arg `AI_NATIVE_BUILD_ENABLED` with production default `true`; Docker Compose maps it from `${AI_ENABLED:-false}`.
- Preserves: existing runtime `AI_ENABLED` environment variable and all Java bean/configuration code.

- [ ] **Step 1: Add the native build-time topology selector to `server/Dockerfile`**

Immediately before the native compilation `RUN`, add this build argument and explanatory comment:

```dockerfile
# Spring AOT fixes conditional bean topology at build time.
# Direct/production Docker builds therefore compile the AI-enabled topology by default.
# Docker Compose overrides this arg from AI_ENABLED so local disabled mode remains available.
ARG AI_NATIVE_BUILD_ENABLED=true
```

Do not add `ARG` values for Groq/Gemini API keys or models.

- [ ] **Step 2: Scope non-secret AOT placeholders to the native Maven command only**

Replace the current native compilation command:

```dockerfile
RUN ./mvnw clean package native:compile -B -DskipTests -Pnative -Plinux
```

with:

```dockerfile
RUN AI_ENABLED="${AI_NATIVE_BUILD_ENABLED}" \
    AI_GROQ_API_KEY="aot-build-placeholder-groq-key" \
    AI_GROQ_MODEL="aot-build-placeholder-groq-model" \
    AI_GEMINI_API_KEY="aot-build-placeholder-gemini-key" \
    AI_GEMINI_MODEL="aot-build-placeholder-gemini-model" \
    ./mvnw clean package native:compile -B -DskipTests -Pnative -Plinux
```

Rules for this block:

- Keep the placeholders literal and obviously fake.
- Do not use Dockerfile `ENV` for these values.
- Do not accept real provider values through `ARG`.
- Keep `AI_GROQ_BASE_URL` on its existing application default.
- Do not change the runtime stage except where later verification proves necessary.

This lets Spring AOT evaluate `@ConditionalOnProperty(app.ai.enabled=true)` with the enabled topology while ensuring the final runtime stage receives no provider credential/model defaults from the builder stage.

- [ ] **Step 3: Make Docker Compose align AOT topology with its existing runtime switch**

In `server/docker-compose.yml`, replace:

```yaml
  backend:
    build: .
```

with:

```yaml
  backend:
    build:
      context: .
      args:
        AI_NATIVE_BUILD_ENABLED: ${AI_ENABLED:-false}
```

Keep the existing runtime environment entry unchanged:

```yaml
      AI_ENABLED: ${AI_ENABLED:-false}
```

Resulting behavior must be:

- ordinary `docker compose build` with no AI setting -> native image compiled with disabled topology and runtime AI disabled;
- `.env` with `AI_ENABLED=true` followed by a rebuild -> native image compiled with enabled topology and runtime AI enabled;
- direct production `docker build` with no build-arg override -> enabled topology because the Dockerfile default is `true`.

Do not add a second local-only Dockerfile or Spring profile.

- [ ] **Step 4: Validate the Compose model without starting services**

From `server/`, run:

```bash
docker compose --env-file .env.example config
```

Expected:

- command exits successfully;
- rendered backend build args contain `AI_NATIVE_BUILD_ENABLED` with the example/default value `false`;
- rendered backend runtime environment still contains `AI_ENABLED: "false"` (formatting/quoting may vary by Compose version).

If `.env.example` placeholder syntax in another required variable prevents Compose from rendering, use an existing local `.env` instead; do not edit secrets into tracked files.

- [ ] **Step 5: Build the production topology explicitly once**

From the repository root, run:

```bash
docker build \
  --build-arg AI_NATIVE_BUILD_ENABLED=true \
  -t ai-chess-rivals:native-ai-local \
  ./server
```

Expected: the GraalVM native build completes successfully without supplying any real Groq/Gemini API key.

If the build fails specifically because Spring AI/provider configuration rejects the fixed placeholders during AOT, capture the complete failing section of the native build log, stop this plan, and report it on issue #55. Do not modify Java configuration speculatively.

- [ ] **Step 6: Commit the build-contract change**

```bash
git add server/Dockerfile server/docker-compose.yml
git commit -m "fix: select AI topology during native build"
```

---

### Task 2: Prove the AI-Enabled Topology in the Actual Native Image

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `AI_NATIVE_BUILD_ENABLED=true` build arg from Task 1, production `server/Dockerfile`, PostgreSQL 17 image, Actuator management port `8081`.
- Produces: CI failure unless the loaded native image contains `groqChatModel`, `geminiChatModel`, and `enabledAiChatGateway`, excludes `disabledAiChatGateway`, starts with runtime-only fake provider configuration, and has no provider key/model variables baked into final image `ENV` metadata.
- Preserves: existing backend/frontend/native change detection and existing native BuildKit cache.

- [ ] **Step 1: Make the existing native build available to later CI steps**

In the `Build production native image` step under the `native` job, keep the current context/file/platform/cache settings and add:

```yaml
          load: true
          tags: ai-chess-rivals:native-ci
          build-args: |
            AI_NATIVE_BUILD_ENABLED=true
```

The full `with:` section should be equivalent to:

```yaml
        with:
          context: ./server
          file: ./server/Dockerfile
          platforms: linux/amd64
          push: false
          load: true
          tags: ai-chess-rivals:native-ci
          build-args: |
            AI_NATIVE_BUILD_ENABLED=true
          cache-from: type=gha,scope=server-native
          cache-to: type=gha,mode=max,scope=server-native
```

Do not pass `AI_GROQ_API_KEY`, `AI_GEMINI_API_KEY`, or model values to `docker/build-push-action`.

- [ ] **Step 2: Add an artifact-level native topology verification step**

Immediately after `Build production native image`, add this step exactly, adjusting indentation only as required by YAML:

```yaml
      - name: Verify AI-enabled topology in native image
        shell: bash
        run: |
          set -euo pipefail

          network="native-ai-verify"
          postgres="native-ai-postgres"
          app="native-ai-app"

          docker network create "$network"

          docker run -d \
            --name "$postgres" \
            --network "$network" \
            -e POSTGRES_DB=aichessrivals \
            -e POSTGRES_USER=postgres \
            -e POSTGRES_PASSWORD=secretpassword \
            postgres:17-alpine

          for attempt in {1..30}; do
            if docker exec "$postgres" pg_isready -U postgres -d aichessrivals >/dev/null 2>&1; then
              break
            fi
            sleep 1
          done
          docker exec "$postgres" pg_isready -U postgres -d aichessrivals

          docker run -d \
            --name "$app" \
            --network "$network" \
            -p 8081:8081 \
            -e SERVER_PORT=8080 \
            -e SPRING_DATASOURCE_URL=jdbc:postgresql://native-ai-postgres:5432/aichessrivals \
            -e SPRING_DATASOURCE_USERNAME=postgres \
            -e SPRING_DATASOURCE_PASSWORD=secretpassword \
            -e SPRING_FLYWAY_URL=jdbc:postgresql://native-ai-postgres:5432/aichessrivals \
            -e SPRING_FLYWAY_USER=postgres \
            -e SPRING_FLYWAY_PASSWORD=secretpassword \
            -e OWNER_CONTROL_TOKEN=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef \
            -e AI_ENABLED=true \
            -e AI_GROQ_API_KEY=ci-runtime-groq-key \
            -e AI_GROQ_MODEL=ci-runtime-groq-model \
            -e AI_GEMINI_API_KEY=ci-runtime-gemini-key \
            -e AI_GEMINI_MODEL=ci-runtime-gemini-model \
            -e MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,beans \
            ai-chess-rivals:native-ci

          for attempt in {1..60}; do
            if curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; then
              break
            fi

            if [[ "$(docker inspect -f '{{.State.Running}}' "$app")" != "true" ]]; then
              docker logs "$app"
              exit 1
            fi

            sleep 2
          done

          if ! curl -fsS http://localhost:8081/actuator/health; then
            docker logs "$app"
            exit 1
          fi

          curl -fsS http://localhost:8081/actuator/beans > /tmp/native-ai-beans.json

          grep -q '"groqChatModel"' /tmp/native-ai-beans.json
          grep -q '"geminiChatModel"' /tmp/native-ai-beans.json
          grep -q '"enabledAiChatGateway"' /tmp/native-ai-beans.json

          if grep -q '"disabledAiChatGateway"' /tmp/native-ai-beans.json; then
            echo "disabledAiChatGateway is present in the AI-enabled native artifact"
            exit 1
          fi

          image_env="$(docker image inspect ai-chess-rivals:native-ci --format '{{range .Config.Env}}{{println .}}{{end}}')"
          if grep -Eq '^AI_(GROQ|GEMINI)_(API_KEY|MODEL)=' <<< "$image_env"; then
            echo "Provider credential/model environment values are baked into the final image"
            exit 1
          fi
```

Why this check is intentionally narrow:

- fake runtime keys/models satisfy configuration and construct the clients without making a provider request;
- `/actuator/beans` proves the actual native artifact contains the expected provider and gateway beans;
- the disabled gateway assertion catches exactly the build-time-topology regression described by #55;
- the image metadata check prevents accidentally converting build placeholders into final-stage `ENV` defaults;
- no prompt, dialogue, or provider response is generated.

Do not assert Spring AI starter auto-configuration bean names unrelated to the application-owned provider beans; that would make this verification brittle across library upgrades.

- [ ] **Step 3: Add guaranteed cleanup for the disposable containers/network**

Immediately after the topology verification step, add:

```yaml
      - name: Clean up native topology verification
        if: ${{ always() }}
        shell: bash
        run: |
          docker rm -f native-ai-app native-ai-postgres 2>/dev/null || true
          docker network rm native-ai-verify 2>/dev/null || true
```

Keep cleanup separate so it runs even when a topology assertion fails.

- [ ] **Step 4: Review the CI security boundary before committing**

Confirm all of the following directly in `.github/workflows/ci.yml`:

- there is no `${{ secrets.* }}` reference in the native topology verification;
- all provider values used by the running verification container begin with `ci-runtime-`;
- no real provider endpoint is invoked;
- Actuator `beans` is enabled only via the verification container environment and not by editing `application.yaml`;
- the native image is still built once and then loaded, not rebuilt a second time for verification.

- [ ] **Step 5: Commit the native artifact verification**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: verify AI topology in native image"
```

---

### Task 3: Document the Build-Time vs Runtime AI Contract

**Files:**
- Modify: `server/.env.example`
- Modify: `server/README.md`
- Modify: `docs/BUILD_AND_VERIFY.md`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`

**Interfaces:**
- Consumes: `AI_NATIVE_BUILD_ENABLED` build behavior from Task 1 and artifact verification from Task 2.
- Produces: one consistent operator/developer contract for JVM, Docker Compose, CI, and Render.

- [ ] **Step 1: Clarify `AI_ENABLED` semantics in `server/.env.example`**

Replace the current AI section heading/comment:

```text
# Phase 2 AI provider configuration (AI stays disabled unless explicitly enabled)
AI_ENABLED=false
```

with:

```text
# Phase 2 AI provider configuration.
# JVM/local execution stays disabled unless explicitly enabled.
# Docker Compose also uses AI_ENABLED to select the native AOT bean topology at build time,
# so changing AI_ENABLED for the container requires rebuilding the backend image.
AI_ENABLED=false
```

Keep all existing provider variable names and existing example placeholders unchanged.

- [ ] **Step 2: Add a `Native AI build/runtime contract` subsection to `server/README.md`**

Under `## Production Deployment Differences`, before or within `### Production Configuration`, add content equivalent to:

```markdown
### Native AI build/runtime contract

Spring AOT fixes `@ConditionalOnProperty` bean presence while the native image is built. The
production Dockerfile therefore compiles with the AI-enabled topology by default using fixed,
non-secret placeholder provider values. Those placeholders exist only for AOT processing; real
Groq/Gemini credentials are not Docker build inputs and are not copied into the runtime image.

At runtime on Render, configure `AI_ENABLED=true`, then set `AI_GROQ_API_KEY` and
`AI_GEMINI_API_KEY` to the real provider secrets stored in Render and set `AI_GROQ_MODEL` and
`AI_GEMINI_MODEL` to the model names selected for deployment. `AI_GROQ_BASE_URL` may keep its
existing default unless Groq-compatible routing changes.

For JVM development, `AI_ENABLED=false` remains the default and no provider credentials are
required. Docker Compose maps `AI_ENABLED` to both the native build topology and runtime value;
when changing it, rebuild the backend (`docker compose up -d --build`) before testing the new
mode. A native image compiled with one topology must not be treated as runtime-switchable to the
other topology.
```

Also add these exact variable names to the existing Render `Production Configuration` list, with prose saying that secrets/models are configured in Render rather than committed:

```text
AI_ENABLED
AI_GROQ_API_KEY
AI_GROQ_MODEL
AI_GEMINI_API_KEY
AI_GEMINI_MODEL
```

Do not document `AI_NATIVE_BUILD_ENABLED` as a Render runtime variable; it is a Docker build contract, not application configuration.

- [ ] **Step 3: Document the automated gate in `docs/BUILD_AND_VERIFY.md`**

Add a subsection near the existing backend/native verification guidance:

```markdown
## Native AI topology verification

The GitHub Actions native job does more than compile the GraalVM image. It builds the production
Dockerfile with the AI-enabled AOT topology, loads the resulting image, starts it against a
disposable PostgreSQL 17 container using fake runtime provider values, and temporarily exposes
Actuator `beans` for that verification container only.

The gate requires `groqChatModel`, `geminiChatModel`, and `enabledAiChatGateway` to exist and
requires `disabledAiChatGateway` to be absent. It also verifies that provider key/model
environment values are not present in the final image configuration. The check performs no real
Groq/Gemini request and requires no provider secret.

Normal JVM verification continues to use the default AI-disabled mode. For Docker Compose,
changing `AI_ENABLED` requires rebuilding the backend because the native bean topology is selected
during AOT compilation.
```

Do not alter the existing Phase 1 acceptance record.

- [ ] **Step 4: Record the AOT topology constraint in `docs/AI Chess Rivals - Tech Stack.md`**

Add a short architecture note in the Spring AI / GraalVM area stating all of the following:

```markdown
### Native AI topology

The production GraalVM artifact treats AI enablement as a build-time Spring AOT choice. Direct
production Docker builds compile the Groq/Gemini + enabled `AiChatGateway` topology using
non-secret placeholders only for AOT processing; real provider keys and model names are supplied
at runtime. Docker Compose maps its existing `AI_ENABLED` value into the native build argument so
local AI-disabled images remain straightforward. CI starts the actual native image and verifies
the application-owned provider/gateway beans through a CI-only Actuator `beans` exposure.
```

Do not change the documented provider order or Spring AI version.

- [ ] **Step 5: Check documentation consistency**

Search tracked files for the AI switch:

```bash
git grep -n "AI_ENABLED\|AI_NATIVE_BUILD_ENABLED\|app.ai.enabled"
```

Confirm there is no statement claiming that an already-built native image can freely switch its AI bean topology at runtime. JVM/local runtime toggling may still be documented as runtime behavior.

- [ ] **Step 6: Commit documentation**

```bash
git add server/.env.example server/README.md docs/BUILD_AND_VERIFY.md "docs/AI Chess Rivals - Tech Stack.md"
git commit -m "docs: explain native AI build contract"
```

---

### Task 4: Run Full Verification and Close the Acceptance Loop

**Files:**
- Verify only; modify files only to fix failures caused by Tasks 1-3.

**Interfaces:**
- Consumes: completed native build contract, CI artifact probe, documentation.
- Produces: branch ready for PR with issue #55 acceptance criteria demonstrably covered.

- [ ] **Step 1: Run focused JVM AI configuration tests**

From the repository root:

```bash
./server/mvnw -f server/pom.xml -Dtest=AiConfigurationContextTest,AiProviderConfigurationTest,FailoverAiChatGatewayTest test
```

Windows equivalent:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=AiConfigurationContextTest,AiProviderConfigurationTest,FailoverAiChatGatewayTest test
```

Expected: PASS. These tests protect the unchanged local disabled mode, enabled provider creation, and Groq -> Gemini -> deterministic fallback behavior.

- [ ] **Step 2: Run the repository verification gate**

POSIX:

```bash
./scripts/verify.sh
```

PowerShell:

```powershell
.\scripts\verify.ps1
```

Expected: backend verification passes; frontend verification remains unchanged and passes.

- [ ] **Step 3: Rebuild/check the production native image if Task 1's build was not run after final edits**

```bash
docker build \
  --build-arg AI_NATIVE_BUILD_ENABLED=true \
  -t ai-chess-rivals:native-ai-local \
  ./server
```

Expected: PASS with no real provider credentials supplied.

- [ ] **Step 4: Push the implementation branch and require the GitHub `Native image verification` job to pass**

The native job must now prove all of these on the actual image:

- health endpoint reaches `UP`;
- `groqChatModel` exists;
- `geminiChatModel` exists;
- `enabledAiChatGateway` exists;
- `disabledAiChatGateway` is absent;
- provider API key/model variables are absent from final image `ENV` metadata;
- no real provider secret or provider call was used.

If the native job fails, inspect the failing app container logs from the verification step before changing code. Fix only the root cause demonstrated by those logs.

- [ ] **Step 5: Map the final diff to issue #55 acceptance criteria**

Before opening/updating the PR, verify this mapping:

1. **Production native build includes Groq/Gemini + enabled gateway topology** -> `server/Dockerfile` build-time flag + CI `/actuator/beans` assertions.
2. **Runtime `AI_ENABLED=true` does not depend on definitions excluded during AOT** -> CI runs the built native image with `AI_ENABLED=true` and asserts enabled bean names.
3. **Real keys not required/baked during build** -> Dockerfile uses fixed fake placeholders scoped to the build command; CI passes no secrets/build credentials and checks final image env metadata.
4. **Runtime still reads provider credentials/models from environment** -> verification container supplies fake runtime values through the existing environment names; production docs list the same variables.
5. **JVM/local AI-disabled mode remains straightforward** -> `application.yaml` unchanged, existing context test passes, Compose defaults build/runtime to false.
6. **Repeatable automated topology check exists** -> GitHub native job starts and inspects the real artifact.
7. **Groq -> Gemini -> deterministic fallback unchanged** -> no Java provider/gateway changes; existing failover tests pass.
8. **No new provider framework** -> no new dependency or Java abstraction.
9. **Docs/environment examples updated** -> Task 3 files.

- [ ] **Step 6: Review the diff for forbidden scope**

Run:

```bash
git diff --name-only master...HEAD
```

Expected changed implementation files are limited to:

```text
.github/workflows/ci.yml
server/Dockerfile
server/docker-compose.yml
server/.env.example
server/README.md
docs/BUILD_AND_VERIFY.md
docs/AI Chess Rivals - Tech Stack.md
```

The implementation-plan file may also appear if the plan was created on the feature branch rather than already existing on `master`.

There must be no changes under `server/src/main/java/**`, `server/src/test/java/**`, `client/**`, or database migrations for the primary solution.

- [ ] **Step 7: Commit any verification-only corrections, if needed**

Only if Tasks 1-3 required a correction after full verification, stage the same bounded issue files rather than using a wildcard:

```bash
git add .github/workflows/ci.yml \
        server/Dockerfile \
        server/docker-compose.yml \
        server/.env.example \
        server/README.md \
        docs/BUILD_AND_VERIFY.md \
        "docs/AI Chess Rivals - Tech Stack.md"
git commit -m "fix: complete native AI topology verification"
```

If `git status --short` shows none of those files modified, do not create an empty commit.

- [ ] **Step 8: Open/update the PR with issue linkage and verification evidence**

PR summary should state:

```markdown
Closes #55

- selects the AI-enabled Spring AOT topology for production native Docker builds without build-time secrets;
- preserves AI-disabled JVM/local behavior and aligns Docker Compose build/runtime mode;
- runs the actual native image in CI and asserts Groq/Gemini + enabled gateway bean presence;
- documents that native bean topology is a build-time contract while provider credentials remain runtime configuration.
```

In the verification section, include the exact root verifier result and the successful GitHub `Native image verification` job. Do not claim a real Groq/Gemini call was tested.

---

## Non-Goals / Do Not Expand

Do not use this issue to add:

- dialogue persistence or WebSocket integration;
- new providers;
- runtime provider selection;
- observability metrics beyond the topology check;
- token accounting;
- retry/failover changes;
- agent/tool workflows;
- a general native configuration framework;
- a second Dockerfile;
- a Spring profile solely for native AI;
- test-only production endpoints.

Those concerns either already exist elsewhere or belong to later Phase 2 work such as #46.

## Expected Final Shape

The intended implementation is deliberately small:

1. **Dockerfile:** production native AOT defaults to AI-enabled topology using fake build-only placeholder values.
2. **Docker Compose:** local build topology follows the existing `AI_ENABLED` setting and remains off by default.
3. **Runtime:** real Groq/Gemini keys/models still arrive only through application environment variables.
4. **CI:** the loaded production native image is started with fake runtime provider values and its application-owned AI beans are inspected through a CI-only Actuator exposure.
5. **Java AI layer:** unchanged.

If this shape works, do not replace it with a more flexible architecture. The project needs a reliable Phase 2 showcase artifact, not runtime-pluggable native bean topology.