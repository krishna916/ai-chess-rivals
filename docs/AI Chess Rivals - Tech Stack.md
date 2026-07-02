# AI Chess Rivals — Tech Stack Document
Version: 1.0
Status: Active

## Executive Summary
This document provides an inventory and explanation of all libraries, tools, and versions used in the **AI Chess Rivals** project. The project is split into a client-side frontend (`client/`) and a server-side backend (`server/`), supplemented by Docker-orchestrated infrastructure and native process executions.

| Layer | Key Technologies | Version / Environment |
|---|---|---|
| **Language & Runtime** | Java (GraalVM AOT), Node.js, TypeScript | Java 25, Node.js 22+, TypeScript ~6.0.2 |
| **Client-Side (Frontend)** | React, Vite, Tailwind CSS, shadcn/ui | React 19.2.7, Vite 8.1.0, Tailwind CSS 4.3.1 |
| **Server-Side (Backend)** | Spring Boot, Spring Modulith, Hibernate | Spring Boot 4.1.0 (GraalVM Native Image), Spring Modulith 2.1.0 |
| **Database** | PostgreSQL | PostgreSQL 17 (via Docker Compose / Neon in production) |
| **Chess Logic Engine** | Stockfish, chess.js | Stockfish 17.1 (native executable), chess.js 1.4.0 |

---

## Client-Side (Frontend) Stack
The frontend is built using **React** with **TypeScript** and bundled with **Vite**. The styling is managed via **Tailwind CSS v4** and components are scaffolded using **shadcn/ui**.

### Core Architecture & Build Tools
*   **React** (`v19.2.7`): The core library for rendering UI components.
*   **Vite** (`v8.1.0`): The fast build tool and development server using ES modules.
*   **TypeScript** (`~6.0.2`): Typed superset of JavaScript, configured with app/node compilation settings.

### User Interface & Styling
*   **Tailwind CSS** (`v4.3.1`): Utility-first CSS framework (fully integrated with Vite via `@tailwindcss/vite`).
*   **shadcn/ui** (`v4.12.0`): UI component builder, utilizing the `radix-nova` base style and CSS variables.
*   **Radix UI** (`v1.6.0`): Headless component primitives providing accessibility defaults.
*   **Lucide React** (`v1.22.0`): Clean and modern SVG icons library.
*   **Geist Font** (`^5.2.9`): Modern font family (Geist Sans Variable).

### State, Routing & API
*   **Zustand** (`v5.0.14`): Lightweight, hook-based state management.
*   **React Router DOM** (`v7.18.0`): Declarative routing for page navigation.
*   **Axios** (`v1.18.1`): Promise-based HTTP client to make backend API calls.

### Chess Mechanics
*   **chess.js** (`v1.4.0`): Chess rules library used to validate moves, generate legal moves, and track board state on the client side.
*   **react-chessboard** (`v5.10.0`): Customizable React chessboard UI component.

### Dependency Reference Table (`client/package.json`)

#### Production Dependencies
| Package | Version | Purpose |
|---|---|---|
| `react` | `^19.2.7` | UI library |
| `react-dom` | `^19.2.7` | DOM rendering for React |
| `react-router-dom` | `^7.18.0` | Frontend routing |
| `zustand` | `^5.0.14` | Global state management |
| `axios` | `^1.18.1` | HTTP API requests |
| `chess.js` | `^1.4.0` | Move validation and board state rules |
| `react-chessboard` | `^5.10.0` | Chessboard UI component |
| `radix-ui` | `^1.6.0` | Accessible headless component primitives |
| `shadcn` | `^4.12.0` | UI component generator / CLI |
| `@fontsource-variable/geist` | `^5.2.9` | Geist variable font |
| `lucide-react` | `^1.22.0` | Icon set for React |
| `class-variance-authority` | `^0.7.1` | CSS class variance helper |
| `clsx` | `^2.1.1` | Conditional class name helper |
| `tailwind-merge` | `^3.6.0` | Merge duplicate Tailwind classes |
| `tailwindcss-animate` | `^1.0.7` | Tailwind CSS animations plugin |
| `tw-animate-css` | `^1.4.0` | Animate.css integration with Tailwind |
| `dayjs` | `^1.11.21` | Date and time parsing/formatting |

