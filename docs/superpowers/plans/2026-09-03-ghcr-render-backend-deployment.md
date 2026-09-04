# GHCR and Render Backend Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the already-verified production backend image from GitHub Actions to GHCR with `latest` and immutable commit-SHA tags, then deploy that exact prebuilt image to an image-backed Render web service without Render compiling GraalVM Native Image.

**Architecture:** Reuse the existing `CI / Native image verification` job instead of introducing a second GraalVM build workflow. That job already builds `server/Dockerfile` for `linux/amd64`, loads the image as `ai-chess-rivals:native-ci`, boots it against disposable PostgreSQL, verifies the AI-enabled native topology, and proves provider configuration is not baked into the final image. On successful `push` runs for `master`, authenticate to GHCR with the repository `GITHUB_TOKEN`, retag and push that same verified local image as `ghcr.io/krishna916/ai-chess-rivals-server:latest` and `ghcr.io/krishna916/ai-chess-rivals-server:<full-commit-sha>`, then invoke a secret Render deploy hook with `imgURL` set to the immutable SHA tag. Pull-request CI remains verification-only.

**Tech Stack:** GitHub Actions, Docker Buildx/BuildKit GitHub Actions cache, `docker/login-action`, GitHub Container Registry (GHCR), GraalVM Native Image via `server/Dockerfile`, Render image-backed web services and deploy hooks, PostgreSQL/Neon runtime configuration.

**Spec:** GitHub issue `#21 Build and deploy backend image through GitHub Actions` is the approved requirements/design. The repository has evolved since that issue was written: issue `#49` introduced the current native-image verification pipeline in `.github/workflows/ci.yml`. This plan therefore preserves #21's functional requirements while avoiding a second duplicate native compilation.

## Source of Truth

- Issue: `#21 Build and deploy backend image through GitHub Actions`
- Existing CI/native implementation: `.github/workflows/ci.yml`
- Existing native CI plan/history: `docs/superpowers/plans/2026-08-08-github-actions-ci-native-verification.md`
- Production image definition: `server/Dockerfile`
- Runtime configuration contract: `server/src/main/resources/application.yaml`
- Deployment/environment documentation: `server/README.md`
- Verification documentation: `docs/BUILD_AND_VERIFY.md`
- Environment-variable inventory: `server/.env.example`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md` if present
- GitHub Container Registry guidance: `https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry`
- GitHub package visibility guidance: `https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility`
- Render prebuilt-image guidance: `https://render.com/docs/deploying-an-image`
- Render deploy-hook guidance: `https://render.com/docs/deploy-hooks`
- Render health-check guidance: `https://render.com/docs/health-checks`

## Selected Design

Do **not** add a second backend image workflow that recompiles the GraalVM native image after the existing CI job already compiled and boot-verified it.

Instead, extend the existing native CI job with post-verification publication/deployment steps:

```text
PR changing server/** or ci.yml
        ↓
Backend verification
        ↓
Native image build + runtime topology verification
        ↓
STOP (no registry login, no push, no Render deploy)

Push/merge to master changing server/** or ci.yml
        ↓
Backend verification
        ↓
Native image build + runtime topology verification
        ↓
Retag the already-loaded verified image
        ↓
Push full SHA tag + latest to GHCR
        ↓
Trigger Render deploy hook with the full SHA image tag
```

This is deliberately simpler than the original July-era issue proposal because the repository now already owns the expensive production-native build in CI. A separate deployment build would consume more GitHub runner time, risk concurrent duplicate native compilation, and create two production-image definitions for no viewer or learning benefit.

## Global Constraints

