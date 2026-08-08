# PR #48 Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three remaining PR #48 review gaps: prevent Spring AI multi-model auto-configuration ambiguity, expose the `ai.api` Modulith boundary, and prove the production GraalVM native build before issue #38 is considered complete.

**Architecture:** Keep the existing Groq -> Gemini -> deterministic fallback implementation unchanged. Disable only Spring AI's generic `ChatClient.Builder` auto-configuration because this application explicitly owns two named `ChatModel`/`ChatClient` pairs; export the existing `ai.api` package as a Modulith named interface; add one full AI-enabled Spring Boot context test plus one structural module-exposure assertion; then run the repository's full JVM and native verification gates.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring AI 2.0.0, JUnit 5, AssertJ, Spring Boot Test, Mockito bean override, GraalVM Native Image, Docker.

## Global Constraints

- Work on PR #48 branch `feature/issue-38-spring-ai-provider-foundation`; do not create a second feature branch unless the execution environment requires an isolated worktree.
- Source corrective design: `docs/superpowers/specs/2026-08-08-pr48-review-fixes-design.md`.
- Original issue remains #38; do not broaden scope beyond its provider-foundation acceptance criteria.
- Do not modify `FailoverAiChatGateway`, `AiProviderConfiguration` timeout/retry logic, provider order, deterministic fallback semantics, or existing AI API request/result types.
- The existing Gemini `Duration.toMillis()` conversion is correct for the Google GenAI SDK version used by Spring AI 2.0.0. Do not change it.
- Keep `spring.ai.model.chat=none`; the two provider models remain manually constructed.
- Add `spring.ai.chat.client.enabled=false` as a fixed application architectural setting. Do not expose it through a new environment variable.
- Automated tests must use dummy provider credentials/model names only and must not call Groq or Gemini.
- Follow the repository's configuration-documentation rule: because `application.yaml` changes, update `docs/AI Chess Rivals - Tech Stack.md` in the same task.
- `server/.env.example` does not change because no environment-variable contract changes.
- Native verification is not optional. If Docker/GraalVM native compilation cannot run, stop and report the acceptance criterion as still unmet rather than declaring #38 complete.
- Do not add providers, registries, retry libraries, resilience frameworks, controllers, persistence, prompts, personalities, tool calling, memory, agents, or UI changes.

## File Map

**Create:**

- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/AiEnabledApplicationContextTest.java` — full Spring Boot context proof that two explicit providers coexist while the generic Spring AI builder is disabled.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/package-info.java` — exports `ai.api` as the Modulith named interface `api`.

**Modify:**

- `server/src/main/resources/application.yaml` — add `spring.ai.chat.client.enabled=false`.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ApplicationModulesTest.java` — assert `AiChatGateway` is exposed by the `ai` module.
- `docs/AI Chess Rivals - Tech Stack.md` — record that the generic Spring AI `ChatClient.Builder` auto-configuration is intentionally disabled because the application owns two explicit provider clients.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/AiProviderConfiguration.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/FailoverAiChatGateway.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/config/AiProperties.java`
- `server/.env.example`
- game/chess/frontend code

---

### Task 1: Disable the Generic Spring AI ChatClient Builder and Prove Full Enabled-Mode Wiring

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/AiEnabledApplicationContextTest.java`
- Modify: `server/src/main/resources/application.yaml`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`

**Interfaces:**
- Consumes: existing named beans `groqChatModel`, `geminiChatModel`, `groqChatClient`, `geminiChatClient`, and the single `AiChatGateway` created by the current PR.
- Produces: a full-context regression test ensuring the real Spring AI starter auto-configuration graph cannot reintroduce a single-model `ChatClient.Builder` conflict.

- [ ] **Step 1: Write the failing full-context test before changing configuration**

Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/AiEnabledApplicationContextTest.java` exactly as follows:

```java
package dev.krishnamurti.ai_chess_rivals.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "app.ai.enabled=true",
      "app.ai.groq.api-key=test-groq-key",
      "app.ai.groq.model=test-groq-model",
      "app.ai.gemini.api-key=test-gemini-key",
      "app.ai.gemini.model=test-gemini-model",
      "app.owner.control-token=test-owner-token",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
class AiEnabledApplicationContextTest {

  @Autowired ApplicationContext context;

  @MockitoBean StockfishClient stockfishClient;

  @Test
  void enabledAiModeUsesOnlyExplicitProviderClients() {
    assertThat(context.getBeansOfType(ChatModel.class))
        .containsOnlyKeys("groqChatModel", "geminiChatModel");
    assertThat(context.getBeansOfType(ChatClient.class))
        .containsOnlyKeys("groqChatClient", "geminiChatClient");
    assertThat(context.getBeansOfType(ChatClient.Builder.class)).isEmpty();
    assertThat(context.getBeansOfType(AiChatGateway.class)).hasSize(1);
  }
}
```

Do not call `AiChatGateway.generate(...)` in this test. Bean construction is the target; provider I/O is intentionally excluded.

- [ ] **Step 2: Run the new test and verify the current PR wiring fails**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml "-Dtest=AiEnabledApplicationContextTest" test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=AiEnabledApplicationContextTest test
```

