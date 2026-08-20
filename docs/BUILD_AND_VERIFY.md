# Build and Verify

The repository provides lightweight verification gates for Java and frontend code. Run the
root verifier before opening a pull request.

## Prerequisites

- JDK 25
- Maven 3.9 or newer (the Maven wrapper is included)
- Node.js 22 or newer and npm

## Whole repository

From any working directory, use the script appropriate for your shell:

```powershell
.\scripts\verify.ps1
```

```sh
./scripts/verify.sh
```

The scripts stop at the first failing backend or frontend check.

## GitHub Actions CI

Pull requests targeting `master` and pushes to `master` run the `CI` workflow. A lightweight
`Detect changes` job decides which verification jobs are relevant to the changed files.

- `Backend verification` runs for `server/**`, `scripts/**`, or CI workflow changes, prepares the
  pinned Linux Stockfish binary with `./server/mvnw -f server/pom.xml generate-resources -Plinux`,
  sets `STOCKFISH_PATH` to that executable, and executes `./server/mvnw -f server/pom.xml verify`
  with Java 25.
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

## Native AI topology verification

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

Normal JVM verification continues to use the default AI-disabled mode. For Docker Compose,
changing `AI_ENABLED` requires rebuilding the backend because the native bean topology is selected
during AOT compilation.

If branch protection is enabled later, the checks produced by this workflow are:
`CI / Detect changes`, `CI / Backend verification`, `CI / Frontend verification`, and
`CI / Native image verification`. Job-level conditions intentionally report irrelevant jobs as
skipped/successful instead of omitting the workflow entirely.

## Backend

Run Maven verification from the repository root:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

```sh
./server/mvnw -f server/pom.xml verify
```

The verify lifecycle enforces Java 25 and Maven 3.9+, checks Java formatting, compiles with
Error Prone, runs tests (including Spring Modulith structure verification), and runs SpotBugs.
Apply Java formatting with `server\mvnw.cmd -f server\pom.xml spotless:apply` on Windows or
`./server/mvnw -f server/pom.xml spotless:apply` on POSIX systems.

### Optional dialogue persistence PostgreSQL integration test

The explicit `DialoguePersistencePostgresIT` is excluded from the normal Surefire run. Run it
against an isolated PostgreSQL 17 container and keep the normal verifier independent of Docker:

```powershell
docker run --name ai-chess-rivals-dialogue-it --rm -d `
  -e POSTGRES_DB=aichessrivals_it `
  -e POSTGRES_PASSWORD=secretpassword `
  -p 55433:5432 postgres:17-alpine

server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePersistencePostgresIT test

