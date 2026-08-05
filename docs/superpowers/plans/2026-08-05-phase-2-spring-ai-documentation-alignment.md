# Phase 2 Spring AI Documentation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align every governing and agent-facing document with the approved Phase 2 Spring AI architecture, provider strategy, phase boundaries, runtime reality, and formatting workflow required by issue #37.

**Architecture:** This is a documentation-only change. Update the small set of authoritative documents rather than duplicating policy across files: governing documents define product and architecture decisions, the Tech Stack inventories concrete technologies, and agent guidance links to the canonical formatting and verification documents. Recreate the missing Implementation Strategy document from the approved Phase 2 design so the issue is fully satisfiable.

**Tech Stack:** Markdown, Spring AI terminology, Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Groq via Spring AI's OpenAI-compatible integration, Gemini fallback, Spotless/Google Java Format, Prettier, repository verification scripts.

## Global Constraints

- Documentation-only task: do not add Maven dependencies, provider configuration, runtime code, database changes, dialogue behavior, or UI changes.
- Spring AI is the required Phase 2 LLM integration layer.
- Groq is the primary provider; Gemini is the only automatic fallback.
- Provider names and model names remain environment-configurable.
- Stockfish remains authoritative for move generation, legality, and evaluation; LLMs generate entertainment only.
- Phase 2 may demonstrate `ChatClient`, provider-specific `ChatModel` configuration, prompt templates, structured output mapping, a lightweight Advisor, and Actuator/Micrometer observability.
- Tool calling, chat memory, autonomous agents, and multi-step workflows are deferred to Phase 3.
- Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, PostgreSQL 17, Stockfish 17.1, Docker deployment, and GraalVM Native Image are the current repository reality.
- Spotless with Google Java Format is authoritative for backend formatting; Prettier is authoritative for frontend formatting.
- Apply formatting before verification; do not introduce another formatter or duplicate the full formatting policy in agent guidance.
- Use repository-relative Markdown links only; remove touched `file:///D:/...` links.
- Keep changes scoped to issue #37 and avoid unrelated prose rewrites.

---

### Task 1: Align the Constitution with Current Runtime and Phase 2 Boundaries

**Files:**
- Modify: `docs/AI Chess Rivals - Constitution.md`
- Reference: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`

**Interfaces:**
- Consumes: the approved Phase 2 design and current repository runtime choices.
- Produces: the highest-level project rules that all later documentation must match.

- [ ] **Step 1: Update document metadata and runtime terminology**

Change the version from `1.0` to `1.1`. Replace the stale Java 21/server/client wording with current repository terminology:

```markdown
## Backend

- Java 25
- Spring Boot 4.1.0
- Spring Modulith 2.1.0
- Spring AI
- Spring Web MVC
- Spring Data JPA
- PostgreSQL 17
- Bean Validation
- Flyway
- Lombok
- Actuator / Micrometer
- Chesslib
- Stockfish 17.1
- GraalVM Native Image
```

Keep the existing frontend list, correcting only facts that are stale relative to the Tech Stack document.

- [ ] **Step 2: Replace the generic provider paragraph with the approved Spring AI strategy**

Under `LLM Responsibilities`, retain the entertainment-only boundary and add these explicit rules:

```markdown
Spring AI is the required Phase 2 integration layer for LLM providers.

- Groq is the default primary provider through Spring AI's OpenAI-compatible integration.
- Gemini is the only automatic fallback provider.
- Provider and model names are environment-configurable.
- Provider failure must never stop or fail a chess match.
```

Do not imply direct/custom REST integration is the preferred Phase 2 path.

- [ ] **Step 3: Correct development and deployment principles**

Replace the stale direct-deployment guidance with the repository's actual model:

```markdown
## Development Principles

Use Docker Compose as the standard local development environment for PostgreSQL and the backend runtime. The React frontend may run through Vite for fast iteration. Stockfish is bundled with the backend runtime.

## Deployment Principles

