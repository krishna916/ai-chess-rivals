# Personality Persistence and Read-Only Roster API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the minimal PostgreSQL-backed personality persistence model and a stable, read-only API that lists active system personalities without exposing prompt-only fields or introducing personality authoring.

**Architecture:** Keep personality persistence inside the existing `ai` Spring Modulith module as one focused `ai/personality` feature package rather than creating another top-level module or generic personality framework. Flyway owns the schema; Spring Data JPA reads records in stable display order; a small service filters to active system personalities and maps them to a four-field roster read model; one GET-only controller exposes that read model at `/api/v1/personalities`.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Data JPA/Hibernate, Spring WebMVC, Spring Modulith 2.1.0, PostgreSQL 17, Flyway, JUnit 5, Mockito, AssertJ, MockMvc, existing Spotless/Error Prone/SpotBugs verification.

## Source of Truth

- Issue: `#40 Phase 2: Add personality persistence and read-only roster API`
- Parent epic: `#4 Phase 2: AI Personality Layer with Spring AI`
- Approved design: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Governing rules: `docs/AI Chess Rivals - Constitution.md`
- Phase strategy: `docs/AI Chess Rivals - Implementation Strategy.md`
- Agent guidance: `AGENTS.md` and `.agents/AGENTS.md`
- Verification workflow: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Keep this work inside the existing `ai` module. Do not create a new top-level Spring Modulith module.
- Do not add a new Maven dependency. Spring Data JPA, Flyway, PostgreSQL, WebMVC, and test tooling already exist.
- Do not add Spring AI calls, prompts, dialogue generation, provider logic, or Stockfish changes in this issue.
- Do not seed the four production character definitions in this issue. Character seed data belongs to issue `#41`.
- Do not add personality create/update/delete endpoints, admin endpoints, authoring UI, ownership, sharing, moderation, versioning, publishing, or user-account fields.
- System personalities are read-only through the application. The only HTTP mapping added by this issue is `GET /api/v1/personalities`.
- The persistence schema must contain exactly the Phase 2 data needed now: stable key, display name, description, prompt traits, speaking probability, style guidance, boundary guidance, avatar reference, display order, system flag, and active flag, plus a generated database ID.
- Use `personality_key` as the stable business identifier. Do not expose the generated database ID in the roster API.
- Stable roster ordering is `display_order ASC, personality_key ASC`.
- A selectable roster record must satisfy both `is_system = true` and `is_active = true`.
- The public roster JSON contains only `key`, `displayName`, `description`, and `avatarRef`.
- Do not expose `id`, `promptTraits`, `speakingProbability`, `styleGuidance`, `boundaryGuidance`, `displayOrder`, `system`, or `active` in the roster response.
- Keep `avatar_ref` nullable so #41 can use a simple placeholder/reference without forcing an asset pipeline.
- Keep the schema extensible only by omission: future user-created personalities may reuse it, but do not add speculative future columns now.
- Follow the repository's existing constructor-injection style and existing `@WebMvcTest`/`@MockitoBean` controller-test pattern.
- Apply backend formatting before verification.

## File Map

**Create:**

- `server/src/main/resources/db/migration/V2__create_personality.sql` — minimal PostgreSQL personality table, constraints, and selectable-roster ordering index.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityEntity.java` — package-local JPA mapping for all persistence fields.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java` — package-local Spring Data repository returning rows in deterministic display order.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRosterItem.java` — public four-field roster read model and JSON shape.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java` — package-local read service that filters to active system personalities and maps them to roster items.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java` — GET-only `/api/v1/personalities` endpoint.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java` — filtering, ordering-preservation, mapping, and empty-roster coverage.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java` — API shape, empty roster, and read-only HTTP coverage.

**Modify:** none expected.

**Explicitly do not modify for #40:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/package-info.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/**`
- `server/src/main/resources/application.yaml`
- `server/pom.xml`
- `client/**`

---

### Task 1: Add the Personality Persistence Model and Read Service

