# Design Specification: Actuator Hardening, Docker Health Checks, and SKILL.md Refinement

This specification describes the security updates to the backend Actuator configuration, Docker container health checks, Hibernate's schema update behavior, and refinement of the `update-tech-stack` skill constraints.

## 1. Context and Goals
- **Hardening Actuator Endpoints**: Restrict details of the `/actuator/health` endpoint and move all management endpoints to a separate internal port (`8081`) to prevent exposing internal infrastructure details.
- **Hibernate Schema Mode**: Change the default fallback mode of Hibernate's schema generator from `update` to `validate` (or `none`) to ensure Flyway migrations remain the single source of truth.
- **Docker Health Checks**: Implement container-level health monitoring in the Dockerfile and docker-compose configurations.
- **Clean Documentation Workflow**: Trim atomic git-commit restrictions from the `update-tech-stack` skill to focus solely on documentation accuracy.
- **Stockfish Multi-arch build**: Skipped due to container environment consistency and lack of precompiled Linux arm64 binaries in the official Stockfish releases.

## 2. Proposed Changes

### 2.1. Backend Configurations (`server/src/main/resources/application.yaml`)
- **Actuator Security and Port**:
  Move management endpoints to port `8081` and change `show-details` to `when-authorized`.
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
- **Hibernate Config**:
  Update `spring.jpa.hibernate.ddl-auto` default to `validate`.
  ```yaml
  spring:
    jpa:
      hibernate:
        ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}
  ```

### 2.2. Docker Configurations (`server/Dockerfile` & `server/docker-compose.yml`)
- **Dockerfile Prefetch**:
  Add `-Plinux` profile to dependency download step to align build and prefetch phases.
  ```dockerfile
  RUN ./mvnw dependency:go-offline -B -Plinux
  ```
- **Dockerfile Runtime Healthcheck**:
  Install `curl` in `eclipse-temurin:25-jre-noble` and configure the container-level healthcheck on port `8081`:
  ```dockerfile
  RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* \
      && useradd -u 1001 -ms /bin/bash spring && chown -R spring:spring /opt/app
  
  EXPOSE 8080
  EXPOSE 8081
  
  HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1
  ```
- **Docker Compose Healthcheck**:
  Add readiness checks on backend container using port `8081` actuator health URL:
  ```yaml
      healthcheck:
        test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
        interval: 10s
        timeout: 5s
        retries: 3
        start_period: 20s
  ```

### 2.3. Skill Rule Refinement (`.agents/skills/update-tech-stack/SKILL.md`)
- Remove the strict git/staging and atomic commit restrictions (line 14, parts of lines 16-19, and the atomic commit rows in the Rationalization Table).

## 3. Verification Plan
1. **Actuator Port**: Verify that actuator endpoints are exposed on port `8081` and not port `8080`.
2. **Health endpoint security**: Query `http://localhost:8081/actuator/health` to confirm details are hidden.
3. **Container Healthcheck**: Run `docker compose up --build` and verify both postgres and backend containers reach a healthy state.
