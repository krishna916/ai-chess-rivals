# AI Chess Rivals Backend Docker Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up the production-ready Dockerfile, local docker-compose.yml environment, and corresponding configurations/documentation for the backend service.

**Architecture:** A multi-stage Docker build separating build dependencies (JDK) from run dependencies (JRE), running as a non-root user. Local development utilizes Docker Compose to link Postgres and the backend container, driven entirely by environment variables. Production runs on Render using Neon DB via environment variables.

**Tech Stack:** Java 25, Spring Boot 4.1, Postgres 17, Flyway, Maven, Docker, Docker Compose.

## Global Constraints
- Java 25 & Maven multi-stage build.
- No Spring profiles or platform-specific configuration changes.
- Switch between local db and Neon via environment variables only.
- Reuse existing Stockfish downloader profile (`-Plinux`).
- Single Dockerfile, single docker-compose.yml, single .env.example inside the `server/` directory.

---

### Task 1: Clean Up Redundant Dependency and File

**Files:**
- Modify: [pom.xml](file:///D:/projects/ai-chess-rivals/server/pom.xml)
- Delete: [compose.yaml](file:///D:/projects/ai-chess-rivals/server/compose.yaml)

- [ ] **Step 1: Modify `pom.xml` to remove `spring-boot-docker-compose` dependency**
  Target lines 87-92:
  ```xml
  		<dependency>
  			<groupId>org.springframework.boot</groupId>
  			<artifactId>spring-boot-docker-compose</artifactId>
  			<scope>runtime</scope>
  			<optional>true</optional>
  		</dependency>
  ```
  Action: Delete this dependency node entirely.

- [ ] **Step 2: Delete the old `compose.yaml` file**
  Delete file: `D:\projects\ai-chess-rivals\server\compose.yaml`

- [ ] **Step 3: Verify the application still compiles locally on host**
  Run command in `D:\projects\ai-chess-rivals\server`:
  `mvn clean compile`
  Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**
  Run:
  ```bash
  git add pom.xml
  git rm compose.yaml
  git commit -m "refactor: remove spring-boot-docker-compose dependency and compose.yaml"
  ```

---

### Task 2: Update Spring Boot Configuration

**Files:**
- Modify: [application.yaml](file:///D:/projects/ai-chess-rivals/server/src/main/resources/application.yaml)

- [ ] **Step 1: Modify `application.yaml` to read database settings from environment variables**
  Update the entire file content:
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

- [ ] **Step 2: Verify compile with updated config**
  Run:
  `mvn clean compile`
  Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**
  Run:
  ```bash
  git add src/main/resources/application.yaml
  git commit -m "config: map datasource, flyway, and jpa to env variables in application.yaml"
  ```

---

### Task 3: Create Multi-Stage Dockerfile

**Files:**
- Create: [Dockerfile](file:///D:/projects/ai-chess-rivals/server/Dockerfile)

- [ ] **Step 1: Write `Dockerfile` with builder and runtime stages**
  Create the file `D:\projects\ai-chess-rivals\server\Dockerfile` containing:
  ```dockerfile
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
  RUN useradd -ms /bin/bash spring && chown -R spring:spring /opt/app
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
  ```

- [ ] **Step 2: Commit**
  Run:
  ```bash
  git add Dockerfile
  git commit -m "feat: add production-ready multi-stage Dockerfile using Java 25"
  ```

---

### Task 4: Create Docker Compose Configuration

**Files:**
- Create: [docker-compose.yml](file:///D:/projects/ai-chess-rivals/server/docker-compose.yml)

- [ ] **Step 1: Create `docker-compose.yml` for local development**
  Create file `D:\projects\ai-chess-rivals\server\docker-compose.yml` containing:
  ```yaml
  services:
    postgres:
      image: postgres:17-alpine
      container_name: chess-postgres
      restart: unless-stopped
      environment:
        POSTGRES_DB: ${POSTGRES_DB}
        POSTGRES_USER: ${POSTGRES_USER}
        POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      ports:
        - "5432:5432"
      volumes:
        - postgres_data:/var/lib/postgresql/data
      healthcheck:
        test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
        interval: 5s
        timeout: 5s
        retries: 5

    backend:
      build: .
      container_name: chess-backend
      restart: unless-stopped
      ports:
        - "8082:8080"
      depends_on:
        postgres:
          condition: service_healthy
      environment:
        SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}
        SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME}
        SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
        SPRING_FLYWAY_URL: ${SPRING_FLYWAY_URL}
        SPRING_FLYWAY_USER: ${SPRING_FLYWAY_USER}
        SPRING_FLYWAY_PASSWORD: ${SPRING_FLYWAY_PASSWORD}
        STOCKFISH_PATH: stockfish/stockfish

  volumes:
    postgres_data:
  ```

- [ ] **Step 2: Commit**
  Run:
  ```bash
  git add docker-compose.yml
  git commit -m "feat: add local development docker-compose.yml with postgres healthcheck"
  ```

---

### Task 5: Create Environment Variables Example

**Files:**
- Create: [.env.example](file:///D:/projects/ai-chess-rivals/server/.env.example)

- [ ] **Step 1: Create `.env.example`**
  Create file `D:\projects\ai-chess-rivals\server\.env.example` containing:
  ```env
  # Local Development Database Configuration
  POSTGRES_DB=aichessrivals
  POSTGRES_USER=postgres
  POSTGRES_PASSWORD=secretpassword

  # Backend Connection Strings (pointing to postgres service within Docker network)
  SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/aichessrivals
  SPRING_DATASOURCE_USERNAME=postgres
  SPRING_DATASOURCE_PASSWORD=secretpassword

  # Flyway Migration URL and credentials
  SPRING_FLYWAY_URL=jdbc:postgresql://postgres:5432/aichessrivals
  SPRING_FLYWAY_USER=postgres
  SPRING_FLYWAY_PASSWORD=secretpassword
  ```

- [ ] **Step 2: Create a local `.env` file by copying the example**
  Copy `D:\projects\ai-chess-rivals\server\.env.example` to `D:\projects\ai-chess-rivals\server\.env`.

- [ ] **Step 3: Commit**
  Run:
  ```bash
  git add .env.example
  git commit -m "feat: add .env.example for local environment variables"
  ```

---

### Task 6: Update README

**Files:**
- Modify: [README.md](file:///D:/projects/ai-chess-rivals/server/README.md)

- [ ] **Step 1: Modify README.md to describe the Docker flow**
  Add Docker instructions to `README.md`.
  Replace lines 55-89 with the new running instructions, and add a Docker Section.
  Let's add the Docker section at the end of the file.

- [ ] **Step 2: Commit**
  Run:
  ```bash
  git add README.md
  git commit -m "docs: update README with Docker environment and execution instructions"
  ```

---

### Task 7: Verify Integration & Run Tests

**Files:**
- None (verification stage)

- [ ] **Step 1: Run `docker compose build`**
  Run command in `D:\projects\ai-chess-rivals\server`:
  `docker compose build`
  Expected: Successful image generation.

- [ ] **Step 2: Run `docker compose up`**
  Run command:
  `docker compose up -d`
  Expected: Both Postgres and the Backend containers start up, Postgres healthcheck succeeds, Flyway executes, and Spring Boot starts up successfully.

- [ ] **Step 3: Verify backend health endpoint**
  Send a GET request to `http://localhost:8082/actuator/health`.
  Expected: A JSON response with `"status": "UP"`.

- [ ] **Step 4: Shut down environment**
  Run:
  `docker compose down -v`
  Expected: Containers stopped and volumes cleaned.