**Files:**
- Create: `server/src/main/resources/db/migration/V2__create_personality.sql`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityEntity.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRosterItem.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java`

**Interfaces:**
- Produces: `List<PersonalityEntity> PersonalityRepository.findAllByOrderByDisplayOrderAscPersonalityKeyAsc()`.
- Produces: `List<PersonalityRosterItem> PersonalityService.listSelectable()`.
- Produces: `public record PersonalityRosterItem(String key, String displayName, String description, String avatarRef)`.
- Selection rule: `PersonalityEntity.selectableSystem()` returns `true` only when both system and active flags are true.
- Ordering rule: the repository orders by `displayOrder` ascending and then `personalityKey` ascending; the service preserves that order while filtering.

- [ ] **Step 1: Write the failing service tests**

Create `PersonalityServiceTest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalityServiceTest {

  @Mock private PersonalityRepository personalityRepository;

  @Test
  void listsOnlyActiveSystemPersonalitiesInRepositoryOrder() {
    PersonalityService service = new PersonalityService(personalityRepository);
    when(personalityRepository.findAllByOrderByDisplayOrderAscPersonalityKeyAsc())
        .thenReturn(
            List.of(
                personality("archived", "Archived", 5, true, false),
                personality("custom", "Custom", 10, false, true),
                personality("alpha", "Alpha", 20, true, true),
                personality("zeta", "Zeta", 20, true, true)));

    assertThat(service.listSelectable())
        .containsExactly(
            new PersonalityRosterItem(
                "alpha", "Alpha", "Alpha description", "/avatars/alpha.svg"),
            new PersonalityRosterItem(
                "zeta", "Zeta", "Zeta description", "/avatars/zeta.svg"));

    verify(personalityRepository).findAllByOrderByDisplayOrderAscPersonalityKeyAsc();
  }

  @Test
  void returnsEmptyRosterWhenRepositoryHasNoRecords() {
    PersonalityService service = new PersonalityService(personalityRepository);
    when(personalityRepository.findAllByOrderByDisplayOrderAscPersonalityKeyAsc())
        .thenReturn(List.of());

    assertThat(service.listSelectable()).isEmpty();
  }

  private static PersonalityEntity personality(
      String key, String displayName, int displayOrder, boolean system, boolean active) {
    return new PersonalityEntity(
        key,
        displayName,
        displayName + " description",
        "Competitive, concise, and character-specific prompt traits.",
        new BigDecimal("0.650"),
        "Dry, confident speaking style.",
        "Keep banter PG-13; no slurs, sexual content, threats, or targeted abuse.",
        "/avatars/" + key + ".svg",
        displayOrder,
        system,
        active);
  }
}
```

This test intentionally places inactive and non-system records in the repository result so the service owns the selectable-roster rule while the repository owns deterministic ordering.

- [ ] **Step 2: Run the service test and verify it fails before implementation**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest test
```

Expected: compilation fails because the personality persistence/service types do not exist yet.

- [ ] **Step 3: Add the Flyway migration**

Create `server/src/main/resources/db/migration/V2__create_personality.sql` with exactly:

```sql
CREATE TABLE personality (
    id bigint GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    personality_key varchar(64) NOT NULL,
    display_name varchar(80) NOT NULL,
    description varchar(280) NOT NULL,
    prompt_traits text NOT NULL,
    speaking_probability numeric(4, 3) NOT NULL,
    style_guidance text NOT NULL,
    boundary_guidance text NOT NULL,
    avatar_ref varchar(255),
    display_order integer NOT NULL,
    is_system boolean NOT NULL DEFAULT true,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT personality_pkey PRIMARY KEY (id),
    CONSTRAINT personality_key_unique UNIQUE (personality_key),
    CONSTRAINT personality_speaking_probability_check
        CHECK (speaking_probability >= 0.000 AND speaking_probability <= 1.000),
    CONSTRAINT personality_display_order_check CHECK (display_order >= 0)
);

CREATE INDEX personality_selectable_order_idx
    ON personality (display_order, personality_key)
    WHERE is_system = true AND is_active = true;
```

Do not insert any rows in this migration. Issue #41 owns the four system-character definitions and seed data.

- [ ] **Step 4: Add the JPA entity**