Expected before the configuration fix: **FAIL** during application-context startup because Spring AI's generic `ChatClient.Builder` auto-configuration cannot resolve a unique `ChatModel` from the explicit Groq and Gemini model beans. Do not continue if the test passes for an unrelated reason; inspect the context and ensure the test is loading the actual application plus starter auto-configurations.

- [ ] **Step 3: Disable only the generic ChatClient builder auto-configuration**

In `server/src/main/resources/application.yaml`, keep the existing `spring.ai.model.*=none` block and add `chat.client.enabled` as a sibling under `spring.ai`:

```yaml
spring:
  ai:
    chat:
      client:
        enabled: false
    model:
      chat: none
      embedding: none
      image: none
      moderation: none
      audio:
        speech: none
        transcription: none
```

Do not remove `spring.ai.model.chat: none`; that still prevents provider starter model auto-configuration from competing with the explicit models.

- [ ] **Step 4: Update the Tech Stack document required by repository rules**

In `docs/AI Chess Rivals - Tech Stack.md`, in the `### Spring AI and LLM Providers` section, add this bullet immediately after the bullet describing Groq primary and Gemini fallback:

```markdown
- The application creates both provider `ChatModel`/`ChatClient` pairs explicitly, so Spring AI's generic single-model `ChatClient.Builder` auto-configuration is disabled with `spring.ai.chat.client.enabled=false`.
```

Do not change provider versions or add another dependency entry.

- [ ] **Step 5: Run formatting, then rerun the full-context test**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml "-Dtest=AiEnabledApplicationContextTest" test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=AiEnabledApplicationContextTest test
```

Expected: `AiEnabledApplicationContextTest` passes, both explicit models and clients are present, no generic `ChatClient.Builder` exists, and no real provider request occurs.

- [ ] **Step 6: Run the existing focused AI configuration tests to catch regressions**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml "-Dtest=AiPropertiesBindingTest,AiProviderConfigurationTest,AiConfigurationContextTest,FailoverAiChatGatewayTest,AiEnabledApplicationContextTest" test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=AiPropertiesBindingTest,AiProviderConfigurationTest,AiConfigurationContextTest,FailoverAiChatGatewayTest,AiEnabledApplicationContextTest test
```

Expected: all selected tests pass with zero failures/errors.

- [ ] **Step 7: Commit the auto-configuration correction**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/AiEnabledApplicationContextTest.java server/src/main/resources/application.yaml "docs/AI Chess Rivals - Tech Stack.md"
git commit -m "fix: disable generic spring ai chat client wiring"
```

---

### Task 2: Export the AI Application API Through Spring Modulith

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ApplicationModulesTest.java`
- Create: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/package-info.java`

**Interfaces:**
- Consumes: public `dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway` from PR #48 and Spring Modulith `ApplicationModules`.
- Produces: named interface `ai :: api`, allowing later Phase 2 modules to depend legally on the existing AI application-facing API.

- [ ] **Step 1: Add a failing structural assertion for `AiChatGateway` exposure**

Replace `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ApplicationModulesTest.java` with:

```java
package dev.krishnamurti.ai_chess_rivals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(AiChessRivalsApplication.class).verify();
  }

  @Test
  void exposesAiApi() {
    ApplicationModules modules = ApplicationModules.of(AiChessRivalsApplication.class);
    var aiModule = modules.getModuleByName("ai").orElseThrow();

    assertThat(aiModule.isExposed(AiChatGateway.class)).isTrue();
  }
}
```

- [ ] **Step 2: Run the structural test and verify exposure currently fails**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml "-Dtest=ApplicationModulesTest" test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=ApplicationModulesTest test
```

Expected before adding the named interface: `verifiesModuleStructure` remains green and `exposesAiApi` **FAILS** because `ai.api` is currently an internal nested package.

- [ ] **Step 3: Export the nested AI API package**

Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/package-info.java`:

```java
@org.springframework.modulith.NamedInterface("api")
package dev.krishnamurti.ai_chess_rivals.ai.api;
```

Match the existing `chess.api` pattern exactly. Do not open the whole `ai` module and do not move the public API classes.

- [ ] **Step 4: Format and rerun the structural test**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml "-Dtest=ApplicationModulesTest" test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml -Dtest=ApplicationModulesTest test
```

Expected: both `ApplicationModulesTest` methods pass; `AiChatGateway` is reported as exposed by the `ai` module.

