# AGENTS.md

# AI Chess Rivals – AI Agent Guidelines

## Purpose

AI Chess Rivals is a hobby showcase project built to demonstrate practical AI engineering through an entertaining AI-vs-AI chess experience.

The goal is **not** to build the strongest chess engine or the most sophisticated architecture.

Optimize for:

- Simplicity
- Readability
- Fast iteration
- Low maintenance
- Entertaining AI interactions

When in doubt, choose the simpler solution.

---

# Project Philosophy

## Entertainment First

The personalities are the product.

Prioritize features that improve:

- Personality
- Trash talk
- Match narratives
- Emotional reactions
- Viewer enjoyment

Do not add complexity that only improves chess strength.

---

## AI Showcase First

This project exists to showcase practical AI engineering.

Favor implementations that demonstrate:

- LLM integration
- Prompt engineering
- AI-assisted workflows
- Personality design
- End-to-end product development

Avoid complexity whose only purpose is architectural elegance.

---

## Shipping Over Perfection

A working implementation is more valuable than a perfect implementation.

Small technical debt is acceptable if it helps ship features faster.

Avoid premature optimization.

---

# Simplicity Rules

Prefer:

- Straightforward code
- Explicit logic
- Small focused classes
- Constructor injection
- Composition over inheritance
- Clear naming

Avoid:

- Generic frameworks
- Plugin architectures
- Reflection-heavy solutions
- Unnecessary abstractions
- Premature optimization
- Over-engineering

If a simpler solution provides similar value, always choose it.

---

# Tech Stack

## Backend

| Technology               | Version / Detail                       |
|--------------------------|----------------------------------------|
| Java                     | 25                                     |
| Spring Boot              | 4.1.0                                  |
| Spring Modulith          | 2.1.0 (modular monolith)              |
| PostgreSQL               | 17 (Docker locally, Neon in prod)      |
| Flyway                   | DB migrations                          |
| Stockfish                | 17.1 (native executable, UCI protocol) |
| Lombok                   | Boilerplate reduction                  |
| GraalVM Native Image     | Production compilation target          |

## Frontend

| Technology               | Version / Detail                       |
|--------------------------|----------------------------------------|
| React                    | 19                                     |
| Vite                     | 8                                      |
| TypeScript               | ~6.0                                   |
| Tailwind CSS             | 4 (with `@tailwindcss/vite`)           |
| shadcn/ui                | 4 (Radix-based components)             |
| Zustand                  | 5 (state management)                   |
| React Router DOM         | 7 (routing)                            |
| chess.js                 | 1.4 (client-side move validation)      |
| react-chessboard         | 5 (board UI)                           |
| Axios                    | 1 (HTTP client)                        |

