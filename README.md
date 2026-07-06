<div align="center">

# ♟️ AI Chess Rivals

**Two AI personalities. One chessboard. Infinite trash talk.**

An AI-vs-AI chess experience where the entertainment isn't the chess — it's the personalities playing it.

Built to showcase practical AI engineering: LLM integration, prompt engineering, personality design, and end-to-end product development.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6.0-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Stockfish](https://img.shields.io/badge/Stockfish-17.1-4A90D9?logo=lichess&logoColor=white)](https://stockfishchess.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

</div>

---

## 🎯 What is this?

AI Chess Rivals is a **hobby showcase project** where two AI-driven chess personalities compete against each other — complete with trash talk, emotional reactions, and match commentary.

> **The chessboard is the stage. The personalities are the product.**

The chess engine (Stockfish) handles the moves. The LLMs handle the drama.

### Core Principles

- **Entertainment First** — Personality, rivalries, and trash talk over stronger chess
- **AI Showcase** — Demonstrating LLM integration, prompt engineering, and personality design
- **Simplicity** — The simplest solution that creates an entertaining experience wins
- **Ship Fast** — A working implementation beats a perfect one

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Client (React)                     │
│  React 19 · Vite 8 · TypeScript · Tailwind CSS · shadcn │
│  chess.js (move validation) · react-chessboard (board)  │
├─────────────────────────────────────────────────────────┤
│                  REST API / WebSocket                   │
├─────────────────────────────────────────────────────────┤
│               Server (Spring Boot 4.1.0)                │
│          Spring Modulith · Modular Monolith             │
│  ┌──────────────┐  ┌────────────┐  ┌──────────────┐    │
│  │    Chess      │  │    Game    │  │     AI       │    │
│  │   Module      │  │   Module   │  │   Module     │    │
│  │ (Stockfish)   │  │  (Matches) │  │ (LLM/Talk)  │    │
│  └──────────────┘  └────────────┘  └──────────────┘    │
├─────────────────────────────────────────────────────────┤
│  Stockfish 17.1        PostgreSQL 17        LLM APIs   │
│  (Native Process)      (Docker / Neon)    (Phase 2)    │
└─────────────────────────────────────────────────────────┘
```

### Key Boundaries

| Layer | Responsibility |
|-------|---------------|
| **Stockfish** | Legal moves, position evaluation, candidate move generation. Never handles personality. |
| **Personality Layer** | Selects from Stockfish's candidate moves based on traits (aggression, risk, blunder chance). |
| **LLMs** | Entertainment only — trash talk, reactions, celebrations, commentary. Never decide chess moves. |

---

## 🛠️ Tech Stack

### Backend

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Language runtime |
| Spring Boot | 4.1.0 | Application framework |
| Spring Modulith | 2.1.0 | Modular monolith structure |
| PostgreSQL | 17 | Persistence (Docker locally, Neon in production) |
| Flyway | — | Database migrations |
| Stockfish | 17.1 | Chess engine (native executable, UCI protocol) |
| Lombok | — | Boilerplate reduction |
| GraalVM Native Image | — | Production compilation target |

### Frontend

| Technology | Version | Purpose |
|---|---|---|
| React | 19 | UI library |
| Vite | 8 | Build tool & dev server |
| TypeScript | ~6.0 | Type safety |
| Tailwind CSS | 4 | Utility-first styling |
| shadcn/ui | 4 | Radix-based UI components |
| Zustand | 5 | State management |
| React Router DOM | 7 | Client-side routing |
| chess.js | 1.4 | Client-side move validation |
| react-chessboard | 5 | Chessboard UI component |
| Axios | 1 | HTTP client |

> See [Tech Stack Document](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) for full version details and dependency tables.

---

## 📁 Project Structure

```
ai-chess-rivals/
├── client/                          # React frontend
│   └── src/
│       ├── components/              # Shared reusable UI components (shadcn/ui)
│       ├── features/                # Feature-specific UI (self-contained)
│       ├── pages/                   # Route-level page components
│       ├── hooks/                   # Custom React hooks
│       ├── store/                   # Zustand state stores
│       ├── services/                # API service layer (Axios)
│       ├── types/                   # TypeScript type definitions
│       ├── lib/                     # Utility functions (cn() helper, etc.)
│       └── assets/                  # Static assets
│
├── server/                          # Spring Boot backend
│   ├── src/main/java/dev/krishnamurti/ai_chess_rivals/
│   │   ├── config/                  # Cross-cutting config (GraalVM hints)
│   │   └── chess/                   # Chess module (Stockfish integration)
│   │       └── config/              # Module-specific configuration
│   ├── src/main/resources/
│   │   └── db/migration/            # Flyway migration scripts
│   ├── stockfish/                   # Stockfish binary (downloaded, not committed)
│   ├── docker-compose.yml           # Local PostgreSQL + backend
│   ├── Dockerfile                   # GraalVM native image build
│   └── pom.xml                      # Maven configuration
│
├── docs/                            # Project documentation
│   ├── AI Chess Rivals - Constitution.md
│   └── AI Chess Rivals - Tech Stack.md
│
├── AGENTS.md                        # AI agent guidelines
└── README.md                        # You are here
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 25 | [GraalVM CE](https://www.graalvm.org/) recommended for native image support |
| **Node.js** | 22+ | For the frontend dev server |
| **Docker Desktop** | Latest | For the PostgreSQL container |
| **Maven** | 3.9+ | Bundled via Maven Wrapper (`mvnw`) |

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/ai-chess-rivals.git
cd ai-chess-rivals
```

### 2. Start the Database

```bash
cd server
cp .env.example .env
docker compose up -d
```

This starts PostgreSQL on **port 5433** (mapped from container port 5432 to avoid conflicts with local PostgreSQL installations).

### 3. Download Stockfish & Start the Backend

```bash
# From server/ directory

# Windows
mvn package -Pwindows -DskipTests

# Linux / macOS
mvn package -Plinux -DskipTests
```

Then run the Spring Boot application:

```bash
# Windows
set STOCKFISH_PATH=stockfish/stockfish.exe
mvn spring-boot:run

# Linux / macOS
STOCKFISH_PATH=stockfish/stockfish mvn spring-boot:run
```

Or run directly from your IDE (IntelliJ recommended) with the `STOCKFISH_PATH` environment variable set.

The backend will be available at **`http://localhost:8082`**.

### 4. Start the Frontend

```bash
# From client/ directory
npm install
npm run dev
```

The frontend dev server will be available at **`http://localhost:5173`**.

---

## ⚙️ Configuration

### Port Mappings

To avoid conflicts with native services on Windows:

| Service | Container Port | Host Port |
|---------|---------------|-----------|
| PostgreSQL | 5432 | **5433** |
| Spring Boot | 8080 | **8082** |

### Environment Variables

All backend configuration is driven through environment variables. See [`.env.example`](server/.env.example) for the complete list:

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | `aichessrivals` | Database name |
| `POSTGRES_USER` | `postgres` | Database user |
| `POSTGRES_PASSWORD` | `secretpassword` | Database password |
| `STOCKFISH_PATH` | `stockfish/stockfish` | Path to Stockfish binary |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...` | JDBC connection string |

---

## 🧪 Development Workflow

### Database Migrations

- **Local**: `spring.jpa.hibernate.ddl-auto` defaults to `update` for rapid prototyping.
- **Production**: All schemas driven through **Flyway** migration scripts under `server/src/main/resources/db/migration/`.

### Upgrading Stockfish

1. Update `<stockfish.version>` in `server/pom.xml`
2. Re-run the download profile: `mvn generate-resources -Pwindows` (or `-Plinux`)
3. No other files need to change

### Useful Commands

| Command | Location | Description |
|---|---|---|
| `docker compose up -d` | `server/` | Start PostgreSQL |
| `docker compose down -v` | `server/` | Stop and reset database |
| `mvn spring-boot:run` | `server/` | Run backend |
| `npm run dev` | `client/` | Run frontend dev server |
| `mvn package -Pwindows` | `server/` | Build + download Stockfish (Windows) |
| `mvn package -Plinux` | `server/` | Build + download Stockfish (Linux) |

---

## 🏛️ Production Deployment

The backend compiles to a **GraalVM Native Image** for minimal startup time and memory footprint.

| Environment | Backend | Database | Stockfish |
|---|---|---|---|
| **Local** | Spring Boot (JVM) | Docker Compose (PostgreSQL 17) | Downloaded via Maven |
| **Production** | GraalVM Native Image on [Render](https://render.com) | [Neon](https://neon.tech) (serverless PostgreSQL) | Bundled in Docker image |

Production requires only configuration changes (environment variables) — no code or profile changes.

---

## 📄 Documentation

| Document | Description |
|---|---|
| [Constitution](docs/AI%20Chess%20Rivals%20-%20Constitution.md) | Project principles, north star, and decision framework |
| [Tech Stack](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) | Complete dependency inventory with versions |
| [AGENTS.md](AGENTS.md) | AI agent guidelines for contributing |
| [Server README](server/README.md) | Backend setup, Stockfish config, Docker workflow |

---

## 🤝 Contributing

This is a personal showcase project, but contributions and suggestions are welcome!

Before contributing, please read:

1. **[AGENTS.md](AGENTS.md)** — Guidelines for AI agents and contributors
2. **[Constitution](docs/AI%20Chess%20Rivals%20-%20Constitution.md)** — Project principles and decision framework

### Key Guidelines

- **Keep it simple** — the simplest solution that works is the best one
- **Entertainment over elegance** — prioritize viewer experience over architectural purity
- **Follow existing patterns** — consistency is more valuable than perfection
- **Ask before adding complexity** — new dependencies, abstractions, or packages require justification

---

## 📜 License

This project is for demonstration and portfolio purposes.

---

<div align="center">

**Built with ☕ Java, ⚛️ React, and 🤖 AI**

*The best solution is usually the simplest one that creates an entertaining AI chess experience.*

</div>