Deploy the backend as a Docker container compiled for the project's GraalVM Native Image target. Bundle the Linux Stockfish executable in the backend image and keep provider/model selection environment-configurable.
```

Do not describe Railway as the primary deployment target if the repository currently documents Render.

- [ ] **Step 4: Make Phase 2 and Phase 3 boundaries explicit**

Add a concise architecture subsection or extend `Explicitly Out of Scope` so it states:

```markdown
Phase 2 includes Spring AI-based dialogue, structured output, provider fallback, prompt templates, a lightweight Advisor, and observability.

Tool calling, chat memory, autonomous agents, multi-step workflows, and generic orchestration remain deferred to Phase 3.
```

- [ ] **Step 5: Review the Constitution for contradictions**

Confirm the document does not contain any of the following stale claims:

```text
Java 21
Deploy Spring Boot directly to Railway or Render
Introduce Docker only if deployment complexity justifies it
Gemini, OpenAI, Anthropic, OpenRouter as an undifferentiated Phase 2 strategy
LLMs choosing or validating chess moves
```

- [ ] **Step 6: Commit the Constitution update**

```bash
git add "docs/AI Chess Rivals - Constitution.md"
git commit -m "docs: align constitution with phase 2 spring ai"
```

---

### Task 2: Recreate and Align the Implementation Strategy

**Files:**
- Create: `docs/AI Chess Rivals - Implementation Strategy.md`
- Reference: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Reference: `docs/AI Chess Rivals - Constitution.md`

**Interfaces:**
- Consumes: the Constitution's governing rules and the approved Phase 2 design.
- Produces: the authoritative phased delivery strategy referenced by issue #37.

- [ ] **Step 1: Create the missing document with metadata and purpose**

Start the file with:

```markdown
# AI Chess Rivals — Implementation Strategy

Version: 1.1
Status: Accepted

## Purpose

Implement the project in independently verifiable phases so chess correctness remains separate from AI entertainment behavior. Phase 1 provides the stable Stockfish match foundation. Phase 2 adds Spring AI-based personality and dialogue without changing chess authority. Phase 3 may later introduce bounded agentic workflows.
```

- [ ] **Step 2: Preserve the completed Phase 1 boundary**

Document Phase 1 as the completed chess foundation responsible for Stockfish/UCI communication, game lifecycle, board state, move history, result detection, WebSocket streaming, frontend hydration, and resilience. State that Phase 1 contains no LLM dependency.

- [ ] **Step 3: Define Phase 2 using the approved design**

Include the following exact architectural decisions:

```markdown
## Phase 2 — AI Personality Layer

- Use Spring AI as the required provider integration layer.
- Use Groq as primary through the OpenAI-compatible integration.
- Use Gemini as the only automatic fallback.
- Keep provider and model names environment-configurable.
- Generate dialogue only after a move is committed and classified.
- Persist accepted dialogue and restore it on refresh/reconnect.
- Keep provider waits bounded and continue the match with deterministic fallback dialogue when both providers fail.
- Use structured output with `text`, `emotion`, and `reactionType`.
- Demonstrate `ChatClient`, provider-specific `ChatModel` configuration, prompt templates, structured output mapping, a lightweight Advisor, and Actuator/Micrometer observability.
```

- [ ] **Step 4: Define the chess/AI separation**

State unambiguously:

```markdown
The chess layer owns legal moves, position state, move commitment, evaluation, result detection, and match progression.

The AI layer observes committed game events and produces entertainment. It never selects, validates, or replaces a chess move.
```

- [ ] **Step 5: Add the approved Phase 3 deferrals**

Create a Phase 3 boundary section that explicitly defers:

```markdown
- tool calling
- chat memory
- autonomous agents
- multi-step workflows
- generic orchestration frameworks
- long-term memory
```

Do not describe these as Phase 2 implementation options.

- [ ] **Step 6: Include delivery order and success criteria**

Use the approved delivery order from the design, beginning with documentation alignment and provider foundation, followed by Stockfish evaluation, personality persistence, dialogue workflow, persistence/lifecycle integration, frontend selection/activity feed, and observability/acceptance.

Phase 2 success must include provider failure never preventing match completion and dialogue surviving refresh/reconnect.

- [ ] **Step 7: Commit the recreated strategy**

```bash
git add "docs/AI Chess Rivals - Implementation Strategy.md"
git commit -m "docs: restore phase implementation strategy"
```

---

### Task 3: Update the Tech Stack Inventory for Spring AI

**Files:**
- Modify: `docs/AI Chess Rivals - Tech Stack.md`
- Reference: `server/pom.xml`
- Reference: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`