- Keep one production-native Docker build per relevant CI run. Do not add a second GraalVM compilation merely for publication.
- Preserve the existing `CI` workflow name and existing check/job names so current PR behavior and any future branch protection remain stable.
- Preserve existing change detection: native work is relevant for `server/**` and `.github/workflows/ci.yml`; frontend-only changes must leave `Native image verification` skipped.
- Preserve the existing dependency chain: native work runs only after successful backend verification.
- Preserve the existing `linux/amd64` platform target required by Render.
- Preserve BuildKit GitHub Actions caching with `cache-from: type=gha,scope=server-native` and `cache-to: type=gha,mode=max,scope=server-native`.
- Do not modify `server/Dockerfile`, Maven configuration, application source, AI behavior, chess behavior, database schema, frontend source, or frontend deployment workflow for this issue.
- Publish exactly `ghcr.io/krishna916/ai-chess-rivals-server:latest` and `ghcr.io/krishna916/ai-chess-rivals-server:${GITHUB_SHA}` where `${GITHUB_SHA}` is the full immutable commit SHA.
- Publish only after the native runtime/topology verification succeeds.
- Never push images or call Render from pull-request events.
- Use the repository-scoped `GITHUB_TOKEN`; do not create a PAT for GitHub Actions image publication.
- Give `packages: write` only to the native job; keep `contents: read`.
- Use `docker/login-action@v4`; do not add `docker/metadata-action` for only two deterministic tags.
- Keep the GHCR package public after the first successful publication so Render can pull it anonymously. Do not maintain Render registry credentials unless public visibility proves impossible for a concrete reason.
- Store the Render hook only as repository secret `RENDER_DEPLOY_HOOK_URL`. Never write the hook URL to YAML, docs, issue comments, logs, or committed environment files.
- Deploy the immutable SHA tag through Render's `imgURL` deploy-hook parameter even though the service's configured/default image is `:latest`. This ensures the deploy corresponds exactly to the commit that passed CI.
- During first-time bootstrap, the first `master` run is expected to publish the image and then fail clearly at the Render step if `RENDER_DEPLOY_HOOK_URL` does not exist yet. Do not silently skip a missing hook after publication; the temporary failure makes incomplete deployment configuration visible.
- Keep the current Git-backed Render service active until the image-backed service passes runtime acceptance and production traffic has been switched successfully.
- Production remains one backend instance because active-match state, cooldown state, and accepted-start counters are in memory.
- Copy runtime values from the existing Render service rather than inventing new production settings. Required names to check include datasource/Flyway credentials, `OWNER_CONTROL_TOKEN`, match limits, `APP_WEBSOCKET_ALLOWED_ORIGIN`, AI/OpenRouter settings, and any explicit `SERVER_PORT` already used by the working service.
- Do not set Render's HTTP health-check path to `/actuator/health`: the application binds Actuator to separate management port `8081`, while Render HTTP health checks target the public web-service port. Use Render's default TCP health check for the image-backed service unless the application later exposes a health endpoint on the public port.
- Do not remove/suspend the old service until both REST and WebSocket behavior are observed on the new service and the production frontend points to it through its existing configuration mechanism.
- Historical plans/specs remain historical records; do not rewrite them to describe the new deployment flow.

## File Map

### Modify

- `.github/workflows/ci.yml` — after the existing native-image runtime verification, grant scoped package-write permission, log into GHCR only for `master` pushes, push the already-verified local image under immutable SHA and `latest` tags, then call the Render deploy hook for the immutable SHA.
- `docs/BUILD_AND_VERIFY.md` — replace the now-stale statement that native CI never publishes/deploys; document PR-vs-master behavior, GHCR tags, Render secret, and deployment ordering.
- `server/README.md` — update production deployment guidance from Render-built Docker to GitHub-built GHCR image; document the image-backed Render service, public-package bootstrap, deploy hook, environment migration, health-check choice, verification, and cutover.

### Do Not Modify

- `server/Dockerfile`
- `server/pom.xml`
- `server/src/**`
- `server/docker-compose.yml`
- `server/.env.example`
- `client/**`
- database migrations
- AI provider configuration
- old dated Superpowers plans/specs

---

