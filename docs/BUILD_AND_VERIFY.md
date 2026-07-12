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

## Frontend

Run these commands from `client/`:

```text
npm run format
npm run format:check
npm run typecheck
npm run verify
```

`npm run verify` sequentially runs formatting, type checking, linting, and the production
build. It stops at the first failure and does not rely on shell-specific command chaining.

## Phase 1 end-to-end acceptance

Use the normal local-development topology so the management and application ports remain
separate:

```powershell
cd server
docker compose up -d postgres
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
.\mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
cd client
npm.cmd run dev
```

Open `http://localhost:5173`. The application API and WebSocket use port `8082`; the local
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
- [x] The no-match state shows an empty board, disabled Stop control, and empty activity panel.

#### Match lifecycle

- [x] Start creates one active match and updates the page without a refresh.
- [x] Moves, board position, active side, move count, and activity advance together.
- [x] Capture and check annotations appear during live play and after snapshot hydration.
- [x] One real Stockfish-vs-Stockfish match completed autonomously as a draw after 70 plies.
- [x] The final board, one final activity entry, and `DRAW` result remain visible.
- [x] A second match can start after completion.
- [ ] Promotion was not encountered in the observed games; snapshot reconstruction is covered by
      focused store tests.

#### Resilience

- [x] Refresh after completion restores the final position, 72 activities, annotations, and one
      final entry.
- [x] Disconnect shows `Connection lost. Reconnecting...`; reconnect clears it and hydrates the
      authoritative current state without duplicate activity.
- [x] Stop preserves the latest valid board and exposes `Stopped` plus `Resume Match`.
- [x] Stop during Stockfish selection no longer interrupts the UCI reader; the stopped game resumed
      from ply 4 and completed without an illegal stale move.
- [x] Unit coverage verifies repeated Stop/Start lifecycle calls remain safe.

#### Responsive viewer

- [x] The 360-pixel viewport has no horizontal overflow.
- [x] The board and activity panel stack vertically on mobile.
- [x] Long activity history scrolls inside the activity panel without scrolling the page header
      out of view.
- [x] Controls, connection state, and status remain readable at the narrow viewport.
