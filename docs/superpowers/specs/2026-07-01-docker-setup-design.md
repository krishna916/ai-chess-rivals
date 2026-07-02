# Design Document: Docker Infrastructure for AI Chess Rivals Backend

This document outlines the design and plan for setting up the Docker infrastructure of the AI Chess Rivals backend. The design focuses on simplicity, fast local development, consistency between local/prod environments, and Render-compliant deployment.

## 1. Multi-Stage Dockerfile (`server/Dockerfile`)

A multi-stage build will separate the Maven compilation environment (JDK) from the minimal execution environment (JRE), ensuring a clean, lightweight, and secure runtime image.

### Builder Stage
- **Base Image**: `eclipse-temurin:25-jdk-noble` (JDK 25 on Ubuntu 24.04).
- **Caching**:
  - Copies Maven wrapper files (`mvnw`, `mvnw.cmd`, `.mvn/`) and dependency file (`pom.xml`).
  - Runs `./mvnw dependency:go-offline -B` to download and cache dependency layers.
- **Build**:
  - Copies source code (`src/`).
  - Executes `./mvnw clean package -B -DskipTests -Plinux` to build the application and download the Linux Stockfish binary to `stockfish/stockfish`.

### Runtime Stage
- **Base Image**: `eclipse-temurin:25-jre-noble` (JRE 25 on Ubuntu 24.04).
- **Security**:
  - Runs as a dedicated non-root user `spring` (UID 1000).
- **Files**:
  - Copies the built JAR file from the builder stage as `/home/spring/app.jar`.
  - Copies the downloaded Stockfish Linux executable as `/home/spring/stockfish/stockfish`.
- **Environment**:
  - Exposes port `8080`.
  - Sets `STOCKFISH_PATH=/home/spring/stockfish/stockfish` by default.
- **Execution**:
  - Starts the application using `ENTRYPOINT ["java", "-jar", "app.jar"]`.

---

## 2. Docker Compose (`server/docker-compose.yml`)

The compose file is strictly for local development and sets up a database container alongside the backend container.

### Services

#### `postgres`
- **Image**: `postgres:17-alpine` (standard stable Postgres image).
- **Volume**: Named volume `postgres_data` mapping to `/var/lib/postgresql/data` for persistence.
- **Credentials**: Configured dynamically using environment variables (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).
- **Healthcheck**:
  ```yaml
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
    interval: 5s
    timeout: 5s
    retries: 5
  ```

#### `backend`
- **Build**: Uses the multi-stage `Dockerfile` in the context `.` (the `server` directory).
- **Dependency**: Depends on the `postgres` healthcheck to guarantee database availability.
- **Environment**: Pass credentials and JDBC connection URLs dynamically.
- **Restart**: `restart: unless-stopped` to match production resilience.
- **Port Mapping**: `8082:8080`.

---

## 3. Environment Variables (`server/.env.example`)

Defines local defaults for database configuration, ensuring zero secrets are committed. Production setup simply replaces these environment variables in Render's configuration.

```env
# Postgres credentials for container initialization
POSTGRES_DB=aichessrivals
POSTGRES_USER=postgres
POSTGRES_PASSWORD=secretpassword

# Spring Boot datasource URL and credentials
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/aichessrivals
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=secretpassword

# Spring Boot Flyway URL and credentials
SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/aichessrivals
SPRING_FLYWAY_USER=postgres
SPRING_FLYWAY_PASSWORD=secretpassword
```

---

## 4. Spring Application Configuration (`server/src/main/resources/application.yaml`)

We will configure properties to ensure all database connections dynamically read from environment variables, keeping `application.yaml` minimal and clean.

```yaml
spring:
  application:
    name: ai-chess-rivals
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/aichessrivals}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:secretpassword}
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    url: ${SPRING_FLYWAY_URL:${spring.datasource.url}}
    user: ${SPRING_FLYWAY_USER:${spring.datasource.username}}
    password: ${SPRING_FLYWAY_PASSWORD:${spring.datasource.password}}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always

app:
  chess:
    stockfish:
      path: ${STOCKFISH_PATH:stockfish/stockfish}
```

---

## 5. spring-boot-docker-compose Dependency Removal

- **Action**: Delete `spring-boot-docker-compose` from `pom.xml` and delete `server/compose.yaml`.
- **Reasoning**:
  1. The project-wide docker compose workflow will launch both Postgres and the backend. The automatic connection generation of `spring-boot-docker-compose` is redundant and can cause startup loop issues inside a containerized runtime environment.
  2. Eliminating the dependency enforces configuration via standard environment variables, which aligns perfectly with Render's production architecture.