Refer to [Tech Stack Document](file:///D:/projects/ai-chess-rivals/docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) for full version details and dependency tables.

---

# Project Structure

Keep new code within the existing project structure.

Avoid introducing new top-level packages or folders unless there is a strong reason.

### Backend

```
server/src/main/java/dev/krishnamurti/ai_chess_rivals/
    config/          # Cross-cutting configuration (e.g., GraalVM hints)
    chess/           # Chess module (Stockfish integration)
        config/      # Module-specific config (e.g., Stockfish properties)
```

Spring Modulith enforces module boundaries at the top-level package level. Each top-level package under `ai_chess_rivals/` is a Modulith module.

### Frontend

```
client/src/
    components/      # Shared reusable UI components
    features/        # Feature-specific UI (self-contained)
    pages/           # Route-level page components
    hooks/           # Custom React hooks
    store/           # Zustand state stores
    services/        # API service layer
    types/           # TypeScript type definitions
    lib/             # Utility functions (e.g., cn() helper)
    assets/          # Static assets
```

Organize code by feature whenever practical.

---

# Backend Guidelines

Use a simple layered approach.

```
Controller
    ↓
Service
    ↓
Repository
```

Guidelines:

- Controllers should remain thin.
- Business logic belongs in services.
- Repositories should only access persistence.
- Keep services cohesive and focused.
- Prefer package-private classes unless wider visibility is required.
- Avoid creating interfaces unless multiple implementations are expected.
- Use constructor injection (Lombok's `@RequiredArgsConstructor` is acceptable).
- Use Jakarta Bean Validation for input validation.
- Use Spring Modulith's event system for cross-module communication rather than direct service calls.

### Database Migrations

- **Local development**: `spring.jpa.hibernate.ddl-auto` defaults to `update` for rapid prototyping.
- **Production**: All schemas must be driven through **Flyway migration scripts** under `server/src/main/resources/db/migration/`.
- Production environments must set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.

### Stockfish Integration

- Stockfish runs as a native process via `ProcessBuilder`, communicating over the UCI protocol (stdin/stdout).
- The `StockfishClient` manages the process lifecycle.
- Stockfish binary is downloaded at build time via Maven profiles (`-Pwindows` or `-Plinux`), not committed to git.
- Binary location: `server/stockfish/stockfish.exe` (Windows) or `server/stockfish/stockfish` (Linux).

---

# Frontend Guidelines

Keep the UI simple and feature-oriented.

Guidelines:

- Pages compose features.
- Features own their UI.
- Shared components should be reusable.
- Keep Zustand stores small and focused.
- Avoid deeply nested component trees.
- Prefer composition over prop drilling.
- Use shadcn/ui components as the base; customize via Tailwind CSS.
- Use `cn()` utility from `lib/utils` for conditional class merging.
- Type all props and state with TypeScript; avoid `any`.

---

# Chess Guidelines

Stockfish is the source of truth for chess.

Stockfish is responsible for:

- Legal move generation
- Position evaluation
- Candidate moves

Do not implement custom chess logic that duplicates Stockfish unless absolutely necessary.

`chess.js` on the client provides client-side move validation and board state tracking for the UI.

---

# AI Guidelines

LLMs never decide chess moves.

LLMs are responsible only for entertainment.

Examples include:

- Trash talk
- Reactions
- Celebrations
- Commentary
- Match narration
- Personality

Keep the chess layer independent from AI-specific logic.

The application should support configurable AI providers and models. The choice of provider (e.g. Gemini, OpenAI, Anthropic, OpenRouter) is an implementation detail.

---

# Phase Awareness

The project is implemented in phases.

## Phase 1

Focus on the chess foundation.

Do not introduce:

- LLM providers
- Prompt engineering
- Personalities
- Conversation history
- AI orchestration

## Phase 2

Add the entertainment layer on top of the completed chess foundation.

Keep these concerns separated.

---

# Explicitly Out of Scope

The MVP intentionally excludes:

- Complex multi-agent frameworks
- Long-term memory / reflection systems
- Autonomous planning
- Event-driven architectures (Kafka, etc.)
- Microservices / distributed systems
- Vector databases
- Redis
- User accounts / registration
- Multiplayer matches
- Plugin architectures
- Generic orchestration frameworks

Complexity should only be added when it directly improves the viewer experience.

---

# Local Development

## Running the Project

- **Backend**: Run Spring Boot directly from the IDE, or via `./mvnw spring-boot:run` from `server/`.
- **Frontend**: Run `npm run dev` from `client/` (Vite dev server).
- **Database**: Run `docker compose up -d` from `server/` to start PostgreSQL.
- **Stockfish**: Downloaded automatically by Maven during build. Ensure the correct profile is active (`-Pwindows` or `-Plinux`).

## Port Mappings

To avoid conflicts with native services on Windows:

| Service       | Container Port | Host Port |
|---------------|----------------|-----------|
| PostgreSQL    | 5432           | **5433**  |
| Spring Boot   | 8080           | **8082**  |

Do not use default ports (`5432`, `8080`) on the host.

Spring configuration fallback URLs (e.g., `spring.datasource.url`) must point to `localhost:5433`.

## Windows-Specific

- **PowerShell**: Do not use `&&` to chain commands. Use `;` or separate sequential commands.
- **Port exclusions**: If a service fails to bind, check Windows/Hyper-V excluded port ranges:
  ```powershell
  netsh int ipv4 show excludedportrange protocol=tcp
  ```
- **Dual-stack conflicts**: If connections fail despite Docker running, check for native services binding to IPv4 on the same port:
  ```powershell
  Get-NetTCPConnection -LocalPort <port-number>
  ```

## Spring Modulith Schema

When `ddl-auto` is set to `validate` or `none`, Modulith's internal `event_publication` table must be explicitly created via a Flyway migration script.

---

# Testing Guidelines

- Write tests for meaningful business logic and integration points.
- Do not over-test obvious getter/setter code.
- Backend tests should use Spring Boot's test slices where appropriate (`@WebMvcTest`, `@DataJpaTest`).
- Use Spring Modulith's `@ApplicationModuleTest` to verify module boundaries.
- Frontend tests are not required for the MVP but may be added for critical flows.
- Keep tests fast and focused. Avoid slow integration tests when a unit test suffices.

---

# Naming Conventions

## Backend

- **Packages**: `dev.krishnamurti.ai_chess_rivals.<module>` (lowercase, underscore-separated).
- **Classes**: PascalCase. Suffix with role: `*Controller`, `*Service`, `*Repository`, `*Config`, `*Client`.
- **Methods**: camelCase. Use descriptive verbs.
- **Constants**: UPPER_SNAKE_CASE.

## Frontend

- **Files**: PascalCase for components (`GameBoard.tsx`), camelCase for utilities/hooks (`useGameStore.ts`).
- **Components**: PascalCase function names matching the filename.
- **Hooks**: Prefix with `use` (`useGameStore`, `useWebSocket`).
- **Stores**: Named by domain (`gameStore.ts`, `chatStore.ts`).
- **Types**: PascalCase, co-located in `types/` or alongside the feature.

---

# Dependencies

Before introducing a new dependency, ask:

- Is it really necessary?
- Can the existing stack solve this?
- Does it simplify the code enough to justify another dependency?

Prefer using existing libraries already present in the project.

---

# Before Adding Complexity

Before introducing:

- a new abstraction
- a new package
- a new service
- a new dependency
- a new architectural pattern

Ask:

1. Does this improve the viewer experience?
2. Does this better showcase AI engineering?
3. Is there a significantly simpler implementation?

If the answer to (3) is yes, choose the simpler approach.

---

# Working with Existing Code

When implementing features:

- Follow existing patterns.
- Reuse existing utilities.
- Keep changes localized.
- Avoid unrelated refactoring.
- Do not rewrite working code without a clear benefit.

Consistency is more valuable than perfection.

---

# Documentation

When making meaningful architectural or behavioral changes:

- Update relevant documentation.
- Keep comments focused on explaining *why*, not *what*.
- Avoid obvious comments.

Key documentation locations:

- `docs/AI Chess Rivals - Constitution.md` — project principles and north star.
- `docs/AI Chess Rivals - Tech Stack.md` — full dependency inventory.
- `.agents/AGENTS.md` — local development rules and debugging procedures.
- `AGENTS.md` (this file) — agent guidelines.

---

# Pull Request Expectations

Changes should:

- Build successfully.
- Keep code readable.
- Minimize scope.
- Avoid unnecessary files.
- Avoid speculative abstractions.

---

# AI Agent Expectations

When implementing a task:

1. Understand the existing implementation before making changes.
2. Reuse existing patterns whenever possible.
3. Prefer modifying existing code over introducing new abstractions.
4. Explain important design decisions.
5. Ask before introducing major architectural changes or new dependencies.
6. Keep solutions pragmatic and aligned with the project's short-lived showcase nature.
7. Read the [Constitution](file:///D:/projects/ai-chess-rivals/docs/AI%20Chess%20Rivals%20-%20Constitution.md) for project principles.
8. Check the [Tech Stack](file:///D:/projects/ai-chess-rivals/docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) before adding any dependency.

Remember:

**This is a showcase project, not an enterprise platform.**

The best solution is usually the simplest one that creates an entertaining AI chess experience.
