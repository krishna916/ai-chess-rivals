# GraalVM Native Image Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the backend application build from a JVM JAR to a GraalVM Native Image while preserving the existing multi-stage Docker workflow and Stockfish integration.

**Architecture:** Refactor the custom multi-stage Dockerfile to use GraalVM Native Image JDK 25 community image as the builder stage and `debian:bookworm-slim` as the runtime stage. Compile using the standard Spring Boot `native` profile and copy the native executable directly into the lightweight runtime container.

**Tech Stack:** GraalVM JDK 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, PostgreSQL, Docker, Docker Compose, Debian Bookworm Slim, Stockfish 17.1.

## Global Constraints

*   Keep the existing multi-stage Docker build.
*   Continue downloading and bundling the Linux Stockfish binary during the Maven build.
*   Continue using the `linux` Maven profile.
*   Replace the JVM runtime image with a minimal Linux runtime image (`debian:bookworm-slim`).
*   Preserve the existing non-root user setup (`spring` user, UID 1001).
*   Preserve the existing health check.
*   Preserve the existing environment variables (`STOCKFISH_PATH`).
*   The application must continue to launch Stockfish via `ProcessBuilder`.
*   Do not introduce Buildpacks or `spring-boot:build-image`; continue using the existing custom Dockerfile.
*   Do not use `&&` to chain commands in PowerShell.

---

### Task 1: Update Dockerfile for GraalVM Native Image Build

**Files:**
- Modify: `server/Dockerfile`

**Interfaces:**
- Consumes: Maven `pom.xml`, Maven wrapper scripts (`mvnw`), and source directories under `server/`
- Produces: GraalVM native image build configuration and multi-stage Docker compilation process

- [ ] **Step 1: Replace Dockerfile content**
  Write the new multi-stage configuration in `server/Dockerfile` targeting `ghcr.io/graalvm/native-image-community:25` as the builder and `debian:bookworm-slim` as the runtime base.

  Replace the entire content of `server/Dockerfile` with:
  ```dockerfile
  # --- Builder Stage ---
  FROM ghcr.io/graalvm/native-image-community:25 AS builder
  WORKDIR /opt/app

  # Copy maven wrapper & dependencies definition
  COPY .mvn/ .mvn
  COPY mvnw pom.xml ./
  RUN chmod +x mvnw

  # Pre-fetch maven dependencies & native plugins to cache layer
  RUN ./mvnw dependency:go-offline -B -Plinux -Pnative

  # Copy source code
  COPY src/ ./src/

  # Package application as native image & download linux stockfish binary
  RUN ./mvnw clean package -B -DskipTests -Pnative -Plinux

  # --- Runtime Stage ---
  FROM debian:bookworm-slim
  WORKDIR /opt/app

  # Install curl (for health checks) and run as non-root user
  RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
      && useradd -u 1001 -ms /bin/bash spring && chown -R spring:spring /opt/app
  USER spring

  # Copy compiled native binary and Stockfish from builder stage
  COPY --chown=spring:spring --from=builder /opt/app/target/ai-chess-rivals ai-chess-rivals
  COPY --chown=spring:spring --from=builder /opt/app/stockfish/stockfish ./stockfish/stockfish

  # Expose ports
  EXPOSE 8080
  EXPOSE 8081

  # Default environment variables for stockfish
  ENV STOCKFISH_PATH=stockfish/stockfish

  # Health check using separate management port
  HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

  # Start the application using the native binary
  ENTRYPOINT ["./ai-chess-rivals"]
  ```

- [ ] **Step 2: Commit changes**
  ```bash
  git add server/Dockerfile
  git commit -m "chore: update Dockerfile for GraalVM native image build with Debian runtime"
  ```

---

### Task 2: Build and Validate the Native Container Execution

**Files:**
- Test/Verify: `server/docker-compose.yml`, `server/Dockerfile`

- [ ] **Step 1: Clear current containers and database volumes**
  Run: `docker compose down -v`
  Expected: Containers stopped and volumes removed.

- [ ] **Step 2: Build the backend container image**
  Run: `docker compose build backend`
  Expected: Successful compilation of the Spring Boot application, download of Stockfish Linux tar, GraalVM AOT compilation phase running, and creation of `chess-backend` image.

- [ ] **Step 3: Launch backend and postgres services**
  Run: `docker compose up -d`
  Expected: Containers start in daemon mode.

- [ ] **Step 4: Verify startup and Stockfish validation in logs**
  Run: `docker compose logs backend`
  Expected: Startup in milliseconds (typical for Native Image), database connection established, Flyway migrations run, and Stockfish startup validation succeeds with log matching:
  `=== Stockfish Startup Validation Successful ===`

- [ ] **Step 5: Query container Actuator health status**
  Run: `docker exec chess-backend curl -f http://localhost:8081/actuator/health`
  Expected: HTTP 200 response with JSON containing `"status":"UP"`.

- [ ] **Step 6: Check container health check status in Docker**
  Run: `docker ps --filter name=chess-backend`
  Expected: The output status shows `(healthy)`.