Create `PersonalityEntity.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "personality")
class PersonalityEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "personality_key", nullable = false, unique = true, length = 64)
  private String personalityKey;

  @Column(name = "display_name", nullable = false, length = 80)
  private String displayName;

  @Column(nullable = false, length = 280)
  private String description;

  @Column(name = "prompt_traits", nullable = false, columnDefinition = "text")
  private String promptTraits;

  @Column(name = "speaking_probability", nullable = false, precision = 4, scale = 3)
  private BigDecimal speakingProbability;

  @Column(name = "style_guidance", nullable = false, columnDefinition = "text")
  private String styleGuidance;

  @Column(name = "boundary_guidance", nullable = false, columnDefinition = "text")
  private String boundaryGuidance;

  @Column(name = "avatar_ref", length = 255)
  private String avatarRef;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  protected PersonalityEntity() {
    this.id = null;
  }

  PersonalityEntity(
      String personalityKey,
      String displayName,
      String description,
      String promptTraits,
      BigDecimal speakingProbability,
      String styleGuidance,
      String boundaryGuidance,
      String avatarRef,
      int displayOrder,
      boolean system,
      boolean active) {
    this.id = null;
    this.personalityKey = personalityKey;
    this.displayName = displayName;
    this.description = description;
    this.promptTraits = promptTraits;
    this.speakingProbability = speakingProbability;
    this.styleGuidance = styleGuidance;
    this.boundaryGuidance = boundaryGuidance;
    this.avatarRef = avatarRef;
    this.displayOrder = displayOrder;
    this.system = system;
    this.active = active;
  }

  Long id() {
    return id;
  }

  String personalityKey() {
    return personalityKey;
  }

  String displayName() {
    return displayName;
  }

  String description() {
    return description;
  }

  String promptTraits() {
    return promptTraits;
  }

  BigDecimal speakingProbability() {
    return speakingProbability;
  }

  String styleGuidance() {
    return styleGuidance;
  }

  String boundaryGuidance() {
    return boundaryGuidance;
  }

  String avatarRef() {
    return avatarRef;
  }

  int displayOrder() {
    return displayOrder;
  }

  boolean system() {
    return system;
  }

  boolean active() {
    return active;
  }

  boolean selectableSystem() {
    return system && active;
  }
}
```

Keep the entity package-local. Do not add setters, Lombok entity annotations, builders, inheritance, lifecycle hooks, or domain-event behavior.

- [ ] **Step 5: Add the deterministic read repository**

Create `PersonalityRepository.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PersonalityRepository extends JpaRepository<PersonalityEntity, Long> {

  List<PersonalityEntity> findAllByOrderByDisplayOrderAscPersonalityKeyAsc();
}
```

Do not add custom JPQL, specifications, query DSLs, or a repository abstraction over Spring Data.

- [ ] **Step 6: Add the roster read model**

Create `PersonalityRosterItem.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

public record PersonalityRosterItem(
    String key, String displayName, String description, String avatarRef) {

  static PersonalityRosterItem from(PersonalityEntity entity) {
    return new PersonalityRosterItem(
        entity.personalityKey(), entity.displayName(), entity.description(), entity.avatarRef());
  }
}
```

This record intentionally has only four components. Do not add prompt traits, probability, style/boundary guidance, persistence ID, ordering, or flags.

- [ ] **Step 7: Add the read service**

Create `PersonalityService.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class PersonalityService {

  private final PersonalityRepository personalityRepository;

  PersonalityService(PersonalityRepository personalityRepository) {
    this.personalityRepository = personalityRepository;
  }

  List<PersonalityRosterItem> listSelectable() {
    return personalityRepository.findAllByOrderByDisplayOrderAscPersonalityKeyAsc().stream()
        .filter(PersonalityEntity::selectableSystem)
        .map(PersonalityRosterItem::from)
        .toList();
  }
}
```

Do not add caching. The Phase 2 roster is tiny and read frequency is negligible.

- [ ] **Step 8: Run the focused service tests**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest test
```

Expected: `BUILD SUCCESS`; both service tests pass.

- [ ] **Step 9: Compile the backend so Hibernate/JPA metadata is checked**

```bash
./server/mvnw -f server/pom.xml -DskipTests compile
```

Expected: `BUILD SUCCESS` with no Java compilation or annotation-processing errors.

- [ ] **Step 10: Commit the persistence/service slice**

```bash
git add \
  server/src/main/resources/db/migration/V2__create_personality.sql \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityEntity.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRosterItem.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java
git commit -m "feat: add personality persistence catalog"
```

---

### Task 2: Expose the Read-Only Personality Roster API

**Files:**
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java`

**Interfaces:**
- Consumes: `List<PersonalityRosterItem> PersonalityService.listSelectable()` from Task 1.
- Produces: `GET /api/v1/personalities` returning HTTP `200` and a JSON array.
- Public JSON item shape: `{ "key": string, "displayName": string, "description": string, "avatarRef": string|null }`.
- Produces no POST, PUT, PATCH, or DELETE mappings.

- [ ] **Step 1: Write the failing controller tests**

