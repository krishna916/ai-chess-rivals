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
RUN ./mvnw clean package native:compile -B -DskipTests -Pnative -Plinux

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