### Task 1: Extend the Existing Native CI Job to Publish the Verified Image and Trigger Render

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: existing `native` job image tag `ai-chess-rivals:native-ci`, existing successful native topology verification, `github.event_name`, `github.ref`, `github.actor`, `github.sha`, `secrets.GITHUB_TOKEN`, and repository secret `RENDER_DEPLOY_HOOK_URL`.
- Produces: `ghcr.io/krishna916/ai-chess-rivals-server:${GITHUB_SHA}`, `ghcr.io/krishna916/ai-chess-rivals-server:latest`, and a Render deploy-hook request whose `imgURL` selects the immutable SHA tag.
- Side-effect boundary: registry publication and Render deployment happen only for `push` to `refs/heads/master`.

- [ ] **Step 1: Re-read the current native job and confirm the loaded image contract**

Run from repository root:

```bash
git grep -n -E 'name: Native image verification|tags: ai-chess-rivals:native-ci|push: false|load: true|cache-from: type=gha,scope=server-native|cache-to: type=gha,mode=max,scope=server-native' -- .github/workflows/ci.yml
```

Expected before editing:

```text
Native image verification
push: false
load: true
tags: ai-chess-rivals:native-ci
cache-from: type=gha,scope=server-native
cache-to: type=gha,mode=max,scope=server-native
```

If the native job no longer loads `ai-chess-rivals:native-ci`, stop this task and adapt the publication steps to the actual verified local tag rather than adding another build.

- [ ] **Step 2: Give only the native job GHCR write permission and define one image name**

Inside the existing `native:` job, add these job-level fields immediately after `runs-on: ubuntu-latest`:

```yaml
    permissions:
      contents: read
      packages: write
    env:
      IMAGE_NAME: ghcr.io/krishna916/ai-chess-rivals-server
```

Do not widen the top-level workflow permissions.

- [ ] **Step 3: Keep the current build and runtime verification unchanged**

Do not alter the existing `Build production native image`, `Verify AI-enabled topology in native image`, or `Clean up native topology verification` logic except for formatting required by the YAML edit.

The publication steps must come **after** the existing cleanup step. Because normal GitHub step execution is implicitly `success()`, publication will not run when build/runtime verification fails even though cleanup uses `if: ${{ always() }}`.

- [ ] **Step 4: Add GHCR login for successful master pushes only**

After `Clean up native topology verification`, add:

```yaml
      - name: Log in to GitHub Container Registry
        if: ${{ github.event_name == 'push' && github.ref == 'refs/heads/master' }}
        uses: docker/login-action@v4
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
```

Expected behavior:

- PR: step is skipped.
- `master` push with relevant native changes: step authenticates using `GITHUB_TOKEN`.
- No PAT or additional GitHub secret is required.

- [ ] **Step 5: Retag and push the exact local image that already passed native verification**

Immediately after GHCR login, add:

```yaml
      - name: Publish verified backend image
        if: ${{ github.event_name == 'push' && github.ref == 'refs/heads/master' }}
        shell: bash
        run: |
          set -euo pipefail

          sha_image="${IMAGE_NAME}:${GITHUB_SHA}"
          latest_image="${IMAGE_NAME}:latest"

          docker image inspect ai-chess-rivals:native-ci >/dev/null
          docker tag ai-chess-rivals:native-ci "$sha_image"
          docker tag ai-chess-rivals:native-ci "$latest_image"
          docker push "$sha_image"
          docker push "$latest_image"
```

Do **not** replace this with a second `docker/build-push-action` invocation. The point is to publish the already-verified image, not rebuild it.

- [ ] **Step 6: Add the Render deploy hook and make missing bootstrap configuration fail explicitly**

Immediately after image publication, add:

```yaml
      - name: Trigger Render image deployment
        if: ${{ github.event_name == 'push' && github.ref == 'refs/heads/master' }}
        shell: bash
        env:
          RENDER_DEPLOY_HOOK_URL: ${{ secrets.RENDER_DEPLOY_HOOK_URL }}
        run: |
          set -euo pipefail

          if [[ -z "$RENDER_DEPLOY_HOOK_URL" ]]; then
            echo "RENDER_DEPLOY_HOOK_URL is not configured. The GHCR image was published, but Render deployment cannot start."
            exit 1
          fi

          curl --fail-with-body --silent --show-error --get \
            --data-urlencode "imgURL=${IMAGE_NAME}:${GITHUB_SHA}" \
            "$RENDER_DEPLOY_HOOK_URL"
```

The deploy hook URL is already secret. Do not echo it or enable shell tracing.

Render accepts `GET`/`POST` deploy-hook requests and allows an image-backed service to override only the tag/digest via `imgURL`. Because the configured service image will be `ghcr.io/krishna916/ai-chess-rivals-server:latest`, the SHA-tag override matches the same registry/namespace/image and is valid.

- [ ] **Step 7: Verify the workflow diff preserves PR behavior and avoids a second build**

Run:

```bash
git diff --check
git diff -- .github/workflows/ci.yml
git grep -n -E 'docker/build-push-action|docker/login-action|Publish verified backend image|Trigger Render image deployment|packages: write|IMAGE_NAME' -- .github/workflows/ci.yml
```

Expected:

- Exactly one `docker/build-push-action` native build remains.
- Existing `push: false`, `load: true`, `linux/amd64`, and GHA cache settings remain.
- One `docker/login-action@v4` step exists.
- Publication and Render steps are both guarded by `push` + `refs/heads/master`.
- `packages: write` exists only on the native job.

- [ ] **Step 8: Commit the workflow change**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: publish verified backend image"
```

---

### Task 2: Update Active Deployment and Verification Documentation

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`
- Modify: `server/README.md`

**Interfaces:**
- Consumes: final workflow behavior from Task 1 and current runtime environment contract from `application.yaml` / `.env.example`.
- Produces: active documentation that distinguishes PR verification from master publication/deployment and gives the one-time GHCR/Render bootstrap/cutover procedure.

- [ ] **Step 1: Replace the stale native-CI publication statement in `BUILD_AND_VERIFY.md`**

Find the current paragraph:

```text
The native CI job is verification only. It does not publish an image, log into GHCR, or deploy to
Render; image publication and deployment remain separate work.
```

Replace it with content that states all of the following explicitly:

```text
Pull requests remain verification-only: the native job builds and boots the production image but does not authenticate to a registry, publish an image, or call Render.

For relevant pushes to master, the same native job first completes the existing build/runtime verification, then publishes that exact loaded image to:
- ghcr.io/krishna916/ai-chess-rivals-server:<full-commit-sha>
- ghcr.io/krishna916/ai-chess-rivals-server:latest

After publication, the job calls the Render deploy hook stored in repository secret RENDER_DEPLOY_HOOK_URL and requests deployment of the immutable commit-SHA tag. A missing hook is a deployment configuration error and intentionally fails the master native job after the image has been published.
```

Keep the existing local verification commands and native-topology explanation unchanged.

- [ ] **Step 2: Update `server/README.md` production deployment description**

In `## Production Deployment Differences`, replace the statement that Render builds the backend image itself with the current production flow:

```text
GitHub Actions builds and verifies server/Dockerfile for linux/amd64, publishes the verified image to GHCR, and Render runs an image-backed web service that pulls the prebuilt image. Render does not compile the GraalVM native image.
```

Document the canonical image:

```text
ghcr.io/krishna916/ai-chess-rivals-server:latest
```

Document that automated deploys use the commit-SHA tag through the deploy hook even though `latest` remains the configured image/reference for manual deploys.

- [ ] **Step 3: Add a concise `Production image deployment` subsection to `server/README.md`**

Document this exact operational sequence:

```text
1. Relevant server/CI changes reach master.
2. CI verifies the backend and native runtime.
3. CI pushes full-SHA and latest tags to GHCR.
4. CI invokes RENDER_DEPLOY_HOOK_URL with imgURL pointing to the full-SHA tag.
5. Render pulls the prebuilt image and starts it with runtime secrets/configuration supplied in Render.
```

Also document:

- GHCR package is intentionally public so Render needs no registry credential.
- The Render hook is stored only as `RENDER_DEPLOY_HOOK_URL` in GitHub Actions secrets.
- The image never contains the OpenRouter key/model runtime values; those stay in Render.
- Keep a single backend instance because match/cooldown/start-limit state is in memory.
- Leave Render HTTP health-check path unset and use default TCP health checking; `/actuator/health` is on separate management port `8081`, not the public application port.
- Keep the old Git-backed service until the image-backed service has passed acceptance and traffic has been moved.

- [ ] **Step 4: Document the runtime settings that must be copied to the image-backed Render service**

In the existing production-configuration section, preserve the current variable list and add `APP_WEBSOCKET_ALLOWED_ORIGIN` if it is not already documented there.

The new service must copy the existing service's values for at least:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_FLYWAY_URL
SPRING_FLYWAY_USER
SPRING_FLYWAY_PASSWORD
SPRING_JPA_HIBERNATE_DDL_AUTO
OWNER_CONTROL_TOKEN
MATCH_COOLDOWN
MATCH_DAILY_START_LIMIT
APP_WEBSOCKET_ALLOWED_ORIGIN
AI_ENABLED
AI_OPENROUTER_API_KEY
AI_OPENROUTER_BASE_URL
AI_OPENROUTER_PRIMARY_MODEL
AI_OPENROUTER_FALLBACK_MODEL
AI_OPENROUTER_PRIMARY_TIMEOUT
AI_OPENROUTER_FALLBACK_TIMEOUT
```

If the current Render service explicitly sets `SERVER_PORT`, Stockfish tuning, or game pacing variables, copy those values unchanged as well. Do not create new values merely because `.env.example` lists optional knobs.

- [ ] **Step 5: Check active docs for contradictions**

Run:

```bash
git grep -n -E 'verification only|does not publish|built using the same `Dockerfile`|builds the backend Docker image|Git-backed' -- docs/BUILD_AND_VERIFY.md server/README.md
```

Expected after edits: no active statement claims the production Render service compiles the native image or that native CI never publishes/deploys.

Do not modify dated historical files under `docs/superpowers/` when this grep finds old decisions there.

- [ ] **Step 6: Verify documentation formatting and commit**

Run:

```bash
git diff --check
git diff -- docs/BUILD_AND_VERIFY.md server/README.md
```

Then commit:

```bash
git add docs/BUILD_AND_VERIFY.md server/README.md
git commit -m "docs: document GHCR Render deployment"
```

---

### Task 3: Run Repository Verification and Validate Pull-Request CI Has No Deployment Side Effects

**Files:**
- Verify only; no planned source changes.

**Interfaces:**
- Consumes: Tasks 1-2.
- Produces: evidence that repository checks remain green and the PR native job still builds/boots without publishing or deploying.

- [ ] **Step 1: Run the canonical repository verifier**

Use the command for the current shell from repository root.

Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

POSIX:

```bash
./scripts/verify.sh
```

Expected: backend and frontend verification pass. This issue does not require application-test changes.

- [ ] **Step 2: Inspect the complete branch diff**

Run:

```bash
git status --short
git diff --check origin/master...HEAD
git diff --stat origin/master...HEAD
git diff origin/master...HEAD -- .github/workflows/ci.yml docs/BUILD_AND_VERIFY.md server/README.md
```

Expected changed implementation/documentation files:

```text
.github/workflows/ci.yml
docs/BUILD_AND_VERIFY.md
server/README.md
```

The plan file itself is historical execution guidance and may also appear in the branch.

- [ ] **Step 3: Push the branch and open the PR for issue #21**

```bash
git push -u origin feature/issue-21-ghcr-render-deployment
```

Open a PR targeting `master` and reference `Closes #21` only after the external Render cutover work is included/verified in the issue checklist. If the PR must merge before Render can be bootstrapped, use `Refs #21` initially and close the issue only after Task 5.