**Interfaces:**
- Consumes: current dependency versions from `server/pom.xml` and approved future Phase 2 dependency intent.
- Produces: a factual inventory that distinguishes dependencies already present from dependencies planned by later Phase 2 issues.

- [ ] **Step 1: Verify current versions before editing**

Read `server/pom.xml` and record the exact current values for Java, Spring Boot, Spring Modulith, GraalVM/native-image configuration, and existing starters. Do not invent a Spring AI version.

- [ ] **Step 2: Add Spring AI to the executive summary**

Add Spring AI to the backend technology summary and describe it as the Phase 2 LLM integration layer. Keep Java 25, Spring Boot 4.1.0, and Spring Modulith 2.1.0 consistent with the build.

- [ ] **Step 3: Add a dedicated Spring AI section**

Document the approved provider architecture:

```markdown
### Spring AI and LLM Providers

- Spring AI is the required integration layer for Phase 2.
- Groq is primary through the OpenAI-compatible integration.
- Gemini is the only automatic fallback.
- Provider and model names are configured through environment-backed application properties.
- Phase 2 uses `ChatClient`, provider-specific `ChatModel` beans/configuration, prompt templates, structured output mapping, a lightweight Advisor, and Actuator/Micrometer observability.
- Tool calling, chat memory, autonomous agents, and multi-step workflows are deferred to Phase 3.
```

- [ ] **Step 4: Document BOM and starters without falsely claiming they already exist**

Add a clearly labeled `Planned for Phase 2` table containing the Spring AI BOM and provider starters required by the approved design. Use artifact names verified from the implementation epic/design or official Spring AI documentation during the later dependency issue. For this documentation issue, do not add them to `pom.xml` and do not list an unverified version number as current.

The table must distinguish:

```text
Current repository dependencies
Planned Phase 2 dependencies
```

- [ ] **Step 5: Remove stale direct/custom REST positioning**

If `spring-boot-starter-restclient` is described as the preferred way to call LLM providers, revise it to its actual generic HTTP-client purpose. Do not remove it from the factual dependency inventory if it is still present in `pom.xml`.

- [ ] **Step 6: Recheck discrepancy notes**

Remove the Java 21 inconsistency warning after the Constitution is updated. Keep only discrepancies that still exist after this issue's edits.

- [ ] **Step 7: Commit the Tech Stack update**

```bash
git add "docs/AI Chess Rivals - Tech Stack.md"
git commit -m "docs: document spring ai provider strategy"
```

---

### Task 4: Align Root and Local Agent Guidance

**Files:**
- Modify: `AGENTS.md`
- Modify: `.agents/AGENTS.md`
- Reference: `docs/Code Formatting Guidelines.md`
- Reference: `docs/BUILD_AND_VERIFY.md`
- Reference: `docs/AI Chess Rivals - Constitution.md`
- Reference: `docs/AI Chess Rivals - Tech Stack.md`
- Reference: `docs/AI Chess Rivals - Implementation Strategy.md`

**Interfaces:**
- Consumes: authoritative architecture, formatting, and verification documents.
- Produces: concise instructions that direct coding agents to those authoritative sources without duplicating them.

- [ ] **Step 1: Replace machine-specific links in `AGENTS.md`**

Replace all `file:///D:/...` links with repository-relative links. Use links that resolve from the repository root file:

```markdown
[Constitution](docs/AI%20Chess%20Rivals%20-%20Constitution.md)
[Implementation Strategy](docs/AI%20Chess%20Rivals%20-%20Implementation%20Strategy.md)
[Tech Stack Document](docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md)
[Code Formatting Guidelines](docs/Code%20Formatting%20Guidelines.md)
[Build and Verify](docs/BUILD_AND_VERIFY.md)
```

