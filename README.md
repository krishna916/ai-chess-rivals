# AI Chess Rivals

Two AI personalities. One chessboard. Infinite trash talk.

AI Chess Rivals is a hobby showcase project for building an entertaining AI-vs-AI chess experience. The goal is not to build the strongest chess engine. The goal is to showcase practical AI engineering through personalities, reactions, match drama, and a complete product built end to end.

The repository contains the chess foundation and the Phase 2 entertainment layer:

- Autonomous Stockfish matches with owner-only Start/Stop and resume controls.
- Four selectable personalities, random rivalry setup, and contextual dialogue.
- Spring AI integration with OpenRouter primary/fallback models and deterministic fallback.
- A read-only live viewer with board state, move annotations, and a unified dialogue/activity feed.

## Principles

- Entertainment first: the personalities are the product.
- AI showcase first: prefer practical LLM integration over theoretical architecture.
- Simplicity over abstraction: use the simplest solution that works.
- Shipping over perfection: optimize for momentum and readability.

## Current Architecture

Today the codebase is a modular monolith with a React client:

- `server/`: Spring Boot 4 backend using Spring Modulith.
- `client/`: React 19 + Vite 8 frontend.
- `server/src/main/java/.../chess`: Stockfish process management and UCI integration.
- `server/src/main/java/.../game`: match lifecycle, Chesslib board progression, REST controls, and WebSocket streaming.
- `server/src/main/java/.../ai`: personality roster, dialogue generation/persistence, and provider fallback.
- `server/src/main/java/.../observability`: request correlation.

Stockfish selects moves and evaluates positions; Chesslib validates/applies moves and detects
terminal outcomes. LLMs generate entertainment only. PostgreSQL/Flyway persist personalities
and dialogue; the active match, cooldown, and daily-start counters remain in memory.

The frontend uses hash routing: `/#/` is the public viewer and `/#/admin` contains owner controls.
Refresh/reconnect hydrate board and dialogue state from the backend. Production uses a static
GitHub Pages frontend and a GraalVM native backend image deployed through GHCR to Render.
Phase 3 tools, chat memory, and autonomous workflows remain deferred.

## Tech Stack

### Backend

| Technology | Version | Purpose |
| --- | --- | --- |
| Java | 25 | Language runtime |
| Spring Boot | 4.1.0 | Application framework |
| Spring Modulith | 2.1.0 | Modular monolith boundaries |
| PostgreSQL | 17 | Local and production database |
| Flyway | Managed by Spring Boot | Database migrations |
| Stockfish | 17.1 | Chess engine via UCI |
| Lombok | Current | Boilerplate reduction |
| GraalVM Native Image | Current toolchain target | Production compilation target |

### Frontend

| Technology | Version | Purpose |
| --- | --- | --- |
| React | 19.2.x | UI library |
| Vite | 8.1.x | Dev server and build tool |
| TypeScript | 6.0.x | Type safety |
| Tailwind CSS | 4.3.x | Styling |
| shadcn/ui | 4.12.x | UI primitives |
| Zustand | 5.0.x | State management |
| React Router DOM | 7.18.x | Routing |
| chess.js | 1.4.x | Installed; currently unused by the read-only viewer |
| react-chessboard | 5.10.x | Board UI |
| Axios | 1.18.x | HTTP client |

See [docs/AI Chess Rivals - Tech Stack.md](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) for the full dependency inventory.

## Project Structure

The main source and development-tool directories are:

```text
ai-chess-rivals/
|-- client/
|   |-- public/                     # Static frontend assets
|   |-- scripts/                    # Frontend verification helpers
|   |-- src/
|   |   |-- assets/                 # Images and bundled assets
|   |   |-- components/ui/          # Shared UI primitives
|   |   |-- features/               # Admin controls and match viewer
|   |   |-- hooks/                  # Custom React hooks
|   |   |-- lib/                    # Utilities such as cn()
|   |   |-- pages/                  # Admin and viewer route pages
|   |   |-- services/               # REST and WebSocket clients
|   |   |-- store/                  # Match viewer state and hydration
|   |   |-- types/                  # Shared TypeScript types
|   |   |-- App.tsx                 # Hash-based viewer/admin routes
|   |   `-- main.tsx                # Frontend entrypoint
|   |-- components.json             # shadcn/ui config
|   |-- package.json
|   `-- vite.config.ts
|-- docs/
|   |-- AI Chess Context.md
|   |-- AI Chess Rivals - Constitution.md
|   |-- AI Chess Rivals - Tech Stack.md
|   |-- AI Chess Rivals - Implementation Strategy.md
|   |-- Code Formatting Guidelines.md
|   |-- PERSONALITIES.md
|   `-- BUILD_AND_VERIFY.md
|-- run-client.ps1                  # Start frontend from Windows PowerShell
|-- run-client.sh                   # Start frontend from Linux/macOS shells
|-- run-db.ps1                      # Start PostgreSQL from Windows PowerShell
|-- run-db.sh                       # Start PostgreSQL from Linux/macOS shells
|-- run-server.ps1                  # Start backend from Windows PowerShell
|-- run-server.sh                   # Start backend from Linux/macOS shells
|-- scripts/
|   |-- verify.ps1                  # Root verification script for Windows
|   `-- verify.sh                   # Root verification script for POSIX shells
|-- server/
|   |-- src/
|   |   |-- main/
|   |   |   |-- java/dev/krishnamurti/ai_chess_rivals/
|   |   |   |   |-- chess/          # Stockfish client, engine, UCI support
|   |   |   |   |-- game/           # Domain, execution, REST, WebSocket, config
|   |   |   |   |-- ai/             # Personalities, dialogue, provider integration
|   |   |   |   `-- observability/  # Request tracing
|   |   |   `-- resources/
|   |   |       |-- application.yaml
|   |   |       `-- db/migration/   # Flyway SQL migrations
|   |   `-- test/java/...           # Modulith, Stockfish, and domain tests
|   |-- stockfish/                  # Download target for local Stockfish binaries
|   |-- docker-compose.yml          # Local Postgres and backend containers
|   |-- run-local.ps1               # Start locally configured backend on Windows
|   |-- run-local.sh                # Start locally configured backend on Linux/macOS
|   |-- Dockerfile
|   |-- pom.xml
|   `-- README.md
|-- AGENTS.md
`-- README.md
```

## Getting Started

### Prerequisites

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Maven | 3.9+ |
| Node.js | 22.13+ within Node 22, or Node 24+ |
| Docker Desktop | Recent |

### 1. Clone

```bash
git clone https://github.com/krishna916/ai-chess-rivals.git
cd ai-chess-rivals
```

### 2. Configure local environment and start PostgreSQL

From `server/`, copy `.env.example` to `.env` if you do not already have a local file
(PowerShell: `Copy-Item .env.example .env`; POSIX: `cp .env.example .env`). Replace the
placeholders before starting either service:

- Set `POSTGRES_DB=aichessrivals` and `POSTGRES_USER=postgres`, and choose `POSTGRES_PASSWORD`.
- Set `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/aichessrivals`,
  `SPRING_DATASOURCE_USERNAME=postgres`, and `SPRING_DATASOURCE_PASSWORD` to the same password.