- [ ] **Step 5: Rerun the full AI-enabled context test after the package metadata change**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml "-Dtest=AiEnabledApplicationContextTest" test
```

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=AiEnabledApplicationContextTest test
```

Expected: PASS with no provider network calls.

- [ ] **Step 6: Commit the Modulith boundary correction**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ApplicationModulesTest.java server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/package-info.java
git commit -m "fix: expose spring ai module api"
```

---

### Task 3: Run Complete JVM and Native Verification Before Updating PR #48

**Files:**
- No source-code changes expected.
- PR metadata: update PR #48 validation text only after every required command succeeds.

**Interfaces:**
- Consumes: completed Tasks 1-2.
- Produces: fresh evidence that issue #38 satisfies JVM, repository-wide, Modulith, and GraalVM native-image acceptance gates.

- [ ] **Step 1: Ensure the branch is clean before verification**

```bash
git status --short
```

Expected: no uncommitted source changes. If formatting changed files after the previous commit, commit those changes before continuing.

- [ ] **Step 2: Run backend verification**

Windows:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

POSIX:

```bash
./server/mvnw -f server/pom.xml verify
```

Expected: Maven exits `0`; formatting, Error Prone compilation, all backend tests including `ApplicationModulesTest` and `AiEnabledApplicationContextTest`, and SpotBugs pass.

- [ ] **Step 3: Run the root repository verifier**

Windows:

```powershell
.\scripts\verify.ps1
```

POSIX:

```bash
./scripts/verify.sh
```

Expected: exit `0`; backend verification plus frontend formatting/type checking/lint/tests/production build all pass.

- [ ] **Step 4: Run the canonical production native-image build**

From the repository root:

```bash
docker build -t ai-chess-rivals:issue-38 ./server
```

Expected: Docker exits `0`. The builder stage must successfully execute the repository's GraalVM command:

```bash
./mvnw clean package native:compile -B -DskipTests -Pnative -Plinux
```

This is the acceptance gate that was previously missing from PR #48.

If Docker Desktop/Linux containers or the container engine is unavailable, **stop here**. Do not mark #38 complete, do not claim native compatibility passed, and leave the PR in draft until the command can actually be executed successfully on a suitable machine/runner.

- [ ] **Step 5: Confirm no accidental changes were generated by verification**

```bash
git status --short
```

Expected: clean working tree. Generated Stockfish/native build artifacts must remain ignored and uncommitted.

- [ ] **Step 6: Update PR #48 validation evidence only after Steps 2-5 pass**

In PR #48's `## Validation` section, replace the old native-build failure bullet with this exact line:

```markdown
- `docker build -t ai-chess-rivals:issue-38 ./server` — passed; production GraalVM native image compiled successfully with the Linux Stockfish profile.
```

Also refresh the backend/root verification bullets if their observed test counts changed. Never copy the old counts if the new run reports different values.

- [ ] **Step 7: Resolve the two review threads only after their corresponding tests pass**

Resolve the review thread on `server/src/main/resources/application.yaml` only after `AiEnabledApplicationContextTest` passes.

Resolve the review thread on `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/package-info.java` only after `ApplicationModulesTest.exposesAiApi` passes.

Do not resolve a thread merely because code was edited.

- [ ] **Step 8: Final branch sanity check**

```bash
git log --oneline -5
git status --short
```

Expected: the two corrective implementation commits are visible after the plan/spec commits and the working tree is clean.

---

## Review Checklist for Luna Before Declaring the PR Ready

- [ ] `spring.ai.chat.client.enabled=false` exists in `application.yaml`.
- [ ] `spring.ai.model.chat=none` still exists.
- [ ] No new environment variable was added for the fixed ChatClient auto-config setting.
- [ ] `AiEnabledApplicationContextTest` loads the full Spring Boot application with AI enabled and dummy credentials.
- [ ] The full-context test asserts exactly the two explicit `ChatModel` beans and two explicit `ChatClient` beans.
- [ ] The full-context test asserts no `ChatClient.Builder` bean exists.
- [ ] `ai/api/package-info.java` declares `@NamedInterface("api")`.
- [ ] `ApplicationModulesTest` explicitly proves `AiChatGateway` is exposed.
- [ ] `AiProviderConfiguration` timeout/retry code is unchanged.
- [ ] `FailoverAiChatGateway` is unchanged.
- [ ] Tech Stack documentation records the intentional generic builder disablement.
- [ ] Backend `verify` passed freshly.
- [ ] Root verifier passed freshly.
- [ ] `docker build -t ai-chess-rivals:issue-38 ./server` passed freshly.
- [ ] PR validation text contains only evidence from the fresh runs.
- [ ] Both review threads are resolved only after their verification gates pass.