- [ ] **Step 2: Update `AGENTS.md` AI and phase guidance**

Replace the generic provider paragraph with:

```markdown
Phase 2 uses Spring AI as the required LLM integration layer. Groq is primary through the OpenAI-compatible integration, Gemini is the only automatic fallback, and provider/model names are environment-configurable.
```

State that tools, memory, autonomous agents, and multi-step workflows belong to Phase 3. Preserve the entertainment-only and Stockfish-authority rules.

- [ ] **Step 3: Add concise formatting-before-verification instructions to `AGENTS.md`**

Link the formatting guide rather than copying its full policy. Include the standard commands:

```markdown
### Formatting and Verification

Read the [Code Formatting Guidelines](docs/Code%20Formatting%20Guidelines.md). Apply formatting before verification.

Backend from repository root:

- Windows: `server\mvnw.cmd -f server\pom.xml spotless:apply`
- POSIX: `./server/mvnw -f server/pom.xml spotless:apply`

Frontend from `client/`:

- `npm run format`
- `npm run format:check`

Whole repository:

- Windows: `.\scripts\verify.ps1`
- POSIX: `./scripts/verify.sh`
```

Explicitly state that Spotless/Google Java Format and Prettier remain authoritative and no additional formatter should be introduced.

- [ ] **Step 4: Replace the machine-specific Constitution link in `.agents/AGENTS.md`**

Because `.agents/AGENTS.md` is one directory below the repository root, use links relative to `.agents/`:

```markdown
[Constitution](../docs/AI%20Chess%20Rivals%20-%20Constitution.md)
[Implementation Strategy](../docs/AI%20Chess%20Rivals%20-%20Implementation%20Strategy.md)
[Tech Stack Document](../docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md)
[Code Formatting Guidelines](../docs/Code%20Formatting%20Guidelines.md)
[Build and Verify](../docs/BUILD_AND_VERIFY.md)
```

- [ ] **Step 5: Add Spring AI and phase boundaries to `.agents/AGENTS.md`**

Under `Core Architectural Constraints`, add concise bullets that match the root guidance:

```markdown
- Phase 2 uses Spring AI; Groq is primary and Gemini is the only automatic fallback.
- Provider and model names are environment-configurable.
- Tools, chat memory, autonomous agents, and multi-step workflows are Phase 3 concerns.
```

Do not duplicate the complete provider failure policy or prompt design here.

- [ ] **Step 6: Add formatting-before-verification instructions to `.agents/AGENTS.md`**

Add the same commands as the root guidance and link to the canonical formatting guide. Keep wording concise and state that formatting is applied before the root verifier.

- [ ] **Step 7: Check both files for stale links and conflicting guidance**

Run:

```bash
git grep -n "file:///D:/" -- AGENTS.md .agents/AGENTS.md
```

Expected: no output.

Run:

```bash
git grep -n -E "direct REST|custom REST|LLMs.*move|LLM.*move" -- AGENTS.md .agents/AGENTS.md
```

Expected: no wording that recommends direct/custom REST for Phase 2 or allows LLM chess decisions. A sentence explicitly prohibiting LLM move decisions is valid.

- [ ] **Step 8: Commit the agent-guidance update**

```bash
git add AGENTS.md .agents/AGENTS.md
git commit -m "docs: align agent guidance for spring ai"
```

---

### Task 5: Cross-Document Consistency and Acceptance Verification

**Files:**
- Verify: `docs/AI Chess Rivals - Constitution.md`
- Verify: `docs/AI Chess Rivals - Implementation Strategy.md`
- Verify: `docs/AI Chess Rivals - Tech Stack.md`
- Verify: `AGENTS.md`
- Verify: `.agents/AGENTS.md`
- Verify: `docs/Code Formatting Guidelines.md`
- Verify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Consumes: all documentation changes from Tasks 1-4.
- Produces: evidence that every acceptance criterion in issue #37 is satisfied.

