# PR #52 Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address the remaining PR #52 review findings by making personality persistence read-only at the repository boundary, making the selectable roster query database-filtered and index-compatible, proving the V2 migration against PostgreSQL 17, and rerunning repository verification.

**Architecture:** Keep the existing `ai/personality` feature and schema. Replace `JpaRepository` with Spring Data's marker `Repository` so no application mutation API is exposed, derive one read query that filters `system=true` and `active=true` in PostgreSQL, and retain the service's defensive selectable filter so the inactive/non-system regression remains explicit. Do not introduce Testcontainers, new dependencies, new endpoints, seed data, or new infrastructure.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Data JPA, PostgreSQL 17, Flyway, JUnit 5, Mockito, AssertJ, Docker Compose, existing Maven/PowerShell verification scripts.

## Source of Truth

- PR: `#52 feat: add personality persistence and read-only roster API`
- Issue: `#40 Phase 2: Add personality persistence and read-only roster API`
- Review comment on PR #52: read-only repository boundary + PostgreSQL migration verification
- Original implementation plan: `docs/superpowers/plans/2026-08-08-personality-persistence-roster-api.md`
- Verification instructions: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Work only on branch `feature/issue-40-personality-roster` / PR #52.
- Do not change the V2 schema unless the PostgreSQL smoke test proves it is invalid.
- Do not add `POST`, `PUT`, `PATCH`, or `DELETE` personality endpoints.
- Do not add personality seed rows; issue #41 owns character definitions and seed data.
- Do not add Testcontainers, H2, a new Maven dependency, a new CI service, or a new module.
- Do not remove the service-level `PersonalityEntity::selectableSystem` defensive filter; it keeps inactive/non-system behavior explicitly regression-tested.
- The production repository query must filter `system=true` and `active=true` and order by `displayOrder ASC, personalityKey ASC`.
- The repository must not extend `JpaRepository`, `CrudRepository`, `ListCrudRepository`, or any other interface that exposes persistence mutation methods.
- Keep the existing SpotBugs exclusion unchanged unless a fresh verification run proves it is no longer needed.
- Do not mark the PR ready for review or merge it. Stop after pushing fixes, updating verification evidence, and confirming CI on the new head.

## File Map

**Modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java` — narrow to read-only Spring Data `Repository` and database-filtered roster query.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java` — call the new repository method while retaining the defensive selectable filter.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java` — update mocked method/verification while keeping inactive and non-system rows in the mocked result.
- PR #52 description — replace the skipped PostgreSQL smoke-test note with actual evidence after the smoke test succeeds.

**Do not modify unless verification exposes an actual failure:**

- `server/src/main/resources/db/migration/V2__create_personality.sql`
- `server/spotbugs-exclude.xml`
- `server/pom.xml`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityEntity.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRosterItem.java`
- `client/**`

---

### Task 1: Make the Personality Repository Read-Only and Database-Filtered

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java`

**Interfaces:**
- Produces: `List<PersonalityEntity> PersonalityRepository.findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc()`.
- Consumes: that method from `PersonalityService.listSelectable()`.
- Keeps: `PersonalityEntity.selectableSystem()` as a defensive service-level filter.

- [ ] **Step 1: Update the service test to expect the new repository contract first**

In `PersonalityServiceTest.java`, replace both references to:

```java
findAllByOrderByDisplayOrderAscPersonalityKeyAsc()
```

with:

```java
findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc()
```

The first test must still return all four mocked records below, including an inactive system record and an active non-system record:

```java
List.of(
    personality("archived", "Archived", 5, true, false),
    personality("custom", "Custom", 10, false, true),
    personality("alpha", "Alpha", 20, true, true),
    personality("zeta", "Zeta", 20, true, true))
```

Keep the expected result exactly as the two selectable records:

```java
assertThat(service.listSelectable())
    .containsExactly(
        new PersonalityRosterItem("alpha", "Alpha", "Alpha description", "/avatars/alpha.svg"),
        new PersonalityRosterItem("zeta", "Zeta", "Zeta description", "/avatars/zeta.svg"));
```

Update the Mockito verification to:

```java
verify(personalityRepository)
    .findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc();
```

- [ ] **Step 2: Run the focused service test and verify the contract change fails before production code changes**

On Windows/PowerShell:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=PersonalityServiceTest test
```

Expected: Java test compilation fails because `PersonalityRepository` does not yet declare `findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc()`.

- [ ] **Step 3: Replace the mutable repository interface with the exact read-only version**

Replace the complete contents of `PersonalityRepository.java` with:

```java
package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.util.List;
import org.springframework.data.repository.Repository;

interface PersonalityRepository extends Repository<PersonalityEntity, Long> {

  List<PersonalityEntity> findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc();
}
```