docker stop ai-chess-rivals-dialogue-it
```

The test pins both datasource and Flyway URLs to `localhost:55433`, validates Flyway V1–V4 and
Hibernate schema mapping, and proves dialogue idempotency, chronological history, and last-four
context behavior. Do not point it at the normal development database or volume.

## Frontend

Run these commands from `client/`:

```text
npm run format
npm run format:check
npm run typecheck
npm run verify
```

`npm run verify` sequentially runs formatting, type checking, linting, Vitest, and the
production build. It stops at the first failure and does not rely on shell-specific command
chaining.

## Phase 2 AI observability and resilience verification

The Phase 2 automated tests are credential-safe. They use local provider stubs and deterministic
exceptions, never real Groq or Gemini requests, and do not log prompts, completions, personality
text, or API keys. Run the normal repository verifier with AI disabled before any manual provider
check:

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

For a fast local acceptance run, start the backend with zero move and dialogue pacing while
keeping the normal application and management ports:

```powershell
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
```

When exercising real dialogue generation, set `AI_ENABLED=true` and provide
`AI_GROQ_API_KEY`, `AI_GROQ_MODEL`, `AI_GEMINI_API_KEY`, and `AI_GEMINI_MODEL` through an ignored
local environment file or the process environment. Never paste those values into this document,
the terminal transcript, issue comments, or application logs. The configured provider timeouts are
8 seconds for Groq and 12 seconds for Gemini; failover is bounded and does not retry a provider.

Inspect the management endpoint while a match is running:

```text
http://localhost:8081/actuator/health
http://localhost:8081/actuator/metrics/ai.gateway.provider.duration
http://localhost:8081/actuator/metrics/ai.gateway.fallback.activations
http://localhost:8081/actuator/metrics/ai.gateway.responses
```

The metrics use only low-cardinality provider, outcome, target, source, and reason tags. Confirm
that a successful primary response records `groq` plus `success` and `primary`; a provider failure
records the corresponding failure or timeout and a Gemini or deterministic-fallback activation;
and an AI-disabled match records `deterministic_fallback` plus `ai_disabled`. A forced Groq failure
may be exercised safely by pointing `AI_GROQ_BASE_URL` at a local stub that deterministically fails;
do not use a production key against an uncontrolled endpoint.

For the manual Phase 2 acceptance pass, record only observations that were actually made:

- [ ] Four selectable personalities start a match and remain associated with the correct players.
- [ ] A random-rivalry match produces contextual dialogue after committed chess events.
- [ ] Refresh hydrates the current board and unified activity without duplicate entries.
- [ ] Disconnect and reconnect restore the authoritative state without duplicate dialogue or moves.
- [ ] Stopping and resuming a match preserves the latest valid board and dialogue ordering.
- [ ] An AI-disabled match remains playable with deterministic fallback dialogue and response metrics.
- [ ] A controlled Groq failure activates Gemini or deterministic fallback and emits safe metrics/logs.
- [ ] No prompt, completion, personality text, or credential appears in captured application output.

The dated acceptance record below this section must list the environment, checks performed, and any
unchecked items with their reason. Automated test results are evidence for the resilience matrix,
but they do not count as browser or real-provider observations.

## Phase 1 end-to-end acceptance

Use the normal local-development topology so the management and application ports remain
separate:

```powershell
cd server
docker compose up -d postgres
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:OWNER_CONTROL_TOKEN = [Convert]::ToHexString(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLower()
.\mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
cd client
npm.cmd run dev
```

Open `http://localhost:5173` to confirm the viewer is read-only. Then open
`http://localhost:5173/admin`, enter the value generated for `OWNER_CONTROL_TOKEN`, and use
that route for Start/Stop. The application API and WebSocket use port `8082`; the local
Actuator health endpoint is `http://localhost:8081/actuator/health`. The Docker backend does not
publish its separate management port to the host, so run the backend locally when the host needs
to query Actuator directly.

The zero-delay values keep the production pacing code path active while making a complete match
practical to observe. Do not use them as production defaults.

### Acceptance record — 2026-07-13

The following items were observed with Stockfish 17.1, the backend on `8082`, the frontend on
`localhost:5173`, and a 360-pixel responsive viewport check.

#### Startup

- [x] Backend starts with the configured Stockfish binary.
- [x] Health check reports `UP` on management port `8081`.
- [x] Frontend loads with no browser console errors or warnings.
- [x] WebSocket connects and clears a previous connection error after reconnecting.
- [ ] The no-match state shows an empty board and activity panel with no public controls.

#### Match lifecycle

- [ ] A valid token entered on `/admin` starts one active match and updates both routes.
- [x] Moves, board position, active side, move count, and activity advance together.
- [x] Capture and check annotations appear during live play and after snapshot hydration.
- [x] One real Stockfish-vs-Stockfish match completed autonomously as a draw after 70 plies.
- [x] The final board, one final activity entry, and `DRAW` result remain visible.
- [ ] A second match can start from `/admin` after the configured cooldown.
- [ ] Promotion was not encountered in the observed games; snapshot reconstruction is covered by
      focused store tests.

#### Resilience

- [x] Refresh after completion restores the final position, 72 activities, annotations, and one
      final entry.
- [x] Disconnect shows `Connection lost. Reconnecting...`; reconnect clears it and hydrates the
      authoritative current state without duplicate activity.
- [ ] Stop from `/admin` preserves the latest valid board and exposes `Stopped` publicly.
- [x] Stop during Stockfish selection no longer interrupts the UCI reader; the stopped game resumed
      from ply 4 and completed without an illegal stale move.
- [x] Unit coverage verifies repeated Stop/Start lifecycle calls remain safe.

#### Responsive viewer

- [x] The 360-pixel viewport has no horizontal overflow.
- [x] The board and activity panel stack vertically on mobile.
- [x] Long activity history scrolls inside the activity panel without scrolling the page header
      out of view.
- [ ] Connection state and status remain readable at the narrow public viewport.