#### DevDependencies
| Package | Version | Purpose |
|---|---|---|
| `typescript` | `~6.0.2` | TypeScript compiler |
| `vite` | `^8.1.0` | Dev server & bundle tool |
| `@tailwindcss/vite` | `^4.3.1` | Vite plugin for Tailwind CSS v4 support |
| `tailwindcss` | `^4.3.1` | Tailwind CSS framework |
| `@vitejs/plugin-react` | `^6.0.2` | React plugin for Vite |
| `eslint` | `^10.5.0` | Linter |
| `@eslint/js` | `^10.0.1` | Default ESLint JS rules |
| `eslint-plugin-react-hooks` | `^7.1.1` | React Hooks rules for ESLint |
| `eslint-plugin-react-refresh` | `^0.5.3` | React Fast Refresh ESLint rules |
| `typescript-eslint` | `^8.61.0` | ESLint support for TypeScript |
| `@types/node` | `^24.13.2` | Node type definitions |
| `@types/react` | `^19.2.17` | React type definitions |
| `@types/react-dom` | `^19.2.3` | React DOM type definitions |
| `globals` | `^17.6.0` | Environment globals definition |

---

## Server-Side (Backend) Stack
The backend is a **Spring Boot** application targeting **Java 25**, compiled to a **GraalVM Native Image** executable for production, and configured as a modular monolith using **Spring Modulith**.

### Framework Core
*   **Java** (`v25`): Modern LTS version of Java used for execution.
*   **Spring Boot Starter Parent** (`v4.1.0`): The base configuration establishing dependency versions across the project.
*   **Spring Modulith** (`v2.1.0`): Extends Spring Boot to enforce logical architecture boundaries, provide module validation, and support module-specific event publication.

### Web & API Communication
*   **Spring Boot Starter WebMVC**: Configures REST APIs and synchronous web endpoints.
*   **Spring Boot Starter WebSocket**: Handles real-time, bi-directional communication between client and server (crucial for streaming chess matches, live evaluations, and real-time trash talk).
*   **Spring Boot Starter RestClient**: Lightweight, synchronous client to make HTTP calls (useful for calling external LLM providers).

### Persistence & Database
*   **Spring Boot Starter Data JPA**: Database access layer powered by Spring Data and Hibernate ORM.
*   **Spring Boot Starter Flyway**: Runs database migrations automatically on application startup.
*   **Flyway Database PostgreSQL**: Compatibility layer for PostgreSQL database support in Flyway.
*   **PostgreSQL JDBC Driver** (`postgresql`): Runtime driver connecting Java to the PostgreSQL instance.

### Developer Tooling & Verification
*   **Lombok**: Reduces boilerplate code (e.g., automatically generating getters/setters, constructors, and builders via annotations).
*   **Spring Boot Actuator**: Exposes operational endpoints (health, environment, metrics) and works with Spring Modulith to expose module diagrams.
*   **Spring Boot DevTools**: Enables hot-swapping classes and automatically restarting the local dev server.

### Dependency Reference Table (`server/pom.xml`)