Create `PersonalityControllerTest.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = PersonalityController.class)
class PersonalityControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PersonalityService personalityService;

  @Test
  void listsSelectablePersonalitiesWithPublicRosterShapeOnly() throws Exception {
    when(personalityService.listSelectable())
        .thenReturn(
            List.of(
                new PersonalityRosterItem(
                    "alpha", "Alpha", "Alpha description", "/avatars/alpha.svg"),
                new PersonalityRosterItem("zeta", "Zeta", "Zeta description", null)));

    mockMvc
        .perform(get("/api/v1/personalities"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].key").value("alpha"))
        .andExpect(jsonPath("$[0].displayName").value("Alpha"))
        .andExpect(jsonPath("$[0].description").value("Alpha description"))
        .andExpect(jsonPath("$[0].avatarRef").value("/avatars/alpha.svg"))
        .andExpect(jsonPath("$[0].id").doesNotExist())
        .andExpect(jsonPath("$[0].promptTraits").doesNotExist())
        .andExpect(jsonPath("$[0].speakingProbability").doesNotExist())
        .andExpect(jsonPath("$[0].styleGuidance").doesNotExist())
        .andExpect(jsonPath("$[0].boundaryGuidance").doesNotExist())
        .andExpect(jsonPath("$[0].displayOrder").doesNotExist())
        .andExpect(jsonPath("$[0].system").doesNotExist())
        .andExpect(jsonPath("$[0].active").doesNotExist())
        .andExpect(jsonPath("$[1].key").value("zeta"))
        .andExpect(jsonPath("$[1].avatarRef").value((String) null));
  }

  @Test
  void returnsEmptyArrayWhenNoSelectablePersonalitiesExist() throws Exception {
    when(personalityService.listSelectable()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/personalities"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("[]"));
  }

  @Test
  void doesNotExposeMutationEndpoints() throws Exception {
    List<MockHttpServletRequestBuilder> mutationRequests =
        List.of(
            post("/api/v1/personalities"),
            put("/api/v1/personalities"),
            patch("/api/v1/personalities"),
            delete("/api/v1/personalities"));

    for (MockHttpServletRequestBuilder request : mutationRequests) {
      mockMvc.perform(request).andExpect(status().isMethodNotAllowed());
    }
  }
}
```

The mutation test is the explicit acceptance guard for “system personalities cannot be mutated through any exposed endpoint.” Do not satisfy it by adding mutation mappings that always reject; simply do not create mutation endpoints.

- [ ] **Step 2: Run the controller test and verify it fails before implementation**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityControllerTest test
```

Expected: compilation fails because `PersonalityController` does not exist.

- [ ] **Step 3: Add the GET-only controller**

Create `PersonalityController.java`:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/personalities")
@CrossOrigin(origins = "http://localhost:5173")
public class PersonalityController {

  private final PersonalityService personalityService;

  public PersonalityController(PersonalityService personalityService) {
    this.personalityService = personalityService;
  }

  @GetMapping
  public ResponseEntity<List<PersonalityRosterItem>> listPersonalities() {
    return ResponseEntity.ok(personalityService.listSelectable());
  }
}
```

Match the existing `MatchController` CORS convention for this issue. Do not introduce a cross-cutting CORS refactor as part of #40.

- [ ] **Step 4: Run both personality test classes**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest,PersonalityControllerTest test
```

Expected: `BUILD SUCCESS`; service and controller tests pass.

- [ ] **Step 5: Run the Spring Modulith structure test**

```bash
./server/mvnw -f server/pom.xml -Dtest=ApplicationModulesTest test
```

Expected: `BUILD SUCCESS`; the new `ai/personality` feature remains inside the existing `ai` module and introduces no module-cycle or forbidden-dependency violation.

- [ ] **Step 6: Commit the read-only API slice**

```bash
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java
git commit -m "feat: expose personality roster API"
```

---

## Final Acceptance Verification

Run these checks after both tasks are committed. Do not add production seed rows merely to make manual verification easier.

- [ ] **Apply backend formatting**

```bash
./server/mvnw -f server/pom.xml spotless:apply
```

If formatting changes files, inspect the diff and commit only formatting changes caused by this plan:

```bash
git status --short
git diff --check
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality
git commit -m "style: format personality roster code"
```

If `git status --short` is clean after `spotless:apply`, do not create an empty formatting commit.

- [ ] **Run focused personality tests again after formatting**

```bash
./server/mvnw -f server/pom.xml -Dtest=PersonalityServiceTest,PersonalityControllerTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Run full backend verification**

```bash
./server/mvnw -f server/pom.xml verify
```

Expected: `BUILD SUCCESS`; Java 25/Maven enforcement, Spotless, Error Prone, tests, Spring Modulith verification, and SpotBugs all pass.