Do not add `save`, `saveAll`, `delete`, `deleteAll`, `findById`, `findAll`, or any other repository method. The interface exists only to serve the selectable roster read path.

- [ ] **Step 4: Update the service to use the database-filtered query**

Replace `PersonalityService.listSelectable()` with:

```java
List<PersonalityRosterItem> listSelectable() {
  return personalityRepository
      .findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc()
      .stream()
      .filter(PersonalityEntity::selectableSystem)
      .map(PersonalityRosterItem::from)
      .toList();
}
```

Do not remove the `.filter(PersonalityEntity::selectableSystem)` line. The repository should enforce the production query predicate; the service keeps a cheap defensive invariant and preserves the inactive/non-system test required by #40.

- [ ] **Step 5: Verify no CRUD-capable repository parent remains**

Run:

```powershell
Select-String -Path "server\src\main\java\dev\krishnamurti\ai_chess_rivals\ai\personality\PersonalityRepository.java" -Pattern "JpaRepository|CrudRepository|ListCrudRepository|PagingAndSortingRepository"
```

Expected: no output.

Then verify the required marker interface and query are present:

```powershell
Select-String -Path "server\src\main\java\dev\krishnamurti\ai_chess_rivals\ai\personality\PersonalityRepository.java" -Pattern "Repository<PersonalityEntity, Long>|findAllBySystemTrueAndActiveTrueOrderByDisplayOrderAscPersonalityKeyAsc"
```

Expected: both lines are found.

- [ ] **Step 6: Run focused personality tests**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=PersonalityServiceTest,PersonalityControllerTest test
```

Expected: `BUILD SUCCESS`; 5 personality tests pass (2 service + 3 controller).

- [ ] **Step 7: Format and inspect the focused diff**

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
git diff --check
git diff -- server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java
```

Expected: only the repository parent/query, service repository call, and corresponding test method references change. No schema/API/AI-provider/client changes.

- [ ] **Step 8: Commit the review fix**

```powershell
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java
git commit -m "fix: make personality roster persistence read-only"
```

---

### Task 2: Prove V2 Against PostgreSQL 17

**Files:** none expected.

**Interfaces:**
- Verifies: `server/src/main/resources/db/migration/V1__create_event_publication.sql` and `V2__create_personality.sql` execute on PostgreSQL 17.
- Verifies: table columns, indexes, and speaking-probability constraint.
- Does not change CI or add a database-testing dependency.

- [ ] **Step 1: Start only the existing PostgreSQL 17 service with deterministic scratch credentials**

From the repository root in PowerShell:

```powershell
$env:POSTGRES_DB = "aichessrivals"
$env:POSTGRES_USER = "postgres"
$env:POSTGRES_PASSWORD = "secretpassword"
docker compose -f server/docker-compose.yml up -d postgres
docker compose -f server/docker-compose.yml exec -T postgres pg_isready -U postgres -d aichessrivals
```

Expected final line contains `accepting connections`.

If Docker is unavailable, stop this task and report that the required review verification cannot be completed. Do not replace this check with H2, SQLite, static SQL inspection, or a new test dependency.

- [ ] **Step 2: Create a clean scratch database**

```powershell
docker compose -f server/docker-compose.yml exec -T postgres dropdb -U postgres --if-exists aichessrivals_personality_test
docker compose -f server/docker-compose.yml exec -T postgres createdb -U postgres aichessrivals_personality_test
```

Expected: both commands exit successfully.

- [ ] **Step 3: Apply V1 and V2 directly with PostgreSQL stop-on-error enabled**

```powershell
Get-Content -Raw "server/src/main/resources/db/migration/V1__create_event_publication.sql", "server/src/main/resources/db/migration/V2__create_personality.sql" | docker compose -f server/docker-compose.yml exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d aichessrivals_personality_test
```

Expected: `CREATE TABLE`, constraints/index creation, and exit code `0`; no SQL error.

- [ ] **Step 4: Inspect the personality schema and indexes**

```powershell
docker compose -f server/docker-compose.yml exec -T postgres psql -U postgres -d aichessrivals_personality_test -c "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'personality' ORDER BY ordinal_position;"

docker compose -f server/docker-compose.yml exec -T postgres psql -U postgres -d aichessrivals_personality_test -c "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'personality' ORDER BY indexname;"
```

Expected personality columns, in order:

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

Expected indexes include:

```text
personality_pkey
personality_key_unique
personality_selectable_order_idx
```

The `personality_selectable_order_idx` definition must contain `(display_order, personality_key)` and the predicate `is_system = true AND is_active = true` (PostgreSQL may render equivalent boolean syntax).