- [ ] **Step 1: Review the staged diff for scope**

```bash
git diff master...HEAD -- "docs/*.md" AGENTS.md .agents/AGENTS.md
```

Confirm there are no Maven, source-code, runtime configuration, migration, or UI changes.

- [ ] **Step 2: Verify required architecture terms are present**

```bash
git grep -n "Spring AI" -- "docs/*.md" AGENTS.md .agents/AGENTS.md
git grep -n "Groq" -- "docs/*.md" AGENTS.md .agents/AGENTS.md
git grep -n "Gemini" -- "docs/*.md" AGENTS.md .agents/AGENTS.md
git grep -n "Phase 3" -- "docs/*.md" AGENTS.md .agents/AGENTS.md
```

Expected: the Constitution, Implementation Strategy, Tech Stack, and both agent-guidance files consistently describe Spring AI, Groq primary, Gemini fallback, and Phase 3 deferrals.

- [ ] **Step 3: Verify stale runtime and deployment claims are gone from governing docs**

```bash
git grep -n -E "Java 21|Deploy Spring Boot directly|Introduce Docker only" -- "docs/AI Chess Rivals - Constitution.md" "docs/AI Chess Rivals - Implementation Strategy.md" "docs/AI Chess Rivals - Tech Stack.md"
```

Expected: no output.

- [ ] **Step 4: Verify both agent files link the formatting guide**

```bash
git grep -n "Code Formatting Guidelines" -- AGENTS.md .agents/AGENTS.md
```

Expected: one repository-relative link in each file, plus concise formatting instructions.

- [ ] **Step 5: Verify formatter and root verification commands**

```bash
git grep -n "spotless:apply" -- AGENTS.md .agents/AGENTS.md
git grep -n "npm run format" -- AGENTS.md .agents/AGENTS.md
git grep -n "scripts.*verify" -- AGENTS.md .agents/AGENTS.md
```

Expected: both agent-guidance files include backend formatting, frontend formatting/check, and Windows/POSIX root verification commands.

- [ ] **Step 6: Run repository verification**

Windows:

```powershell
.\scripts\verify.ps1
```

POSIX:

```sh
./scripts/verify.sh
```

Expected: all configured backend and frontend checks pass. If an environment prerequisite prevents execution, record the exact command and failure in the pull request; do not claim verification passed.

- [ ] **Step 7: Perform issue #37 acceptance checklist review**

Confirm each item explicitly:

```text
[ ] Runtime/deployment choices agree across relevant docs.
[ ] Spring AI is required for Phase 2.
[ ] Groq primary and Gemini fallback are documented.
[ ] Provider/model names are environment-configurable.
[ ] Stockfish owns chess decisions; LLMs own entertainment only.
[ ] Tools, memory, autonomous agents, and multi-step workflows are deferred to Phase 3.
[ ] No document recommends direct/custom REST as the Phase 2 integration path.
[ ] Both agent files link the formatting guide with repository-relative links.
[ ] Both agent files require formatting before verification.
[ ] Spotless and Prettier remain authoritative.
[ ] No touched `file:///D:/...` link remains.
[ ] Verification result is recorded honestly.
```

- [ ] **Step 8: Commit any consistency fixes**

If the review required corrections:

```bash
git add "docs/AI Chess Rivals - Constitution.md" "docs/AI Chess Rivals - Implementation Strategy.md" "docs/AI Chess Rivals - Tech Stack.md" AGENTS.md .agents/AGENTS.md
git commit -m "docs: resolve phase 2 documentation inconsistencies"
```

If no correction was required, do not create an empty commit.

- [ ] **Step 9: Prepare the pull request summary**

Use this structure:

```markdown
## Summary

- align governing documentation with the approved Spring AI Phase 2 design
- document Groq primary / Gemini fallback and environment-configurable models
- restore the missing Implementation Strategy document
- link canonical formatting and verification guidance from both agent files
- replace machine-specific documentation links with repository-relative links

## Verification

- `<exact verification command>` — PASS/FAIL with reason

Closes #37
```
