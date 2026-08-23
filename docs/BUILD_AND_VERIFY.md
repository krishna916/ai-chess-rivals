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

The automated verification suite requires no OpenRouter credentials and does not start
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
Because construction of the enabled gateway requires both qualified OpenRouter clients to resolve,
this verifies the enabled provider/gateway chain without depending on Spring Framework internal
bean-creation logs or exposing an additional Actuator endpoint. CI also verifies that provider
key/model environment values are not present in the final image configuration. The check performs
no real OpenRouter request and requires no provider secret.

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

The test pins both datasource and Flyway URLs to `localhost:55433`, validates Flyway V1–V5 and
Hibernate schema mapping, and proves dialogue idempotency, chronological history, and last-four
context behavior. The separate `AiResponseSourceMigrationPostgresIT` creates a disposable database,
migrates through V4, seeds legacy Groq/Gemini rows, applies V5, and asserts the provider-neutral
values. Do not point either test at the normal development database or volume.

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
exceptions, never real OpenRouter requests, and do not log prompts, completions, personality
text, or API keys. Run the normal repository verifier with AI disabled before any manual provider
check:

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

For a fast local acceptance run, start the backend with six plies, minimal engine think time, and
zero move/dialogue pacing while keeping the normal application and management ports:

```powershell
$env:GAME_MOVE_THINK_TIME_MILLIS = "1"
$env:GAME_MAX_PLIES = "6"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:MATCH_COOLDOWN = "0s"
```

`GAME_MOVE_THINK_TIME_MILLIS` uses `1` rather than `0` because runtime configuration validates the
value with a minimum of one millisecond; six plies are enough for a short acceptance match while
still exercising start dialogue and move-reaction context.

When exercising real dialogue generation, set `AI_ENABLED=true` and provide the following through
an ignored local environment file or the process environment. Never paste the key into this
document, the terminal transcript, issue comments, or application logs:

```text
AI_OPENROUTER_API_KEY=<secret supplied only in the local shell or deployment secret store>
AI_OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
AI_OPENROUTER_PRIMARY_MODEL=nvidia/nemotron-3-ultra-550b-a55b:free
AI_OPENROUTER_FALLBACK_MODEL=~deepseek/deepseek-v4-flash-latest
AI_OPENROUTER_PRIMARY_TIMEOUT=8s
AI_OPENROUTER_FALLBACK_TIMEOUT=12s
```

The primary must be a specific `:free` model, not `openrouter/free`. The remote flow is bounded:
one primary attempt, exactly one remote-fallback attempt, then deterministic personality-specific
fallback. There is no same-model retry.

Inspect the management endpoint while a match is running:

```text
http://localhost:8081/actuator/health
http://localhost:8081/actuator/metrics/ai.gateway.provider.duration
http://localhost:8081/actuator/metrics/ai.gateway.fallback.activations
http://localhost:8081/actuator/metrics/ai.gateway.responses
```

The metrics use only low-cardinality provider, outcome, target, source, and reason tags. Confirm
that a successful primary response records `primary` plus `success` and `primary`; a remote failure
records the corresponding failure or timeout and a `remote_fallback` or deterministic-fallback
activation; and an AI-disabled match records `deterministic_fallback` plus `ai_disabled`.

To exercise the controlled remote-fallback path, keep the real OpenRouter key and fallback model,
but set the primary model to an intentionally invalid ID that still satisfies startup policy:

```powershell
$env:AI_ENABLED = "true"
$env:AI_OPENROUTER_PRIMARY_MODEL = "invalid/forced-primary-failure:free"
$env:AI_OPENROUTER_FALLBACK_MODEL = "~deepseek/deepseek-v4-flash-latest"
# Keep AI_OPENROUTER_API_KEY in the ignored local environment only.
```

Restart the backend, start one short match, and confirm one `primary` failure activates
`remote_fallback` exactly once, followed by a successful remote-fallback response and continued
dialogue/match progress. Restore the real primary model immediately after the run. Do not use a
production key against an uncontrolled endpoint.

For the manual Phase 2 acceptance pass, record only observations that were actually made:

- [ ] Four selectable personalities start a match and remain associated with the correct players.
- [ ] A random-rivalry match produces contextual dialogue after committed chess events.
- [ ] Refresh hydrates the current board and unified activity without duplicate entries.
- [ ] Disconnect and reconnect restore the authoritative state without duplicate dialogue or moves.
- [ ] Stopping and resuming a match preserves the latest valid board and dialogue ordering.
- [ ] An AI-disabled match remains playable with deterministic fallback dialogue and response metrics.
- [x] A controlled primary failure activated the bounded remote-fallback/deterministic-fallback path and emitted
      safe metrics/logs.
