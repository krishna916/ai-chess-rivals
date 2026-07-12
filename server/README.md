# AI Chess Rivals — Server

Spring Boot backend for the AI Chess Rivals application.

---

## Stockfish Engine Setup

### Why Stockfish binaries are not in the repository

Binary executables change between OS/CPU variants, grow large quickly, and pollute
`git log`. Committing them would force every developer to download a binary they may
never use. Instead, the correct binary is **downloaded on demand** during the Maven
`generate-resources` phase via the `download-maven-plugin`.

---

### Downloading the executable

Run the appropriate Maven profile from the `server/` directory.

**Windows**

```bash
mvn package -Pwindows
```

Downloads `stockfish-windows-x86-64-avx2.zip` from the official Stockfish GitHub
release, extracts it, and renames the executable to:

```
server/stockfish/stockfish.exe
```

**Linux (Render / Railway / Ubuntu)**

```bash
mvn package -Plinux
```

Downloads `stockfish-ubuntu-x86-64-avx2.tar` from the official Stockfish GitHub
release, extracts it, renames the binary to `stockfish`, and sets the executable
bit (`chmod 755`):

```
server/stockfish/stockfish
```

> **Source**: All downloads come from the official Stockfish GitHub repository:
> `https://github.com/official-stockfish/Stockfish/releases`
> No third-party mirrors are used.

---

### Running the application

The application reads the Stockfish path from the `STOCKFISH_PATH` environment
variable (or falls back to `stockfish/stockfish`).

**Windows**

```bash
set STOCKFISH_PATH=stockfish/stockfish.exe
mvn spring-boot:run
```

Or in `application.yaml` (already pre-configured):

```yaml
app:
  chess:
    stockfish:
      path: ${STOCKFISH_PATH:stockfish/stockfish}
```

Set `STOCKFISH_PATH=stockfish/stockfish.exe` in your IDE's run configuration or
your deployment environment variables.

**Linux**

```bash
STOCKFISH_PATH=stockfish/stockfish mvn spring-boot:run
```

The application contains **no OS detection** — it simply executes whatever path
is configured.

---

## Match pacing

The backend waits for a random duration after each non-terminal move has been
broadcast and before it requests the next move. Configure the inclusive range
with `GAME_MOVE_DELAY_MIN` and `GAME_MOVE_DELAY_MAX` (defaults: `3s` and `10s`).
Set both to `0s` for fast local runs and integration-style verification.
Stopping a match interrupts an active wait; the latest in-progress position
remains available for the existing resume flow.

---

### Upgrading Stockfish

1. Open `server/pom.xml`.
2. Find the `<stockfish.version>` property:

   ```xml
   <stockfish.version>17.1</stockfish.version>
   ```

3. Change it to the new version tag (e.g. `18`). The tag format is `sf_<version>`.
   Verify the tag exists at:
   `https://github.com/official-stockfish/Stockfish/releases`

4. Re-run the download profile:

   ```bash
   # Windows
   mvn generate-resources -Pwindows

   # Linux
   mvn generate-resources -Plinux
   ```

No other files need to change.

---

### Troubleshooting

#### `IllegalStateException: Stockfish executable not found at: …`

The binary has not been downloaded yet. Run:

```bash
# Windows
mvn generate-resources -Pwindows

# Linux
mvn generate-resources -Plinux
```

Check that `server/stockfish/stockfish.exe` (Windows) or `server/stockfish/stockfish`
(Linux) exists after the download.

#### `Stockfish file exists but is not executable`

On Linux, the `chmod` step in the Maven profile should handle this automatically.
If you are seeing this error, run manually:

```bash
chmod +x server/stockfish/stockfish
```

#### Download fails on CI/CD (Render / Railway)

Add the profile to your build command in the platform's build settings:

```bash
mvn package -Plinux
```

Set the environment variable in the platform dashboard:

```
STOCKFISH_PATH=stockfish/stockfish
```

#### Wrong binary for the platform

Do not mix profiles. If you previously ran `-Pwindows` and are now on Linux,
delete the contents of `server/stockfish/` (except `.gitkeep`) and re-run
`mvn generate-resources -Plinux`.

---

## Docker Compose Development Workflow

The recommended development workflow utilizes Docker Compose to orchestrate both the backend application and the PostgreSQL database.

### 1. Prerequisites

- **Docker Desktop** (with Docker Compose v2+) installed and running.

### 2. Configuration Setup

Before launching, copy the `.env.example` template into a `.env` file in the same directory:
```bash
cp .env.example .env
```
Ensure the environment variables are set correctly for your local development environment.

### 3. Build & Run

**Build the image:**
```bash
docker compose build
```

**Start services in background:**
```bash
docker compose up -d
```

**Stop services:**
```bash
docker compose down
```

**Stop services and destroy database volumes (starts with a fresh database):**
```bash
docker compose down -v
```

### 4. Monitoring & Diagnostics

**View all logs:**
```bash
docker compose logs -f
```

**View only backend logs:**
```bash
docker compose logs -f backend
```

**Rebuild the backend container and restart services:**
```bash
docker compose up -d --build
```

### 5. Connecting to the Database

Connect to the PostgreSQL instance using any database client (such as DBeaver, pgAdmin, or IntelliJ Database Tools):
- **Host**: `localhost`
- **Port**: `5432` (mapped from standard container port)
- **Database**: `aichessrivals` (or value of `POSTGRES_DB` in `.env`)
- **Username**: `postgres` (or value of `POSTGRES_USER` in `.env`)
- **Password**: `secretpassword` (or value of `POSTGRES_PASSWORD` in `.env`)

---

## Production Deployment Differences

### Environment Differences
- **Local Development**:
  - The PostgreSQL database runs as a container (`postgres:17-alpine`) inside the same Docker Compose network.
  - Flyway migrations are run against the local container database.
- **Production (Render + Neon)**:
  - The backend runs as a single Docker container on **Render** (built using the same `Dockerfile`).
  - The database is hosted on **Neon PostgreSQL** as a serverless instance.
  - The PostgreSQL service container is **not** deployed to Render.
  - Transitioning from local development to production requires **only configuration changes** (no code changes or Spring profile changes).

### Production Configuration
In Render, set the following environment variables in your web service dashboard to point to Neon:
- `SPRING_DATASOURCE_URL`: (Your Neon JDBC connection string)
- `SPRING_DATASOURCE_USERNAME`: (Your Neon database username)
- `SPRING_DATASOURCE_PASSWORD`: (Your Neon database password)
- `SPRING_FLYWAY_URL`: (Same as `SPRING_DATASOURCE_URL`)
- `SPRING_FLYWAY_USER`: (Same as `SPRING_DATASOURCE_USERNAME`)
- `SPRING_FLYWAY_PASSWORD`: (Same as `SPRING_DATASOURCE_PASSWORD`)