- [ ] **Step 4: Verify PR CI behavior**

Because `.github/workflows/ci.yml` itself changed, expect all existing CI jobs to run on the PR.

Confirm:

```text
CI / Detect changes                         PASS
CI / Backend verification                  PASS
CI / Frontend verification                 PASS
CI / Native image verification             PASS
```

In the native job logs confirm:

- production native image builds once;
- native AI topology verification passes;
- baked provider-environment check passes;
- `Log in to GitHub Container Registry` is skipped;
- `Publish verified backend image` is skipped;
- `Trigger Render image deployment` is skipped.

If the native build/runtime verification fails, use `superpowers:systematic-debugging`; do not weaken or bypass the verification to make deployment proceed.

---

### Task 4: Bootstrap the GHCR Package and Create the Image-Backed Render Service

**Files:**
- No repository file changes required.
- External configuration: GitHub Packages, Render Dashboard, GitHub Actions repository secrets.

**Interfaces:**
- Consumes: merged Task 1 workflow and the first successful GHCR publication from `master`.
- Produces: public GHCR package, running image-backed Render service, and GitHub secret `RENDER_DEPLOY_HOOK_URL`.

- [ ] **Step 1: Merge the PR and inspect the first relevant `master` native job**

Expected bootstrap sequence:

```text
Backend verification                  PASS
Native image build/runtime verify     PASS
GHCR login                            PASS
Publish verified backend image        PASS
Trigger Render image deployment       FAIL (expected only because hook is not configured yet)
```

This temporary failure is intentional. Do not remove the failure guard. The image must already exist in GHCR before an image-backed Render service can be created and verified.

Record the merge commit full SHA from the workflow run; that SHA must exist as a GHCR image tag.

- [ ] **Step 2: Make the newly-created GHCR package public**

Open the package created under the `krishna916` account for `ai-chess-rivals-server`.

In **Package settings -> Danger Zone -> Change visibility**, choose **Public** and confirm the package name.

GitHub Container Registry packages are private on first publication; public GHCR packages can be pulled anonymously. Once public, GitHub does not allow changing that package back to private, which is acceptable for this public showcase repository and is explicitly chosen to avoid Render registry credentials.

- [ ] **Step 3: Prove the package has both required tags and is anonymously pullable**

From a shell that is not relying on existing GHCR credentials:

```bash
docker logout ghcr.io 2>/dev/null || true
docker pull ghcr.io/krishna916/ai-chess-rivals-server:latest
docker pull ghcr.io/krishna916/ai-chess-rivals-server:<MERGE_COMMIT_FULL_SHA>
```

Replace `<MERGE_COMMIT_FULL_SHA>` with the full SHA recorded in Step 1.

Expected: both pulls succeed without authenticating to GHCR.

- [ ] **Step 4: Create a new Render image-backed web service**

In Render Dashboard:

1. Choose **New -> Web Service**.
2. Choose **Existing Image**.
3. Use image URL:

```text
ghcr.io/krishna916/ai-chess-rivals-server:latest
```

4. Do not add registry credentials; the package is public.
5. Use a temporary distinct service name such as `ai-chess-rivals-server-image` so the old Git-backed service remains available during validation.
6. Use the same region, plan/instance size, and instance count as the current backend. Keep instance count at `1`.
7. Copy every production environment variable from the current service. Do not rotate or retype secrets unnecessarily; use Render's existing values as the source of truth.
8. Leave HTTP Health Check Path unset so Render uses its default TCP check. Do not enter `/actuator/health` because Actuator listens on the separate management port `8081`.
9. Preserve any explicit `SERVER_PORT` value from the current working service. If the old service does not set it, do not add one only for this migration.
10. Create/deploy the service.

