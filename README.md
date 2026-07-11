# AI Chess Rivals

Two AI personalities. One chessboard. Infinite trash talk.

AI Chess Rivals is a hobby showcase project for building an entertaining AI-vs-AI chess experience. The goal is not to build the strongest chess engine. The goal is to showcase practical AI engineering through personalities, reactions, match drama, and a complete product built end to end.

The current repository is still in the foundation phase:

- The backend already contains the Stockfish integration and a small `game` domain model.
- The frontend is scaffolded with the intended app structure and base UI tooling.
- The personality and LLM-driven entertainment layer is planned, but it is not implemented on `master` yet.

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
- `server/src/main/java/.../game/domain`: early chess match domain objects.

What exists now:

- Stockfish is the source of truth for chess operations.
- PostgreSQL and Flyway are configured for persistence and schema management.
- WebMVC, WebSocket, and RestClient dependencies are present in the backend stack.
- The frontend has Tailwind CSS v4 and shadcn/ui primitives wired in.

What does not exist yet on `master`:

- No implemented AI personality module.
- No LLM provider integration.
- No completed match orchestration UI.
- No production gameplay flow in the frontend yet.

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
| chess.js | 1.4.x | Client-side chess state helpers |
| react-chessboard | 5.10.x | Board UI |
| Axios | 1.18.x | HTTP client |

See [docs/AI Chess Rivals - Tech Stack.md](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) for the full dependency inventory.

## Project Structure

The previous structure summary was stale. This reflects the current repository layout on `master`.

```text
ai-chess-rivals/
|-- client/
|   |-- public/                     # Static frontend assets
|   |-- scripts/                    # Frontend verification helpers
|   |-- src/
|   |   |-- assets/                 # Images and bundled assets
|   |   |-- components/ui/          # Shared UI primitives
|   |   |-- features/               # Feature area placeholders
|   |   |-- hooks/                  # Custom React hooks
|   |   |-- lib/                    # Utilities such as cn()
|   |   |-- pages/                  # Route-level page placeholders
|   |   |-- services/               # Client API layer placeholders
|   |   |-- store/                  # Zustand store placeholders
|   |   |-- types/                  # Shared TypeScript types
|   |   |-- App.tsx                 # Current scaffold UI
|   |   `-- main.tsx                # Frontend entrypoint
|   |-- components.json             # shadcn/ui config
|   |-- package.json
|   `-- vite.config.ts
|-- docs/
|   |-- AI Chess Context.md
|   |-- AI Chess Rivals - Constitution.md
|   |-- AI Chess Rivals - Tech Stack.md
|   `-- BUILD_AND_VERIFY.md
|-- scripts/
|   |-- verify.ps1                  # Root verification script for Windows
|   `-- verify.sh                   # Root verification script for POSIX shells
|-- server/
|   |-- src/
|   |   |-- main/
|   |   |   |-- java/dev/krishnamurti/ai_chess_rivals/
|   |   |   |   |-- chess/          # Stockfish client, engine, UCI support
|   |   |   |   `-- game/domain/    # Match, move, board position domain model
|   |   |   `-- resources/
|   |   |       |-- application.yaml
|   |   |       `-- db/migration/   # Flyway SQL migrations
|   |   `-- test/java/...           # Modulith, Stockfish, and domain tests
|   |-- stockfish/                  # Download target for local Stockfish binaries
|   |-- docker-compose.yml          # Local Postgres and backend containers
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
| Node.js | 22+ |
| Docker Desktop | Recent |

### 1. Clone

```bash
git clone https://github.com/krishna916/ai-chess-rivals.git
cd ai-chess-rivals
```

### 2. Start PostgreSQL

From `server/`:

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

# Linux / macOS
STOCKFISH_PATH=stockfish/stockfish ./mvnw spring-boot:run
```

Backend defaults:

- App: `http://localhost:8080`
- Actuator: `http://localhost:8081`
- Docker-mapped backend port: `http://localhost:8082`

### 4. Run the frontend

From `client/`:

```bash
npm install
npm run dev
```

Frontend dev server: `http://localhost:5173`

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
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/aichessrivals` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `secretpassword` |
| `SPRING_FLYWAY_URL` | Falls back to datasource URL |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `STOCKFISH_PATH` | `stockfish/stockfish` |
| `STOCKFISH_THREADS` | `1` |
| `STOCKFISH_HASH_MB` | `16` |
| `APP_WEBSOCKET_ALLOWED_ORIGIN` | `http://localhost:5173` |

## Live Match Stream

- Endpoint: `ws://localhost:8080/ws/match` when the backend runs directly, or `ws://localhost:8082/ws/match` through Docker
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

This runs:

- Backend Maven `verify`
- Frontend `npm run verify`

See [docs/BUILD_AND_VERIFY.md](docs/BUILD_AND_VERIFY.md) for the full verification workflow.

## Documentation

- [AGENTS.md](AGENTS.md): contributor and agent rules
- [docs/AI Chess Rivals - Constitution.md](docs/AI%20Chess%20Rivals%20-%20Constitution.md): project principles
- [docs/AI Chess Rivals - Tech Stack.md](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md): dependency inventory
- [server/README.md](server/README.md): backend-specific notes

## Status

`master` currently represents an early implementation baseline:

- Backend chess engine integration is in place.
- Core `game` domain types are in place.
- Verification and code quality gates are configured.
- Frontend foundation is set up, but product features are still mostly ahead.

That is intentional. This project is being built in phases, with chess foundations first and AI entertainment layers added later.
