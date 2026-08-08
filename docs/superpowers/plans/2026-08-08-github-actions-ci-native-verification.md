# GitHub Actions CI and Native Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lightweight GitHub Actions CI that verifies backend and frontend changes automatically and conditionally proves the production GraalVM native Docker image builds for `linux/amd64` before backend changes are merged.

**Architecture:** Add one readable `.github/workflows/ci.yml` workflow triggered by pull requests targeting `master` and pushes to `master`. A tiny first job computes whether backend, frontend, or native-build-relevant files changed; backend/frontend verification jobs run only when relevant, and the expensive native Docker job runs only after backend verification succeeds for backend/native changes. Use repository-owned verification commands, official GitHub setup actions, Docker Buildx cache, and no deployment/publishing steps.

**Tech Stack:** GitHub Actions, Ubuntu hosted runners, Java 25 Temurin, Maven Wrapper, Node.js 22, npm, Docker Buildx, Docker BuildKit GitHub Actions cache, GraalVM Native Image through `server/Dockerfile`, Stockfish 17.1.

## Source of Truth

- Issue: `#49 Add GitHub Actions CI with PR verification and conditional native build`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent rules: `AGENTS.md`
- Current dependency/runtime inventory: `docs/AI Chess Rivals - Tech Stack.md`
- Canonical local verification commands: `docs/BUILD_AND_VERIFY.md`
- Production native build path: `server/Dockerfile`
- Backend build definition: `server/pom.xml`
- Frontend verification entry point: `client/package.json` -> `npm run verify`

## Selected Design

Use **one CI workflow with job-level change detection**, not multiple path-filtered workflows and not a third-party changed-files action.

Why:

- A single workflow keeps CI behavior discoverable in one file.
- Job-level `if` conditions make irrelevant jobs report as skipped instead of preventing the entire workflow from existing.
- This avoids the GitHub branch-protection footgun where a required workflow skipped by top-level `paths` can remain pending forever.
- The change detector is a small Bash block using `git diff`; no plugin framework or third-party path-filter dependency is needed.
- Native verification can depend on successful backend verification, avoiding an expensive Docker/native build when ordinary Maven verification already fails.
- Frontend-only changes skip the native job completely.

Do **not** split this into reusable workflows, matrix jobs, composite actions, or a generic CI framework.

## Global Constraints

- Trigger on `pull_request` targeting `master` and `push` to `master`.
- Do not use top-level `paths`/`paths-ignore`; use job-level conditions so skipped jobs produce explicit successful/skipped checks and can be safely considered for future branch protection.
- Use Java `25` with Temurin for backend JVM verification.
- Use Node.js `22` for frontend verification.
- Backend command must remain exactly `./server/mvnw -f server/pom.xml verify`.
- Frontend dependency installation must use `npm ci` from `client/`.
- Frontend verification must use the existing `npm run verify` script from `client/`.
- Do not duplicate Maven, Spotless, Error Prone, SpotBugs, ESLint, Prettier, TypeScript, Vitest, or build rules in workflow YAML.
- CI must not require Groq/Gemini credentials and must never make real AI provider calls.
- CI must not start PostgreSQL for the existing automated verification suite.
- Native verification must build `server/Dockerfile` with build context `./server` for platform `linux/amd64`.
- Native verification must not push, publish, tag for a registry, log into a registry, or trigger Render.
- Use Docker BuildKit GitHub Actions cache (`type=gha`) for native build layers.
- Do not add QEMU: GitHub's standard Ubuntu x64 runner already matches the required `linux/amd64` target.
- Do not modify application source code merely to make CI easier. If the real native build exposes an existing compatibility defect, preserve the failing check and use `superpowers:systematic-debugging` before making the smallest root-cause fix.
- Keep local verification commands in `docs/BUILD_AND_VERIFY.md` unchanged.
- Do not implement #21 responsibilities (GHCR publishing or Render deployment).
- Do not add SonarCloud, Codecov thresholds, mutation testing, coverage gates, dependency/security scanning, release automation, matrix builds, Kubernetes, or reusable workflow infrastructure.

