# --- Builder Stage ---
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /opt/app

# Copy maven wrapper & dependencies definition
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Pre-fetch maven dependencies to cache layer
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ ./src/

# Package application & download linux stockfish binary
RUN ./mvnw clean package -B -DskipTests -Plinux

# --- Runtime Stage ---
FROM eclipse-temurin:25-jre-noble
WORKDIR /opt/app

# Run as non-root user
RUN useradd -u 1001 -ms /bin/bash spring && chown -R spring:spring /opt/app
USER spring

# Copy compiled artifacts from builder stage
COPY --chown=spring:spring --from=builder /opt/app/target/*.jar app.jar
COPY --chown=spring:spring --from=builder /opt/app/stockfish/stockfish ./stockfish/stockfish

# Expose port
EXPOSE 8080

# Default environment variables for stockfish
ENV STOCKFISH_PATH=stockfish/stockfish

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