Expected: Render pulls the prebuilt image. The deploy logs must not contain Maven/GraalVM compilation because Render is no longer building the Dockerfile.

- [ ] **Step 5: Verify the new service before configuring automation**

Using the new Render service URL, verify the public REST endpoint:

```text
GET /api/v1/match -> HTTP 200
```

Also verify in Render:

- deploy event succeeds;
- service remains healthy on the default TCP check;
- application starts with the expected Neon/Flyway configuration;
- Stockfish starts from the bundled `stockfish/stockfish` path;
- AI topology/runtime configuration starts without missing OpenRouter settings;
- only one backend instance is running.

Do not stop the old service yet.

- [ ] **Step 6: Copy the new service's deploy hook into GitHub Actions secrets**

In the new Render service, open **Settings** and copy the service deploy hook URL.

In GitHub repository **Settings -> Secrets and variables -> Actions**, create repository secret exactly:

```text
RENDER_DEPLOY_HOOK_URL
```

Paste the Render hook URL as the secret value.

Do not save this URL in local `.env`, docs, issue comments, or terminal history.

---

### Task 5: Prove Automated Immutable Deployment, Cut Traffic Over, Then Retire the Old Service

**Files:**
- No planned repository source changes.
- External configuration may update the existing frontend deployment's `VITE_API_URL` value; do not change frontend code or its deployment workflow.

**Interfaces:**
- Consumes: public GHCR package, image-backed Render service, and configured `RENDER_DEPLOY_HOOK_URL`.
- Produces: proven automatic master -> GHCR -> Render deployment and safe retirement of the old Git-backed service.

- [ ] **Step 1: Re-run the first post-merge native job after the Render hook secret exists**

Re-run the `Native image verification` job from the relevant `master` workflow run.

The cache should make the repeated native build substantially cheaper than a cold build, but the job must still execute its normal verification.

Expected final steps:

```text
Log in to GitHub Container Registry     PASS
Publish verified backend image          PASS
Trigger Render image deployment         PASS
```

- [ ] **Step 2: Verify Render deployed the immutable SHA requested by GitHub Actions**

In Render Events/Deploy details, confirm the deploy was triggered by the hook and references the same image repository plus the merge commit SHA tag used by the GitHub Actions run.

Confirm Render performed an image pull/start only; there must be no GraalVM/Maven build phase.

- [ ] **Step 3: Re-run backend smoke checks against the image-backed service**

Verify:

```text
GET /api/v1/match -> HTTP 200
WebSocket /ws/match connects successfully
```

If a match is currently running, verify state hydration/updates continue. Do not start a production match solely for deployment testing if doing so would disturb the showcase; REST + WebSocket connectivity and normal service logs are sufficient for deployment acceptance.

- [ ] **Step 4: Cut the production frontend to the new backend using existing configuration only**

The client reads its API endpoint from:

```text
VITE_API_URL
```

If production currently points directly at the old Render service URL, change the existing frontend deployment environment value to the new image-backed service URL ending in `/api/v1`, then redeploy the frontend using its existing deployment process.

Do not modify `client/src/services/matchApi.ts`, `client/src/services/matchSocket.ts`, or create a new frontend workflow. `matchSocket.ts` derives `/ws/match` from the same HTTP base URL, so one backend-origin switch is sufficient.

- [ ] **Step 5: Verify the production viewer after cutover**

Confirm through the deployed frontend:

- initial `GET /api/v1/match` succeeds against the new backend;
- `/ws/match` connects to the new backend;
- current board/activity hydrates normally;
- no CORS/WebSocket-origin error appears;
- `APP_WEBSOCKET_ALLOWED_ORIGIN` on the new Render service matches the production frontend origin;
- owner controls still use the existing `OWNER_CONTROL_TOKEN` and are not exposed publicly.

- [ ] **Step 6: Suspend the old Git-backed Render service before deleting it**