## File Map

**Create:**

- `.github/workflows/ci.yml` — the only workflow for #49; detects relevant changes, runs backend/frontend verification, and conditionally builds the native production image.

**Modify:**

- `docs/BUILD_AND_VERIFY.md` — document automatic GitHub CI behavior, job names, native-build conditions, credentials/database expectations, and future required-check guidance while preserving local commands.

**Do not modify unless the native job proves an existing defect:**

- `server/Dockerfile`
- `server/pom.xml`
- backend Java source/tests
- frontend source/tests
- Docker Compose
- AI provider configuration

---

### Task 1: Add the GitHub Actions CI Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: GitHub `pull_request`/`push` event SHAs, `./server/mvnw -f server/pom.xml verify`, `client/package-lock.json`, `npm run verify`, and `server/Dockerfile`.
- Produces: four GitHub check jobs named `Detect changes`, `Backend verification`, `Frontend verification`, and `Native image verification`.
- Produces from `Detect changes`: string outputs `backend`, `frontend`, and `native`, each exactly `true` or `false`.

- [ ] **Step 1: Confirm no existing workflow owns this behavior**

From the repository root run:

```bash
find .github -maxdepth 2 -type f -print 2>/dev/null || true
```

Expected before implementation: there is no existing `.github/workflows/ci.yml` to merge with. If another workflow has appeared since this plan was written, read it before continuing and avoid creating duplicate CI behavior.

- [ ] **Step 2: Create the workflow directory and file**

Create `.github/workflows/ci.yml` with exactly this initial implementation:

```yaml
name: CI

on:
  pull_request:
    branches:
      - master
  push:
    branches:
      - master

permissions:
  contents: read

jobs:
  changes:
    name: Detect changes
    runs-on: ubuntu-latest
    outputs:
      backend: ${{ steps.changes.outputs.backend }}
      frontend: ${{ steps.changes.outputs.frontend }}
      native: ${{ steps.changes.outputs.native }}
    steps:
      - name: Check out repository
        uses: actions/checkout@v7
        with:
          fetch-depth: 0

      - name: Detect relevant paths
        id: changes
        shell: bash
        env:
          EVENT_NAME: ${{ github.event_name }}
          PR_BASE_SHA: ${{ github.event.pull_request.base.sha }}
          BEFORE_SHA: ${{ github.event.before }}
        run: |
          if [[ "$EVENT_NAME" == "pull_request" ]]; then
            base_sha="$PR_BASE_SHA"
          else
            base_sha="$BEFORE_SHA"
          fi

          changed_files="$(git diff --name-only "$base_sha" "$GITHUB_SHA")"
          printf '%s\n' "$changed_files"

          backend=false
          frontend=false
          native=false

          while IFS= read -r file; do
            case "$file" in
              server/*)
                backend=true
                native=true
                ;;
              client/*)
                frontend=true
                ;;
              scripts/*)
                backend=true
                frontend=true
                ;;
              .github/workflows/ci.yml)
                backend=true
                frontend=true
                native=true
                ;;
            esac
          done <<< "$changed_files"

          {
            echo "backend=$backend"
            echo "frontend=$frontend"
            echo "native=$native"
          } >> "$GITHUB_OUTPUT"

  backend:
    name: Backend verification
    needs: changes
    if: ${{ needs.changes.outputs.backend == 'true' }}
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Set up Java 25
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "25"
          cache: maven
          cache-dependency-path: server/pom.xml

      - name: Verify backend
        run: ./server/mvnw -f server/pom.xml verify

  frontend:
    name: Frontend verification
    needs: changes
    if: ${{ needs.changes.outputs.frontend == 'true' }}
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: client
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Set up Node.js 22
        uses: actions/setup-node@v7
        with:
          node-version: "22"
          cache: npm
          cache-dependency-path: client/package-lock.json

      - name: Install frontend dependencies
        run: npm ci

      - name: Verify frontend
        run: npm run verify

  native:
    name: Native image verification
    needs:
      - changes
      - backend
    if: ${{ needs.changes.outputs.native == 'true' && needs.backend.result == 'success' }}
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v4

      - name: Build production native image
        uses: docker/build-push-action@v7
        with:
          context: ./server
          file: ./server/Dockerfile
          platforms: linux/amd64
          push: false
          cache-from: type=gha,scope=server-native
          cache-to: type=gha,mode=max,scope=server-native
```