- [ ] No prompt, completion, personality text, or credential appears in captured application output.

The dated acceptance record below this section must list the environment, checks performed, and any
unchecked items with their reason. Automated test results are evidence for the resilience matrix,
but they do not count as browser or real-provider observations.

### Acceptance record — 2026-08-20

#### Automated evidence

- [x] Backend verification passed with 306 tests, Spotless, and SpotBugs reporting zero findings.
- [x] The focused backend resilience slice passed, including provider success, failure, timeout,
      validation-failure, fallback, MDC lifecycle, and match stop/resume coverage.
- [x] The focused frontend activity-ordering suite passed 29 tests.
- [x] Captured-output regression coverage proved provider logs contain safe metadata without prompt
      or response content.
- [x] The root verifier passed with `AI_ENABLED=false`: backend 306 tests, Spotless, SpotBugs,
      frontend format, typecheck, lint, 83 tests, and production build.

#### Manual and runtime evidence

- [x] Browser acceptance ran with PostgreSQL healthy, the backend on `8082`/`8081`, and the
      frontend on `localhost:5173`.
- [x] The AI-disabled full-stack match ran with deterministic fallback dialogue; Gremlin vs Regent
      reached 111 moves and the viewer rendered 195 activity events. The response metric reported
      the `deterministic_fallback` source with the `ai_disabled` reason.
- [x] The roster exposed all four selectable personalities (Blaze, Vesper, Gremlin, and Regent);
      Gremlin vs Regent and Blaze vs Vesper starts preserved the correct player associations.
- [x] Refresh/reconnect hydration preserved the same 195-event activity count without duplicate
      `Match started` entries.
- [x] Stop/resume browser evidence showed Blaze vs Vesper stopped after four moves, then resumed
      the same match ID from move four and continued live play.
- [ ] Random-rivalry selection was not exercised through the owner-controls UI in this pass.
- [ ] Real-provider and controlled OpenRouter-failure checks were not run; no provider request was made
      and local credentials were not inspected or exposed.

### Acceptance record — 2026-08-23

#### Automated evidence

- [x] The focused backend regression slice passed: 46 tests, including OpenRouter topology,
      provider-neutral response sources, failover metrics, runtime hints, pacing, and dialogue
      generation.
- [x] The root verifier passed with `AI_ENABLED=false`: backend tests, Spotless, SpotBugs, frontend
      formatting, typecheck, lint, 83 tests, and production build.
- [x] PostgreSQL 17 was started in the documented disposable container;
      `DialoguePersistencePostgresIT` passed 2 persistence tests and
      `AiResponseSourceMigrationPostgresIT` passed the V4-to-V5 legacy Groq/Gemini conversion test.
- [ ] The local native-image Docker build did not produce a result; Docker Desktop terminated the
      build stream with `rpc error: code = Unavailable desc = error reading from server: EOF` during
      GraalVM compilation. No source-level native-image failure was reported.
- [x] GitHub Actions run `32650368156` passed backend, frontend, and native-image verification,
      including the production native image build, AI-enabled topology startup, and no-baked-provider
      environment checks.

#### Manual and runtime evidence

- [x] A real OpenRouter six-ply match ran with primary
      `nvidia/nemotron-3-ultra-550b-a55b:free` and remote fallback
      `~deepseek/deepseek-v4-flash-latest`. The match completed six moves and persisted eight dialogue
      lines, including four `REMOTE_PRIMARY`, three `REMOTE_FALLBACK`, and one `DETERMINISTIC_FALLBACK`
      source. An isolated primary-only request also returned HTTP 200 with usable content from the
      configured primary model.
- [x] The controlled primary-failure path used the process-only override
      `invalid/forced-primary-failure:free` with the real fallback configuration. Match
      `6149e3e7-317d-4896-a97a-d511b7d02984` completed one ply; one remote-fallback response and
      deterministic fallback responses were persisted, with fresh counters showing one
      `remote_fallback` response, three `deterministic_fallback` responses, and five primary failures.
      The override was not written to `.env`.
- [x] Automated fallback tests covered the one-shot remote-fallback and deterministic-fallback paths,
      including low-cardinality source metrics and safe captured-output assertions.
- [ ] Browser match, refresh/reconnect, stop/resume, and normal 7–12 second pacing were not observed
      in this pass. The real-provider runs used `0s` pacing only as verification acceleration; the
      documented 7–12 second production defaults were unchanged.
- [x] No secret, prompt, completion, or personality text was exposed by the automated verification
      output or the recorded runtime evidence; the ignored local environment was inspected by variable
      names only.

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