- [ ] **Run the repository-level verifier before opening the PR**

```bash
./scripts/verify.sh
```

Expected: backend and frontend verification both pass. #40 does not intentionally change frontend files.

- [ ] **Smoke-test the migration SQL against PostgreSQL 17 without polluting the normal development database**

Start only PostgreSQL:

```bash
cd server
docker compose up -d postgres
cd ..
```

Create a scratch database:

```bash
docker compose -f server/docker-compose.yml exec -T postgres \
  dropdb -U postgres --if-exists aichessrivals_personality_test

docker compose -f server/docker-compose.yml exec -T postgres \
  createdb -U postgres aichessrivals_personality_test
```

Apply the existing V1 migration and the new V2 migration with psql's stop-on-error behavior:

```bash
cat server/src/main/resources/db/migration/V1__create_event_publication.sql \
  server/src/main/resources/db/migration/V2__create_personality.sql | \
  docker compose -f server/docker-compose.yml exec -T postgres \
    psql -v ON_ERROR_STOP=1 -U postgres -d aichessrivals_personality_test
```

Expected: both migrations apply with no SQL error.

Inspect the personality columns and indexes:

```bash
docker compose -f server/docker-compose.yml exec -T postgres \
  psql -U postgres -d aichessrivals_personality_test -c \
  "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'personality' ORDER BY ordinal_position;"

docker compose -f server/docker-compose.yml exec -T postgres \
  psql -U postgres -d aichessrivals_personality_test -c \
  "SELECT indexname FROM pg_indexes WHERE tablename = 'personality' ORDER BY indexname;"
```

Expected columns, in order:

```text
id
personality_key
display_name
description
prompt_traits
speaking_probability
style_guidance
boundary_guidance
avatar_ref
display_order
is_system
is_active
```

Expected indexes include PostgreSQL's primary/unique indexes plus `personality_selectable_order_idx`.

Verify the probability check rejects an invalid row:

```bash
docker compose -f server/docker-compose.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U postgres -d aichessrivals_personality_test -c \
  "INSERT INTO personality (personality_key, display_name, description, prompt_traits, speaking_probability, style_guidance, boundary_guidance, display_order, is_system, is_active) VALUES ('invalid-probability', 'Invalid', 'Invalid test row', 'traits', 1.100, 'style', 'boundary', 0, true, true);"
```

Expected: command fails with the `personality_speaking_probability_check` constraint. This failure is expected and proves the database constraint is active.

Drop the scratch database:

```bash
docker compose -f server/docker-compose.yml exec -T postgres \
  dropdb -U postgres --if-exists aichessrivals_personality_test
```

- [ ] **Confirm the final diff remains inside issue #40's boundary**

```bash
git status --short
git diff --check
git diff --stat master...HEAD
```

Expected production changes are limited to the new V2 migration and the new `ai/personality` feature package. No client, chess, game, Spring AI provider, Maven dependency, or configuration changes should appear.

## Acceptance-Criteria Traceability

- `Migration creates the minimal personality schema` → Task 1 Step 3 + PostgreSQL migration smoke test.
- `System personalities cannot be mutated through any exposed endpoint` → Task 2 controller exposes only GET + `doesNotExposeMutationEndpoints` test.
- `Active personalities can be listed through a stable read-only API` → repository ordering + service filtering + `GET /api/v1/personalities`.
- `API omits internal prompt-only fields that the frontend does not need` → four-field `PersonalityRosterItem` + controller JSON assertions.
- `Empty-roster and inactive-record behavior is tested` → `returnsEmptyRosterWhenRepositoryHasNoRecords` + `listsOnlyActiveSystemPersonalitiesInRepositoryOrder`.
- `Modulith boundaries and repository verification pass` → `ApplicationModulesTest`, backend `verify`, and root `scripts/verify.sh`.

## Deliberate Deferrals

The implementation must stop after the read-only roster foundation is green. Do not continue into:

- Four concrete personality names, bios, prompt traits, fallback lines, or avatar placeholders — issue `#41`.
- Dialogue generation or prompt construction — issue `#42`.
- Dialogue persistence or match-lifecycle integration — issue `#43`.
- Match personality selection, identifier validation, or random rivalry setup — issue `#44`.
- Frontend personality rendering — issue `#44` and later UI work.

This boundary is intentional: #40 should leave behind a small, boring, dependable catalog that later Phase 2 work can consume without turning personality persistence into a framework.