- [ ] **Step 5: Prove the probability constraint rejects an invalid row**

Run:

```powershell
docker compose -f server/docker-compose.yml exec -T postgres psql -U postgres -d aichessrivals_personality_test -c "INSERT INTO personality (personality_key, display_name, description, prompt_traits, speaking_probability, style_guidance, boundary_guidance, display_order, is_system, is_active) VALUES ('invalid-probability', 'Invalid', 'Invalid test row', 'traits', 1.100, 'style', 'boundary', 0, true, true);"
```

Expected: command fails with `personality_speaking_probability_check`.

This failure is the expected result. Verify PowerShell reports a non-zero external command exit code:

```powershell
$LASTEXITCODE
```

Expected: non-zero.

- [ ] **Step 6: Drop the scratch database**

```powershell
docker compose -f server/docker-compose.yml exec -T postgres dropdb -U postgres --if-exists aichessrivals_personality_test
```

Expected: exit code `0`.

Do not delete the normal `aichessrivals` database or Docker volume.

---

### Task 3: Final Verification and PR Evidence

**Files:**
- Modify PR #52 description only after all commands below succeed.

**Interfaces:**
- Verifies the complete backend after the repository contract change.
- Verifies repository-level backend/frontend gates.
- Requires fresh GitHub Actions success for the new PR head after push.

- [ ] **Step 1: Run full backend verification**

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: `BUILD SUCCESS`; all tests pass; Spotless passes; SpotBugs reports zero errors/warnings; `ApplicationModulesTest` passes.

- [ ] **Step 2: Run the repository verifier**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
```

Expected: backend and frontend verification gates complete successfully.

- [ ] **Step 3: Confirm the final diff remains inside the review-fix boundary**

```powershell
git status --short
git diff --check
git diff --stat master...HEAD
git diff master...HEAD -- server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityRepository.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityService.java server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityServiceTest.java
```

Expected: no uncommitted implementation changes. The corrective code delta is limited to the three personality files plus this plan document; the rest of PR #52 remains the original #40 implementation.

- [ ] **Step 4: Push the corrective commit(s)**

```powershell
git push origin feature/issue-40-personality-roster
```

Expected: PR #52 head advances and GitHub Actions starts a fresh CI run.

- [ ] **Step 5: Update PR #52 verification evidence**

In the existing PR description, replace:

```text
- PostgreSQL migration smoke test attempted but skipped because Docker Desktop and `psql` were unavailable in the local environment
```

with:

```text
- PostgreSQL 17 scratch-database smoke test — V1 + V2 applied successfully; personality columns/indexes verified; invalid speaking probability correctly rejected by `personality_speaking_probability_check`
```

Also add one summary bullet:

```text
- narrow personality persistence to a read-only Spring Data repository with DB-side active/system filtering and stable ordering
```

Do not claim GitHub CI is green until the new-head workflow has actually completed successfully.

- [ ] **Step 6: Wait only for GitHub's new-head CI result, then report evidence**

Required successful jobs for this server-side change:

```text
Detect changes
Backend verification
Native image verification
```

`Frontend verification` may be skipped by path filtering if no client/scripts/CI files changed after the corrective commit.

If any required job fails, inspect and fix that failure before declaring PR #52 ready. Do not merge the PR.

## Acceptance Traceability

- `System personalities cannot be mutated through any exposed endpoint/application persistence boundary` → repository extends only marker `Repository` and declares one read query; controller remains GET-only.
- `Active personalities can be listed through a stable read-only API` → DB query filters `system=true` + `active=true` and orders by `displayOrder`, then `personalityKey`.
- `Inactive-record behavior is tested` → service test still injects inactive and non-system records and confirms they are excluded by the defensive service invariant.
- `Migration creates the minimal personality schema` → PostgreSQL 17 scratch-database V1/V2 execution plus schema/index inspection.
- `Database constraints are real` → invalid `speaking_probability=1.100` is rejected by PostgreSQL.
- `Modulith boundaries and repository verification pass` → Maven `verify`, root `verify.ps1`, and fresh GitHub Actions backend/native jobs.

## Stop Condition

Stop when all of the following are true:

1. `PersonalityRepository` exposes only the selectable roster read query.
2. Focused personality tests pass.
3. PostgreSQL 17 V1/V2 smoke test passes and the invalid-probability row is rejected.
4. Full backend verification and root verification pass locally.
5. Fixes are pushed to PR #52.
6. PR description contains the fresh PostgreSQL verification evidence.
7. Fresh GitHub Actions backend and native-image jobs are green.

Do not continue into issue #41 character definitions/seeding, dialogue generation, match personality selection, frontend rendering, or any new persistence abstraction.