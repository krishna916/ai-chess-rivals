# Actuator Hardening and Docker Health Checks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Secure the backend Spring Boot application by locking down Actuator endpoints, moving them to a separate internal port, adding Docker-level health checks, and removing strict git constraints from the update-tech-stack skill.

**Architecture:** Moving actuator endpoints from port 8080 to an internal-only port 8081 and restricting detail views. Modifying the Dockerfile to install curl, configure runtime healthcheck, and update compose configs. Trimming update-tech-stack skill file.

**Tech Stack:** Spring Boot 4.x, Docker, Docker Compose, Maven.

## Global Constraints
- Do not modify database schemas except via Flyway migrations.
- Set `spring.jpa.hibernate.ddl-auto` fallback to `validate` (non-mutating).

---

### Task 1: Update Application Configurations

**Files:**
- Modify: `server/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: Existing port settings.
- Produces: Hardened Actuator endpoints on port 8081 and non-mutating Hibernate ddl-auto fallback.

- [ ] **Step 1: Update application.yaml with hardened configurations**
  Apply the changes to change `ddl-auto` to `validate`, set the management port to `8081`, and `show-details` to `when-authorized`.
  ```yaml
  spring:
    jpa:
      hibernate:
        ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}
  ```
  ```yaml
  management:
    server:
      port: 8081
    endpoints:
      web:
        exposure:
          include: health,info,prometheus
    endpoint:
      health:
        show-details: when-authorized
  ```

- [ ] **Step 2: Verify changes via dry-run or inspection**
  Check that the syntax is valid YAML.
  Expected: Valid YAML syntax.

- [ ] **Step 3: Commit**
  ```bash
  git add server/src/main/resources/application.yaml
  git commit -m "config: harden actuator endpoints and configure hibernate validate mode"
  ```

---

### Task 2: Refine Update Tech Stack Skill

**Files:**
- Modify: `.agents/skills/update-tech-stack/SKILL.md`

**Interfaces:**
- Consumes: Current SKILL.md.
- Produces: Simplified SKILL.md without staging/commit atomic rules.

- [ ] **Step 1: Trim staging/commit rules from SKILL.md**
  Remove the statement: `Both changes must be staged and committed together.`
  Remove the Loophole Controls: `No Deferral` and `Atomic Updates`.
  Remove the commit/staging row from the Rationalization Table.

- [ ] **Step 2: Verify SKILL.md formatting**
  Verify the markdown remains clean and valid.

- [ ] **Step 3: Commit**
  ```bash
  git add .agents/skills/update-tech-stack/SKILL.md
  git commit -m "docs: remove staging/commit rules from update-tech-stack skill"
  ```

---

### Task 3: Hardening Dockerfile and Compose

**Files:**
- Modify: `server/Dockerfile`
- Modify: `server/docker-compose.yml`

**Interfaces:**
- Consumes: Actuator port `8081`.
- Produces: Docker containers running curl-based healthchecks.

- [ ] **Step 1: Update server/Dockerfile prefetch and runtime steps**
  Modify line 10 to include `-Plinux`:
  ```dockerfile
  RUN ./mvnw dependency:go-offline -B -Plinux
  ```
  Modify the runtime stage to install curl, expose port 8081, and configure HEALTHCHECK:
  ```dockerfile
  FROM eclipse-temurin:25-jre-noble
  WORKDIR /opt/app

  RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* \
      && useradd -u 1001 -ms /bin/bash spring && chown -R spring:spring /opt/app
  USER spring

  COPY --chown=spring:spring --from=builder /opt/app/target/*.jar app.jar
  COPY --chown=spring:spring --from=builder /opt/app/stockfish/stockfish ./stockfish/stockfish

  EXPOSE 8080
  EXPOSE 8081

  ENV STOCKFISH_PATH=stockfish/stockfish

  HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```

- [ ] **Step 2: Update server/docker-compose.yml with backend health check**
  Add the healthcheck to `backend` service:
  ```yaml
      healthcheck:
        test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
        interval: 10s
        timeout: 5s
        retries: 3
        start_period: 20s
  ```

- [ ] **Step 3: Commit Docker configuration changes**
  ```bash
  git add server/Dockerfile server/docker-compose.yml
  git commit -m "docker: add curl-based healthchecks and update dependency prefetch profile"
  ```
