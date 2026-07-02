# Design Specification: GraalVM Native Image Migration

This design document outlines the strategy for migrating the backend application from a standard Java Virtual Machine (JVM) JAR build to a **GraalVM Native Image** while preserving the existing multi-stage Docker workflow and Stockfish integration.

## 1. Goal

To leverage GraalVM Ahead-Of-Time (AOT) compilation to produce a single native Linux binary. This reduces container image startup latency and memory footprint, while maintaining a fully functioning integration with the Stockfish chess engine process.

## 2. Requirements

*   **Builder Image**: Update the Dockerfile builder stage from OpenJDK (`eclipse-temurin:25-jdk-noble`) to the official GraalVM Native Image JDK 25 community image (`ghcr.io/graalvm/native-image-community:25`).
*   **Target Image**: Replace the JRE runtime image (`eclipse-temurin:25-jre-noble`) with a minimal Linux runtime image (`debian:bookworm-slim`).
*   **Native Packaging**: Build a native executable instead of a JAR, and copy/run it in the target runtime stage.
*   **Security & User Setup**: Retain the `spring` non-root user setup (UID 1001) in the runtime image.
*   **Stockfish**: Continue downloading Stockfish during the Maven packaging stage (under the `linux` profile) and copy it into the runtime image. Launch it via `ProcessBuilder` exactly as before.
*   **Health Check**: Retain the Docker health check using `curl` targeting `/actuator/health` on port `8081`.
*   **No Buildpacks**: Use the custom multi-stage Dockerfile rather than Buildpacks.

## 3. Detailed Architecture & Setup

```mermaid
graph TD
    subgraph Builder Stage (ghcr.io/graalvm/native-image-community:25)
        A[pom.xml + Wrapper] -->|Pre-fetch dependencies| B(Maven Cache)
        B -->|Copy src/| C(AOT Processing)
        C -->|Download Stockfish| D(stockfish/stockfish)
        C -->|native:compile| E(target/ai-chess-rivals)
    end
    subgraph Runtime Stage (debian:bookworm-slim)
        E -->|Copy Binary| F(ai-chess-rivals)
        D -->|Copy Binary| G(stockfish/stockfish)
        H[non-root user: spring] --> F
        H --> G
        I[curl] -->|Healthcheck| F
    end
```

### 3.1. Maven Configuration

The project already uses `spring-boot-starter-parent` v4.1.0 and has the `native-maven-plugin` added under `<build><plugins>`. The parent POM defines the `native` profile which automatically configures AOT processing and binds native compilation to the Maven lifecycle when `-Pnative` is enabled. No extra plugins are necessary.

### 3.2. Dockerfile Changes

The `Dockerfile` will be refactored as follows:

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

## 4. Native Image Compatibility

- **ProcessBuilder**: Launching external processes works out-of-the-box in GraalVM without custom reflection configurations.
- **Spring Modulith**: Modulith automatically handles AOT configuration in Spring Boot 3.x/4.x.
- **Flyway**: Native support is built into Flyway, and the standard database driver (PostgreSQL JDBC) includes native-image configurations.
- **Reflection / Serialization**: If any serialization or reflection warnings/errors appear during startup (e.g. JSON serialization for endpoints), we will register runtime hints using a custom `RuntimeHintsRegistrar` implementation class instead of manual JSON files.

## 5. Verification Plan

1.  **Build the Docker Image**:
    ```bash
    docker compose build backend
    ```
2.  **Verify Native Executable**:
    Ensure the docker build output indicates a native-image compilation phase and produces the `/opt/app/ai-chess-rivals` executable.
3.  **Run Containers**:
    ```bash
    docker compose up -d
    ```
4.  **Verify Logs and Startup**:
    Check `docker compose logs backend` to ensure the application starts up in milliseconds and reports successful Stockfish validation.
5.  **Verify Health Endpoint**:
    Query `http://localhost:8082/actuator/health` (port 8082 on the host maps to 8080 on the container, check if actuator port 8081 is internally functional).
