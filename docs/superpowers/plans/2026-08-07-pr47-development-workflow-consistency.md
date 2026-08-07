# PR #47 Development Workflow Consistency Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining documentation inconsistency in PR #47 by making the Constitution describe the repository's actual local-development workflow while preserving the approved Docker/GraalVM production deployment model.

**Architecture:** This is a documentation-only correction. `docs/AI Chess Rivals - Constitution.md` should state high-level development principles that agree with the existing operational instructions in `AGENTS.md`, `README.md`, and `docs/BUILD_AND_VERIFY.md`; those operational documents already describe the intended workflow and should not be rewritten for this fix.

**Tech Stack:** Markdown, Java 25, Spring Boot 4.1.0, PostgreSQL 17, Docker Compose, Vite, Stockfish 17.1, GraalVM Native Image.

## Global Constraints

- Scope is limited to the blocking PR #47 review finding about local-development runtime consistency.
- Do not change Spring AI, Groq/Gemini, Phase 2/Phase 3, formatting, dependency, or chess-authority wording unless required to preserve surrounding grammar.
- Do not change runtime code, Docker configuration, Maven configuration, frontend configuration, or CI.
- Preserve Docker + GraalVM Native Image as the production deployment model.
- Preserve PostgreSQL-in-Docker as the normal local database workflow.
- Preserve direct Spring Boot execution via Maven/IDE as the normal local backend workflow described by `AGENTS.md`, `README.md`, and `docs/BUILD_AND_VERIFY.md`.
- Preserve Vite as the normal frontend development workflow.
- Keep the diff minimal and avoid unrelated documentation cleanup.

---

### Task 1: Correct the Constitution's Local-Development Principle

**Files:**
- Modify: `docs/AI Chess Rivals - Constitution.md`
- Reference: `AGENTS.md`
- Reference: `README.md`
- Reference: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Consumes: the repository's existing operational local-development workflow.
- Produces: a Constitution whose development and deployment principles no longer contradict operational documentation.

- [ ] **Step 1: Confirm the current operational workflow before editing**

Read the local-development sections in these files and confirm they currently describe:

```text
PostgreSQL: Docker Compose
Backend: Spring Boot directly via Maven/IDE for normal local development and verification
Frontend: Vite development server
Production backend: Docker container / GraalVM Native Image
```

Do not modify these reference files for this task unless the branch has changed and they no longer describe the workflow above.

- [ ] **Step 2: Replace only the stale Constitution development paragraph**

In `docs/AI Chess Rivals - Constitution.md`, replace:

```markdown
Use Docker Compose as the standard local development environment for PostgreSQL and the backend
runtime. The React frontend may run through Vite for fast iteration. Stockfish is bundled with
the backend runtime.
```

with:

```markdown
Use Docker Compose for PostgreSQL during normal local development. Run the Spring Boot backend
directly through Maven or the IDE for fast iteration and local verification, and run the React
frontend through the Vite development server. The backend may also run in Docker when a fully
containerized environment or deployment-parity check is useful.
```

Leave the existing production deployment paragraph unchanged:

```markdown
Deploy the backend as a Docker container compiled for the project's GraalVM Native Image target.
Bundle the Linux Stockfish executable in the backend image and keep provider/model selection
environment-configurable.
```

- [ ] **Step 3: Check that development and deployment are now clearly separated**

Read the full `Development Principles` and `Deployment Principles` sections together. Confirm they communicate all of the following without contradiction:

```text
Local DB -> Docker Compose
Local backend -> Maven/IDE by default
Local frontend -> Vite
Optional local backend container -> allowed for parity/full-container checks
Production backend -> Docker + GraalVM Native Image
Production Stockfish -> Linux binary bundled in backend image
```

- [ ] **Step 4: Run targeted repository consistency searches**

From the repository root, run:

```bash
git grep -n "standard local development environment for PostgreSQL and the backend" -- "docs/AI Chess Rivals - Constitution.md" AGENTS.md README.md docs/BUILD_AND_VERIFY.md
```

Expected: no output.

Run:

```bash
git grep -n -E "Spring Boot directly|spring-boot:run|Docker Compose|docker compose" -- "docs/AI Chess Rivals - Constitution.md" AGENTS.md README.md docs/BUILD_AND_VERIFY.md
```

Expected: the results are compatible with the workflow documented above; no file requires Docker for the normal local backend path while another requires direct Maven/IDE execution.

- [ ] **Step 5: Review the diff for scope**

Run:

```bash
git diff -- "docs/AI Chess Rivals - Constitution.md"
```

Expected: only the local-development paragraph changes. There should be no unrelated Spring AI, provider, phase-boundary, deployment, or formatting-policy edits.

- [ ] **Step 6: Run whitespace validation**

```bash
git diff --check
```

Expected: no output and exit code 0.

- [ ] **Step 7: Run the repository verifier**

On Windows:

```powershell
.\scripts\verify.ps1
```

On POSIX:

```sh
./scripts/verify.sh
```

Expected for this documentation-only change: backend verification remains green. If frontend verification still stops on the same pre-existing Prettier violations in unchanged client files already documented by PR #47, record that fact exactly rather than changing unrelated frontend files.

- [ ] **Step 8: Commit the correction**

```bash
git add "docs/AI Chess Rivals - Constitution.md"
git commit -m "docs: align local development workflow"
```

---

### Task 2: Re-check Issue #37 Acceptance After the Fix

**Files:**
- Review only: `docs/AI Chess Rivals - Constitution.md`
- Review only: `docs/AI Chess Rivals - Implementation Strategy.md`
- Review only: `docs/AI Chess Rivals - Tech Stack.md`
- Review only: `AGENTS.md`
- Review only: `.agents/AGENTS.md`

**Interfaces:**
- Consumes: the completed Task 1 correction and issue #37 acceptance criteria.
- Produces: evidence that PR #47 no longer has the blocking documentation inconsistency.

- [ ] **Step 1: Re-check the runtime/deployment acceptance criterion**

Confirm all relevant documentation now agrees on:

```text
Java 25
Spring Boot 4.1.0
PostgreSQL 17
Normal local DB: Docker Compose
Normal local backend: Maven/IDE
Normal local frontend: Vite
Production backend: Docker + GraalVM Native Image
Production Stockfish: Linux binary bundled with backend container
```

- [ ] **Step 2: Confirm the correction did not regress the other issue #37 requirements**

Verify that the touched branch still states:

```text
Spring AI is required for Phase 2.
Groq is primary.
Gemini is the only automatic fallback.
Provider/model names are environment-configurable.
Stockfish owns chess decisions.
LLMs own entertainment only.
Tool calling, chat memory, autonomous agents, and multi-step workflows are Phase 3 concerns.
Both agent-guidance files link the formatting guide with repository-relative links.
Formatting is applied before verification.
Spotless/Google Java Format and Prettier remain authoritative.
```

- [ ] **Step 3: Report completion on PR #47**

Add a short PR comment stating that the Constitution's local-development wording was corrected to match `AGENTS.md`, `README.md`, and `docs/BUILD_AND_VERIFY.md`, and include the verification result from Task 1.

Do not claim the frontend verifier passed if it still stops on the previously documented unchanged Prettier violations.