- Set the Flyway URL/user/password to the same local database values.
- Generate and set `OWNER_CONTROL_TOKEN` using the commands in [Configuration](#configuration).
- Keep `AI_ENABLED=false` for a first run; deterministic personality dialogue needs no provider key.

Compose loads `.env` for database settings. Spring imports it when launched from `server/`.
The template contains placeholders, not ready-to-run credentials. Then start PostgreSQL:

```bash
docker compose up -d postgres
```

This exposes PostgreSQL on `localhost:5433`.

### 3. Download Stockfish and run the backend

From `server/`:

```bash
# Windows
.\mvnw.cmd package -Pwindows -DskipTests

# Linux
./mvnw package -Plinux -DskipTests
```

Then run the app:

```bash
# Windows PowerShell
$env:STOCKFISH_PATH = "stockfish/stockfish.exe"
.\mvnw.cmd spring-boot:run

# Linux
STOCKFISH_PATH=stockfish/stockfish ./mvnw spring-boot:run
```

The provided download profiles target Windows/Linux x86-64 AVX2. On macOS or another
architecture, supply a compatible Stockfish executable separately and set `STOCKFISH_PATH`.

Backend defaults:

- App: `http://localhost:8082`
- Actuator: `http://localhost:8081`
- Docker-mapped backend port: `http://localhost:8082`

### 4. Run the frontend

From `client/`:

```bash
npm install
npm run dev
```

Frontend viewer: `http://localhost:5173/#/`; owner controls: `http://localhost:5173/#/admin`.
Enter the backend's owner token to unlock Start/Stop. Select two distinct personalities or
randomize the rivalry before starting a new match.

### Convenience scripts

From the repository root, run each command in its own terminal:

```bash
./run-db.sh
./server/run-local.sh
./run-client.sh
```

`./run-server.sh` starts the backend with its normal environment defaults. Use
`./server/run-local.sh` to explicitly export local settings. It connects to PostgreSQL on port
`5433`, listens on port `8082`, and requires `SPRING_DATASOURCE_PASSWORD` or
`SPRING_FLYWAY_PASSWORD` to be set in the shell. All POSIX scripts can be started from the
repository root; they locate their working directories automatically. The local launcher
activates `dev`, but there is no profile-specific application configuration; its environment
exports supply the settings. These backend scripts still require the configured owner token
and a platform-compatible Stockfish binary.

## Configuration

### Default local ports

| Service | Internal Port | Host Port |
| --- | --- | --- |
| PostgreSQL | 5432 | 5433 |
| Spring Boot app in Docker | 8080 | 8082 |
| Vite dev server | 5173 | 5173 |

### Important backend environment variables

| Variable | Default |
| --- | --- |
| `SERVER_PORT` | `8082` (Compose overrides it to `8080` inside the container) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/aichessrivals` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `secretpassword` |
| `SPRING_FLYWAY_URL` | Falls back to datasource URL |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `STOCKFISH_PATH` | `stockfish/stockfish` |
| `STOCKFISH_THREADS` | `1` |
| `STOCKFISH_HASH_MB` | `16` |
| `APP_WEBSOCKET_ALLOWED_ORIGIN` | Additional exact REST/WebSocket origin; defaults to `http://localhost:5173` |
| `OWNER_CONTROL_TOKEN` | Required; generate a random 32-byte token |
| `MATCH_COOLDOWN` | `60s` |
| `MATCH_DAILY_START_LIMIT` | `12` accepted starts per UTC day |

REST CORS and the match WebSocket always accept HTTP and HTTPS origins from `localhost` and
subdomains of `krishnamurti.dev`, with or without an explicit port. The bare domain
`krishnamurti.dev`, loopback IP addresses, and unrelated hosts are not included in these patterns.
`APP_WEBSOCKET_ALLOWED_ORIGIN` can add one exact origin alongside this built-in policy. Owner
Start/Stop requests still require the bearer token.

Generate `OWNER_CONTROL_TOKEN` with PowerShell:

```powershell
[Convert]::ToHexString(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLower()
```

Or on Linux/macOS:

```bash
openssl rand -hex 32
```

Configure the same token in the backend/Render environment and your password
manager. Enter it manually at `http://localhost:5173/#/admin`; it is retained only
in that tab's `sessionStorage`. Routing uses URL hashes locally and in production;
use `/#/admin` to open the owner controls. The public `/#/` route is read-only.
Cooldown and daily quota state are in memory, reset on restart, and require a single backend
instance.

## Live Match Stream

- Default endpoint: `ws://localhost:8082/ws/match` for direct backend execution and Docker
- Messages use a stable envelope: `{ "type": "...", "payload": ... }`
- The first server message is `MATCH_STATE` when a match exists, otherwise `NO_MATCH`
- The backend currently supports exactly one active match stream

## Verification

Run the repository-level verifier before opening a PR:

```powershell
.\scripts\verify.ps1
```

```sh
./scripts/verify.sh
```

Prepare Stockfish and the isolated integration-test database on port `55433` first, as described
in [Build and Verify](docs/BUILD_AND_VERIFY.md#postgresql-integration-tests).

This runs:

- Backend Maven `verify`
- Frontend `npm run verify`

See [docs/BUILD_AND_VERIFY.md](docs/BUILD_AND_VERIFY.md) for the full verification workflow.

## Documentation

- [AGENTS.md](AGENTS.md): contributor and agent rules
- [docs/AI Chess Rivals - Constitution.md](docs/AI%20Chess%20Rivals%20-%20Constitution.md): project principles
- [docs/AI Chess Rivals - Tech Stack.md](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md): dependency inventory
- [docs/AI Chess Rivals - Implementation Strategy.md](docs/AI%20Chess%20Rivals%20-%20Implementation%20Strategy.md): phase boundaries and dialogue flow
- [docs/PERSONALITIES.md](docs/PERSONALITIES.md): four system character designs
- [server/README.md](server/README.md): backend-specific notes
- [client/README.md](client/README.md): frontend development and deployment

## Status

Phase 1 chess foundations and Phase 2 personality/dialogue features are implemented.
Verification covers module boundaries, match lifecycle, provider resilience, database migrations,
and frontend state/UI behavior. Dated acceptance records in
[Build and Verify](docs/BUILD_AND_VERIFY.md) distinguish automated checks from manual observations.
Phase 3 agentic capabilities remain out of scope for the current implementation.