| Artifact ID | Group ID | Scope | Version | Description |
|---|---|---|---|---|
| `spring-boot-starter-actuator` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | Metrics, health checks and diagnostic endpoints |
| `spring-boot-starter-data-jpa` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | Persistence using Spring Data & Hibernate |
| `spring-boot-starter-flyway` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | DB migration engine starter |
| `spring-boot-starter-restclient` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | RestClient configuration utilities |
| `spring-boot-starter-validation` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | Jakarta Bean Validation integration |
| `spring-boot-starter-webmvc` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | REST API engine (Spring MVC) |
| `spring-boot-starter-websocket` | `org.springframework.boot` | Compile | Inherited (`4.1.0`) | Real-time websocket capabilities |
| `flyway-database-postgresql` | `org.flywaydb` | Compile | Inherited (`4.1.0`) | PostgreSQL-specific Flyway module |
| `spring-modulith-observability-api` | `org.springframework.modulith` | Compile | `2.1.0` | Architectural observability API |
| `spring-modulith-starter-core` | `org.springframework.modulith` | Compile | `2.1.0` | Core Modulith tooling and verification |
| `spring-modulith-starter-jpa` | `org.springframework.modulith` | Compile | `2.1.0` | Modulith persistence utilities |
| `spring-boot-devtools` | `org.springframework.boot` | Runtime (Opt) | Inherited (`4.1.0`) | Rapid development helpers / restart |
| `postgresql` | `org.postgresql` | Runtime | Inherited (`4.1.0`) | JDBC connector |
| `spring-modulith-actuator` | `org.springframework.modulith` | Runtime | `2.1.0` | Modulith metrics endpoints |
| `spring-modulith-observability-core` | `org.springframework.modulith` | Runtime | `2.1.0` | Modulith tracing / spans core engine |
| `spring-modulith-runtime` | `org.springframework.modulith` | Runtime | `2.1.0` | Enforces Modulith transactional event registry |
| `lombok` | `org.projectlombok` | Compile (Opt) | Inherited (`4.1.0`) | Boilerplate reducer annotations |
| `spring-boot-configuration-processor` | `org.springframework.boot` | Compile (Opt) | Inherited (`4.1.0`) | Metadata generator for configuration settings |

*Note: Test dependencies mirror their compile counterparts with `org.springframework.boot` or `org.springframework.modulith` grouping and are restricted to the `<scope>test</scope>` lifecycle.*

---

## Infrastructure, Externals & Automation
To support locally running developers and showcase targets, the codebase incorporates automation around local orchestration and binary acquisition.

### 1. PostgreSQL Database
Orchestrated locally via Docker Compose (`server/docker-compose.yml`) and connected via environment variables.
*   **Image**: `postgres:17-alpine`
*   **Port Mapping**: `5433:5432`
*   **Configured Database**: `aichessrivals`
*   **Developer Credentials**: `postgres` / `secretpassword`

### 2. Stockfish Chess Engine
Stockfish is used as a local executable process communicating over UCI (stdin/stdout) via the `StockfishClient`.
*   **Version Pin**: `17.1` (declared via `<stockfish.version>` in `pom.xml`)
*   **Binary Management**: Binaries are downloaded dynamically based on target build profiles rather than being committed directly to git:
    *   **Windows Profile (`-Pwindows`)**: Downloads `stockfish-windows-x86-64-avx2.zip`, extracts it, and moves the exe to `server/stockfish/stockfish.exe`.
    *   **Linux Profile (`-Plinux`)**: Downloads `stockfish-ubuntu-x86-64-avx2.tar`, extracts it, moves the binary to `server/stockfish/stockfish`, and applies execution permissions (`chmod 755`).
*   **Process Client**: The `StockfishClient` runs `ProcessBuilder` on this native executable, starting the process, sending UCI configuration, and verifying readiness via `isready`/`readyok` sequence.

---

## Configuration Discrepancies & Architecture Notes
During the codebase review, the following notes were compiled for reference:

> [!WARNING]
> **Java Version Inconsistency**
> The project's core constitution document (`docs/AI Chess Rivals - Constitution.md`) specifies **Java 21**, whereas the server build specification (`server/pom.xml`) uses **Java 25** (`<java.version>25</java.version>`). Make sure your development environments have JDK 25 installed to avoid compilation issues.

> [!NOTE]
> **Missing Chesslib Dependency**
> The Constitution document lists `Chesslib` under the server-side stack. However, there is no Maven dependency declared for `chesslib` in `pom.xml`, and no Java files in the codebase currently import `chesslib`. Chess logic is currently handled on the client side using `chess.js` and on the backend via the low-level `StockfishClient` executing UCI commands directly.