Important details that must remain intact:

1. The workflow itself has **no top-level path filter**.
2. A `server/**` change sets both `backend=true` and `native=true`.
3. A `client/**` change sets only `frontend=true`.
4. A `scripts/**` change sets backend and frontend true because those scripts define repository verification behavior.
5. Changing `.github/workflows/ci.yml` sets all three outputs true so edits to CI exercise every job.
6. Native verification depends on backend verification succeeding.
7. There is no registry login and `push: false` is explicit.
8. The Docker build context is `./server`, matching `COPY .mvn`, `COPY mvnw pom.xml`, and `COPY src` in `server/Dockerfile`.
9. `platforms: linux/amd64` is explicit even though the hosted runner is already x64.

- [ ] **Step 3: Review the change-detection truth table before running anything**

Use this table to inspect the `case` block mechanically:

| Changed path | backend | frontend | native |
|---|---:|---:|---:|
| `server/pom.xml` | true | false | true |
| `server/src/main/...` | true | false | true |
| `server/Dockerfile` | true | false | true |
| `client/package.json` | false | true | false |
| `client/src/...` | false | true | false |
| `scripts/verify.sh` | true | true | false |
| `.github/workflows/ci.yml` | true | true | true |
| `docs/README-like-file.md` | false | false | false |

Do not broaden `native` to frontend or documentation paths.

- [ ] **Step 4: Check YAML/text hygiene locally**

Run:

```bash
git diff --check
```

Expected: exit code `0` and no whitespace errors.

Then inspect the complete workflow:

```bash
sed -n '1,260p' .github/workflows/ci.yml
```

Expected: one workflow only, with the four jobs shown above and no secret references.

- [ ] **Step 5: Run the existing local verification gates before committing CI**

On POSIX:

```bash
./scripts/verify.sh
```

On Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

Expected: existing backend and frontend verification both succeed. This does not replace the GitHub-native Docker build; it only confirms #49 did not alter local verification behavior.

- [ ] **Step 6: Commit the workflow**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add pull request verification workflow"
```

---

### Task 2: Document Automatic CI Behavior

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Consumes: exact workflow/job names and path rules from Task 1.
- Produces: contributor-facing documentation that distinguishes local verification from automatic GitHub CI and gives future branch-protection check names.

- [ ] **Step 1: Add a GitHub Actions section after `## Whole repository` and before `## Backend`**

Insert this section without changing the existing local commands:

```markdown
## GitHub Actions CI

Pull requests targeting `master` and pushes to `master` run the `CI` workflow. A lightweight
`Detect changes` job decides which verification jobs are relevant to the changed files.

- `Backend verification` runs for `server/**`, `scripts/**`, or CI workflow changes and executes
  `./server/mvnw -f server/pom.xml verify` with Java 25.
- `Frontend verification` runs for `client/**`, `scripts/**`, or CI workflow changes, installs
  dependencies with `npm ci`, and executes `npm run verify` with Node.js 22.
- `Native image verification` runs only for `server/**` or CI workflow changes, only after backend
  verification succeeds. It builds `server/Dockerfile` for `linux/amd64` with Docker Buildx and
  GitHub Actions layer caching.

Frontend-only changes therefore do not compile the GraalVM native image. Documentation-only
changes still create the lightweight `Detect changes` check, while irrelevant backend/frontend
jobs are reported as skipped.

The automated verification suite requires no Groq/Gemini credentials and does not start
PostgreSQL. AI remains disabled by default in tests, and provider tests must use local stubs/fakes
rather than real API calls.

The native CI job is verification only. It does not publish an image, log into GHCR, or deploy to
Render; image publication and deployment remain separate work.

If branch protection is enabled later, the checks produced by this workflow are:
`CI / Detect changes`, `CI / Backend verification`, `CI / Frontend verification`, and
`CI / Native image verification`. Job-level conditions intentionally report irrelevant jobs as
skipped/successful instead of omitting the workflow entirely.
```

Do not rewrite the Phase 1 acceptance record in this issue.

- [ ] **Step 2: Verify the documentation still contains the canonical local commands unchanged**

Run:

```bash
grep -n "./server/mvnw -f server/pom.xml verify" docs/BUILD_AND_VERIFY.md
grep -n "npm run verify" docs/BUILD_AND_VERIFY.md
```

Expected: both commands are still present, and the new section describes rather than replaces local verification.

- [ ] **Step 3: Check documentation diff quality**

Run:

```bash
git diff --check
git diff -- docs/BUILD_AND_VERIFY.md
```

Expected: only the new GitHub Actions CI section is added; local verification instructions and Phase 1 acceptance history are not reworked.

- [ ] **Step 4: Commit the documentation**

```bash
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: document github actions verification"
```

---

### Task 3: Validate the Workflow on GitHub and Close the Acceptance Loop

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `docs/BUILD_AND_VERIFY.md`
- Modify application/build files only if the native build exposes a real existing incompatibility and only after systematic debugging.

**Interfaces:**
- Consumes: GitHub Actions workflow created in Task 1.
- Produces: a pull request where the standard verification jobs and real production native Docker build are visibly successful.

- [ ] **Step 1: Run final repository verification before pushing**

Run:

```bash
./scripts/verify.sh
```

Expected: backend and frontend verification both succeed locally.

If running from Windows instead:

```powershell
.\scripts\verify.ps1
```

- [ ] **Step 2: Confirm #49 scope before push**

Run:

```bash
git status --short
git log --oneline -5
git diff master...HEAD --stat
```

Expected implementation scope before any native-build compatibility fix:

```text
.github/workflows/ci.yml
 docs/BUILD_AND_VERIFY.md
```

Do not include provider secrets, generated Stockfish binaries, Docker build output, node modules, Maven target output, or unrelated refactors.

- [ ] **Step 3: Push the implementation branch and open the pull request for issue #49**

Push the current branch using the repository's normal Git workflow and open a PR targeting `master` that references `#49`.

Because `.github/workflows/ci.yml` itself changed, the first PR run must set all three change outputs to `true`; therefore all four checks must appear:

```text
CI / Detect changes
CI / Backend verification
CI / Frontend verification
CI / Native image verification
```

- [ ] **Step 4: Verify standard CI behavior from the pull request**

Expected:

1. `Detect changes` prints the changed file list and completes successfully.
2. `Backend verification` installs Java 25 and runs exactly `./server/mvnw -f server/pom.xml verify`.
3. `Frontend verification` installs Node.js 22, runs `npm ci`, then `npm run verify`.
4. No job requests `AI_GROQ_API_KEY`, `AI_GEMINI_API_KEY`, or any provider secret.
5. No PostgreSQL service/container is started by the workflow.

If backend or frontend verification fails, fix the actual repository verification failure; do not weaken or bypass the canonical command in CI.

- [ ] **Step 5: Verify the native production build**

Expected in `CI / Native image verification`:

1. Docker Buildx initializes successfully.
2. The build uses context `./server` and `server/Dockerfile`.
3. The build targets `linux/amd64`.
4. The Dockerfile's GraalVM builder reaches and successfully completes:

```text
./mvnw clean package native:compile -B -DskipTests -Pnative -Plinux
```

5. The runtime image layer successfully copies both the native `ai-chess-rivals` executable and Linux Stockfish binary.
6. The job exits successfully without pushing an image.

If this job fails because the current application cannot compile natively, **do not** disable native compilation, add `continue-on-error`, remove the job, or fall back to a JVM image. Invoke `superpowers:systematic-debugging`, reproduce the failing native-build stage as closely as practical, identify the root cause, and make the minimum compatibility fix required for the existing production Dockerfile to build. Keep any such fix inside #49 because native-build success is an explicit acceptance criterion.

- [ ] **Step 6: Confirm BuildKit cache is active without turning CI into publishing**

Open the successful native job log and confirm cache import/export steps reference the GitHub Actions cache backend.

Expected workflow configuration remains:

```yaml
cache-from: type=gha,scope=server-native
cache-to: type=gha,mode=max,scope=server-native
push: false
```

There must be no `docker/login-action`, registry credentials, GHCR tags, `push: true`, Render hook, or deployment step.

- [ ] **Step 7: Confirm frontend-only changes are guaranteed to skip native compilation by the checked-in path contract**

Review the successful `Detect changes` implementation against this exact contract:

```text
client/**  -> frontend=true, backend=false, native=false
server/**  -> backend=true, native=true
```

The native job condition must remain:

```yaml
if: ${{ needs.changes.outputs.native == 'true' && needs.backend.result == 'success' }}
```

Do not add `client/**` to the native path condition. A later frontend-only PR should therefore show `CI / Native image verification` as skipped rather than building the image.

- [ ] **Step 8: Run the final scope and hygiene check after any CI-driven fixes**

Run:

```bash
git diff --check master...HEAD
git status --short
```

Expected: clean working tree after committed fixes and no unrelated files.

If CI exposed a genuine native compatibility defect and application/build files were changed, rerun both:

```bash
./server/mvnw -f server/pom.xml verify
docker buildx build --platform linux/amd64 -f server/Dockerfile server
```

Run the Docker command only where Docker/Buildx is available; GitHub Actions remains the authoritative native-build gate for #49.

- [ ] **Step 9: Final acceptance checklist before marking #49 complete**

Verify every item explicitly:

```text
[ ] PR targeting master automatically has CI checks.
[ ] Pushes to master run the same CI workflow.
[ ] Backend verification uses Java 25 and Maven verify.
[ ] Frontend verification uses Node.js 22, npm ci, and npm run verify.
[ ] No real AI provider credentials/calls are required.
[ ] No PostgreSQL service is required by CI verification.
[ ] Backend/native changes enable native verification.
[ ] Frontend-only changes keep native=false.
[ ] Native Docker build targets linux/amd64 and succeeds.
[ ] Native failure is a failing check; it is not ignored.
[ ] No image is published.
[ ] No Render deployment is triggered.
[ ] BUILD_AND_VERIFY documents CI while preserving local commands.
[ ] Workflow remains one small readable file with no CI framework.
```

Only after all applicable checks are green should issue #49 be marked complete and Phase 2 work resume.

---

## Implementation Notes for Luna

- Keep the change localized. The expected implementation is primarily **one workflow file plus one documentation edit**.
- Do not redesign the Dockerfile in anticipation of CI. First build the exact production Dockerfile already in the repository.
- Do not add provider secrets to make native compilation succeed; native compilation is a build-time check, not a provider integration test.
- Do not add a PostgreSQL service to GitHub Actions unless an existing verification test unexpectedly proves it is required; current tests are intentionally structured to avoid that dependency.
- Prefer a failing CI check over a misleading green check. Never use `continue-on-error` on backend, frontend, or native verification.
- If native compilation uncovers a failure introduced by the Spring AI provider foundation, debug and fix that compatibility issue rather than weakening the CI requirement.
- Stop at verification. GHCR publication and Render deployment belong to #21.