First suspend the old service rather than deleting it immediately.

Observe the production frontend against the new image-backed service. If the viewer remains healthy, leave the old service suspended for a short rollback window appropriate for this hobby project.

If a deployment defect appears, resume the old service and point the existing frontend configuration back to it; do not redesign the deployment architecture during rollback.

- [ ] **Step 7: Remove the old service only after acceptance is complete**

After the new service has passed the checks above and no rollback is needed, delete or permanently retire the old Git-backed Render service.

This is the point where issue #21's old-service acceptance criterion is complete.

- [ ] **Step 8: Record final acceptance in issue #21 / PR**

Record only non-secret evidence:

```text
- master workflow run URL/ID
- full commit SHA whose image was deployed
- confirmation that both GHCR tags exist
- confirmation that the GHCR package is public
- confirmation that Render deploy used the prebuilt image and did not run GraalVM compilation
- confirmation that REST + WebSocket smoke checks passed
- confirmation that production frontend points to the image-backed service
- confirmation that old Git-backed service was suspended/retired
```

Never paste the Render deploy hook, OpenRouter API key, database credentials, owner-control token, or other secret values.

---

## Final Verification Checklist

- [ ] `CI / Backend verification` still gates native work.
- [ ] `CI / Native image verification` still builds exactly one `linux/amd64` image with GHA BuildKit cache.
- [ ] Existing native runtime topology verification still passes before publication.
- [ ] PR runs do not authenticate to GHCR, publish images, or call Render.
- [ ] Frontend-only changes leave native publication/deployment skipped.
- [ ] Relevant `master` pushes publish `latest` and full-SHA tags.
- [ ] GHCR publication uses `GITHUB_TOKEN` and native-job-only `packages: write` permission.
- [ ] GHCR package is public and anonymously pullable.
- [ ] Render is image-backed and configured with `ghcr.io/krishna916/ai-chess-rivals-server:latest`.
- [ ] GitHub secret `RENDER_DEPLOY_HOOK_URL` exists and is never committed/logged.
- [ ] Automated Render deploy requests the immutable full-SHA tag through `imgURL`.
- [ ] Render pulls the image and does not compile GraalVM Native Image.
- [ ] New service preserves Neon, Flyway, owner-control, match, WebSocket-origin, and OpenRouter runtime configuration.
- [ ] New Render service uses one instance.
- [ ] Render uses default TCP health checking; `/actuator/health` is not incorrectly configured on the public port.
- [ ] `GET /api/v1/match` and `/ws/match` work on the new service.
- [ ] Production frontend uses the new backend through existing `VITE_API_URL` configuration, without frontend code/workflow changes.
- [ ] Old Git-backed Render service remains available until cutover verification, then is suspended/retired.
- [ ] Active documentation no longer says Render builds the production native image or that native CI never publishes/deploys.

## Cost / Complexity Assessment

**Cost Impact:** Low / reduced. Native compilation moves entirely to GitHub Actions for production deploys, and this plan deliberately reuses the image already built for native verification instead of compiling twice on the same run.

**Complexity Impact:** Low. One existing workflow gains three post-verification steps; no new service abstraction, deployment framework, registry credential, reusable workflow, or infrastructure-as-code layer is introduced.

**Cheaper/Simpler Alternative Rejected:** A separate `deploy-backend.yml` that rebuilds the native image would look cleaner on paper but duplicates the most expensive build step and can race the existing native CI. Uploading/downloading a Docker image artifact between workflows would avoid recompilation but adds large artifact transfer/storage and more moving pieces. Reusing the already-loaded verified image is the 80/20 solution.

**MVP Recommendation:** **Must Have for deployment-cost cleanup, but keep it exactly this small.** Do not expand #21 into Render Blueprints, release automation, Kubernetes, multi-environment promotion, signed images, SBOM pipelines, vulnerability scanners, or generic CD infrastructure unless a separate future issue demonstrates concrete value.
