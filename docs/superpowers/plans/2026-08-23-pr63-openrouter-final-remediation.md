# PR 63 OpenRouter Final Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish PR #63 by restoring the safe production native-build default, landing the original issue #62 implementation plan in the PR history, recording the already-completed real OpenRouter acceptance evidence, correcting PR metadata, and re-running final verification without changing the working OpenRouter routing implementation.

**Architecture:** This is a remediation-only pass. Keep the current OpenRouter primary -> remote fallback -> deterministic fallback implementation unchanged. The only runtime behavior change is restoring the Dockerfile's direct/production native-build default to the AI-enabled topology while preserving Docker Compose's existing local override; all other work is documentation, evidence, and PR hygiene.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, GraalVM Native Image, Docker/Docker Compose, GitHub Actions, PostgreSQL 17 + Flyway, React/TypeScript, existing repository verification scripts.

**Spec:** GitHub issue #62 plus the approved implementation plan `docs/superpowers/plans/2026-08-23-openrouter-cost-aware-provider-routing.md` and the PR #63 review findings. No new feature design is introduced by this remediation.

## Global Constraints

- Do not change `AiProviderConfiguration`, `FailoverAiChatGateway`, `AiProperties`, prompt generation, dialogue validation, personality behavior, persistence semantics, or chess/match logic unless final verification exposes a concrete regression.
- Do not add dependencies, rate limiters, retry layers, provider registries, Redis, Resilience4j, additional agents, or Phase 3 behavior.
- Preserve one OpenRouter key/base URL, specific `:free` primary model, one cheap remote fallback model, `maxRetries(0)`, and deterministic local fallback.
- Preserve normal match pacing at `7s`-`12s` and the explicit `0s` local/test override.
- Preserve `server/docker-compose.yml` mapping `AI_NATIVE_BUILD_ENABLED: ${AI_ENABLED:-false}` so local Compose builds continue to select the topology matching `AI_ENABLED`.
- Preserve the GitHub Actions AI-enabled native verification. It may continue passing `AI_NATIVE_BUILD_ENABLED=true` explicitly.
- Never print, commit, or paste `AI_OPENROUTER_API_KEY` or authorization headers. Model IDs are non-secret and may be recorded.
- Record only acceptance observations that actually occurred. The user has confirmed that both the real primary-success OpenRouter run and the controlled primary-failure -> paid-fallback-success run passed; recover the exact non-secret model IDs from the execution environment before documenting them when possible.
- Do not rename the current PR branch. The branch name is historical noise and changing it adds no product value.
- Do not rewrite old historical plans/specs to remove Groq/Gemini references.

---

### Task 1: Bring the Approved Issue #62 Plan Into PR #63

**Files:**
- Add through cherry-pick: `docs/superpowers/plans/2026-08-23-openrouter-cost-aware-provider-routing.md`

**Interfaces:**
- Source commit: `3d533b8db7e876d071501bc72f1df3e1fce857c9` (`docs: plan OpenRouter provider routing migration`).
- Current PR: #63.
- Current PR head branch: `feature/issue-46-phase-2-observability-acceptance`.

- [ ] **Step 1: Confirm the original plan is absent from the current PR branch**

Run:

```bash
git rev-parse --abbrev-ref HEAD
git cat-file -e HEAD:docs/superpowers/plans/2026-08-23-openrouter-cost-aware-provider-routing.md
```

Expected:
- current branch is `feature/issue-46-phase-2-observability-acceptance`;
- the second command fails because the plan commit currently lives on `feature/issue-62-openrouter-routing`, not the PR branch.

- [ ] **Step 2: Cherry-pick the approved plan commit**

Run:

```bash
git cherry-pick 3d533b8db7e876d071501bc72f1df3e1fce857c9
```

Expected: clean cherry-pick adding only `docs/superpowers/plans/2026-08-23-openrouter-cost-aware-provider-routing.md`.

- [ ] **Step 3: Verify the cherry-pick did not alter implementation files**

Run:

```bash
git show --stat --oneline HEAD
git show --name-only --format='' HEAD
```

Expected: only the issue #62 plan file is listed.

Do not edit the historical plan while cherry-picking it; this remediation plan is the place for the final follow-up instructions.

---

### Task 2: Restore the Direct/Production Native Build to AI-Enabled by Default

**Files:**
- Modify: `server/Dockerfile`
- Modify: `server/README.md`
- Modify: `docs/AI Chess Rivals - Tech Stack.md`
- Verify only: `server/docker-compose.yml`
- Verify only: `.github/workflows/ci.yml`

**Interfaces:**
- Direct/production `docker build` default: AI-enabled AOT topology.
- Docker Compose local build behavior: follows `AI_ENABLED`, defaulting to disabled.
- Runtime provider credentials/models remain runtime environment values and must not be baked into the final image.

- [ ] **Step 1: Change the Dockerfile build argument default back to enabled**

In `server/Dockerfile`, replace:

```dockerfile
# Keep direct Docker builds aligned with the runtime default; CI and production AI images must
# explicitly pass AI_NATIVE_BUILD_ENABLED=true. Docker Compose maps this arg from AI_ENABLED.
ARG AI_NATIVE_BUILD_ENABLED=false
```

with:

```dockerfile
# Direct/production Docker builds compile the AI-enabled topology by default.
# Docker Compose overrides this arg from AI_ENABLED so local disabled mode remains available.
ARG AI_NATIVE_BUILD_ENABLED=true
```

Do not change the non-secret OpenRouter AOT placeholders below this block.

- [ ] **Step 2: Verify Compose still owns the local topology override**

Run:

```bash
git grep -n 'AI_NATIVE_BUILD_ENABLED: ${AI_ENABLED:-false}' -- server/docker-compose.yml
```

Expected: exactly one hit. Do not change it.

- [ ] **Step 3: Keep native CI explicitly testing the enabled topology**

Run:

```bash
git grep -n 'AI_NATIVE_BUILD_ENABLED=true' -- .github/workflows/ci.yml
```

Expected: the existing native build job still explicitly passes `AI_NATIVE_BUILD_ENABLED=true`. No CI logic change is required for this remediation.

- [ ] **Step 4: Restore the README native-build contract**

In `server/README.md`, make the native-build contract say all of the following, without introducing a new Render-specific build setting:

```text
Spring AOT fixes AI bean topology at native-image build time.
Direct/production Docker builds compile the AI-enabled topology by default using only non-secret OpenRouter placeholder values during AOT processing.
Real OpenRouter keys and model IDs are supplied at runtime and are not copied into the image.
Docker Compose maps AI_ENABLED to AI_NATIVE_BUILD_ENABLED so local AI-disabled images remain straightforward.
Changing AI_ENABLED for a Compose native image requires rebuilding the backend.
```

Delete the current instructions that require Render to configure a separate `AI_NATIVE_BUILD_ENABLED=true` build argument. Keep the runtime Render variables `AI_ENABLED=true`, `AI_OPENROUTER_API_KEY`, `AI_OPENROUTER_BASE_URL`, `AI_OPENROUTER_PRIMARY_MODEL`, `AI_OPENROUTER_FALLBACK_MODEL`, and both timeout values.

- [ ] **Step 5: Restore the Tech Stack native topology description**

In `docs/AI Chess Rivals - Tech Stack.md`, replace the current statement that the Dockerfile defaults to the disabled topology with the production contract:

```text
The production GraalVM artifact treats AI enablement as a build-time Spring AOT choice. Direct production Docker builds compile the OpenRouter primary/fallback + enabled AiChatGateway topology using non-secret placeholders only for AOT processing; real provider keys and model names are supplied at runtime. Docker Compose maps AI_ENABLED into the native build argument so local AI-disabled images remain straightforward. CI starts the actual native image and verifies the enabled application-owned provider/gateway chain.
```

Keep the existing OpenRouter provider policy and native topology marker unchanged.

- [ ] **Step 6: Run static contract checks**

PowerShell:

```powershell
$dockerfile = Get-Content server/Dockerfile -Raw
if ($dockerfile -notmatch '(?m)^ARG AI_NATIVE_BUILD_ENABLED=true$') { throw 'Direct Docker AI build default is not enabled' }
if ($dockerfile -match '(?m)^ARG AI_NATIVE_BUILD_ENABLED=false$') { throw 'Disabled direct Docker default still present' }

git grep -n -E 'Dockerfile defaults to the AI-disabled topology|production AI images must pass|Render.*AI_NATIVE_BUILD_ENABLED=true' -- server/README.md "docs/AI Chess Rivals - Tech Stack.md"
if ($LASTEXITCODE -eq 0) { throw 'Stale disabled-production build guidance remains' }
```

Expected: PowerShell exits successfully and the stale-guidance grep has no hits.

- [ ] **Step 7: Commit the native-build contract fix**

Run:

```bash
git add server/Dockerfile server/README.md "docs/AI Chess Rivals - Tech Stack.md"
git commit -m "fix: restore production AI native build default"
```

---

### Task 3: Record the Completed Real OpenRouter Acceptance Evidence

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Acceptance evidence must cover a real primary-success run.
- Acceptance evidence must cover controlled primary failure -> one paid OpenRouter fallback -> successful dialogue while the match continues.
- Exact model IDs are non-secret; the API key must never be displayed or recorded.

- [ ] **Step 1: Recover only the non-secret model IDs used during acceptance**

PowerShell:

```powershell
$primaryModel = $env:AI_OPENROUTER_PRIMARY_MODEL
$fallbackModel = $env:AI_OPENROUTER_FALLBACK_MODEL

if ([string]::IsNullOrWhiteSpace($primaryModel) -or [string]::IsNullOrWhiteSpace($fallbackModel)) {
    if (Test-Path server/.env) {
        Get-Content server/.env |
            Where-Object { $_ -match '^AI_OPENROUTER_(PRIMARY|FALLBACK)_MODEL=' }
    }
} else {
    "AI_OPENROUTER_PRIMARY_MODEL=$primaryModel"
    "AI_OPENROUTER_FALLBACK_MODEL=$fallbackModel"
}
```

Expected: only model-ID lines are printed. Do **not** run a command that prints the complete `.env` file or `AI_OPENROUTER_API_KEY`.

If the environment confirms the planned models, the expected IDs are:

```text
inclusionai/ling-3.0-flash:free
~deepseek/deepseek-v4-flash-latest
```

If the successful acceptance used different model IDs, record the observed IDs instead of changing them to these examples.

- [ ] **Step 2: Update the dated `2026-08-23` acceptance record**

In `docs/BUILD_AND_VERIFY.md`, change the previously unchecked real-provider bullets to checked observations reflecting the completed run. The final record must explicitly state:

```text
- real OpenRouter primary-success dialogue was observed;
- the specific primary model ID actually used;
- controlled primary failure was observed;
- exactly one remote-fallback path was activated for that failed primary generation;
- the remote fallback returned valid dialogue;
- the fallback model ID actually used;
- the chess match continued after fallback;
- no provider credential, prompt, or raw response was added to the acceptance record or normal application logs;
- 0s pacing, if used for this manual acceptance, was verification-only and normal deployment pacing remains 7s-12s.
```

Also retain the already-recorded automated failover and deterministic-fallback coverage.

- [ ] **Step 3: Correct native verification evidence using the current CI result**

The acceptance record currently notes that the local Docker Desktop native build ended with an EOF. Preserve that as local evidence, but add the stronger repository evidence:

```text
GitHub Actions native-image verification passed on PR #63, including production native image build, AI-enabled topology startup verification, and the no-baked-provider-environment check.
```

Do not claim the local Docker Desktop build itself passed.

- [ ] **Step 4: Review the acceptance section for contradictory unchecked statements**

Run:

```bash
git grep -n -E 'Real OpenRouter.*not run|controlled OpenRouter.*not run|no provider request was made|no provider credential was available' -- docs/BUILD_AND_VERIFY.md
```

Expected: no hit in the new issue #62 / PR #63 acceptance record. Older dated historical records may remain if they accurately describe earlier runs; do not rewrite history merely to eliminate those phrases globally.

- [ ] **Step 5: Commit the acceptance evidence**

Run:

```bash
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: record OpenRouter runtime acceptance"
```

---

### Task 4: Correct PR #63 Metadata and Verification Summary

**Files:**
- GitHub PR #63 metadata only; no repository source file required.

**Interfaces:**
- Issue implemented: #62, not #46.
- PR title may remain `feat: route Phase 2 dialogue through OpenRouter`.
- Branch name remains unchanged.

- [ ] **Step 1: Prepare the corrected PR body**

Use this body, adjusting only exact model IDs if the acceptance run used models different from the planned examples:

```markdown
## Summary

Implements issue #62 from the approved OpenRouter migration plan:

- routes Spring AI dialogue through OpenRouter primary/fallback models
- keeps one-shot remote failover followed by deterministic fallback
- migrates persisted Groq/Gemini response-source values to provider-neutral roles
- adds safe metrics/logging, bounded timeouts, and 7s-12s production pacing
- removes the Gemini-specific Spring AI/SDK path
- updates CI, native/AOT configuration, environment examples, deployment guidance, and active architecture documentation

## Verification

- Full local verifier passed with `AI_ENABLED=false`: backend tests, Spotless, SpotBugs, frontend checks/tests, and production build.
- PostgreSQL 17 persistence verification passed, including the V4-to-V5 legacy response-source migration.
- GitHub Actions CI passed backend, frontend, and native-image verification, including AI-enabled topology startup and no-baked-provider-environment checks.
- Real OpenRouter primary-success acceptance passed with valid dialogue and continued match progress.
- Controlled primary failure -> one OpenRouter paid-fallback attempt -> successful valid dialogue passed and the match continued.
- Local Docker Desktop native compilation previously ended with an infrastructure EOF; GitHub Actions native verification subsequently passed on the PR head.

See `docs/BUILD_AND_VERIFY.md` for the detailed acceptance record.
```

- [ ] **Step 2: Update PR #63**

Using GitHub CLI from the repository root:

```bash
gh pr edit 63 --body-file .git/pr63-body.md
```

Before running it, write the exact body from Step 1 to `.git/pr63-body.md`. The `.git` directory is not versioned, so this cannot accidentally enter the PR.

Expected: PR body references issue #62 and no longer says the real OpenRouter acceptance was not run.

- [ ] **Step 3: Verify PR metadata**

Run:

```bash
gh pr view 63 --json number,title,isDraft,body,headRefName,baseRefName
```

Expected:
- `number` = `63`;
- body says `issue #62`;
- body records successful real primary and fallback acceptance;
- head remains `feature/issue-46-phase-2-observability-acceptance`;
- base remains `master`.

Do not rename the head branch.

---

### Task 5: Final Verification, Push, and Ready-for-Review Gate

**Files:**
- Verification only unless a concrete failure exposes a real defect.

**Interfaces:**
- Repository verifier must pass.
- Current PR CI must pass after the remediation commits are pushed.
- PR remains draft until these checks are green.

- [ ] **Step 1: Verify the remediation stayed scoped**

Run:

```bash
git status --short
git diff master...HEAD --name-only
```

Confirm this remediation introduced no new changes to the OpenRouter Java routing implementation beyond the already-existing PR #63 implementation. New remediation changes should be limited to:

```text
docs/superpowers/plans/2026-08-23-openrouter-cost-aware-provider-routing.md
docs/superpowers/plans/2026-08-23-pr63-openrouter-final-remediation.md
server/Dockerfile
server/README.md
docs/AI Chess Rivals - Tech Stack.md
docs/BUILD_AND_VERIFY.md
```

The PR still contains the original #62 implementation files from earlier commits; that is expected.

- [ ] **Step 2: Run whole-repository verification**

Windows:

```powershell
.\scripts\verify.ps1
```

POSIX:

```bash
./scripts/verify.sh
```

Expected: backend and frontend verification pass with zero failures.

- [ ] **Step 3: Run final stale-policy scans**

Run:

```bash
git grep -n -E 'AI_GROQ_|AI_GEMINI_|app\.ai\.groq|app\.ai\.gemini|enabled \(Groq -> Gemini\)' -- server .github AGENTS.md .agents docs/BUILD_AND_VERIFY.md "docs/AI Chess Rivals - Tech Stack.md" || true

git grep -n -E 'Dockerfile defaults to the AI-disabled topology|production AI images must pass|Render.*AI_NATIVE_BUILD_ENABLED=true' -- server/README.md "docs/AI Chess Rivals - Tech Stack.md" || true
```

Expected: no active-policy hits. Historical files under `docs/superpowers/plans/` are intentionally excluded from this scan.

- [ ] **Step 4: Push the remediation commits**

Run:

```bash
git push origin feature/issue-46-phase-2-observability-acceptance
```

- [ ] **Step 5: Wait synchronously for the new PR-head CI checks**

Run:

```bash
gh pr checks 63 --watch
```

Expected: backend verification, frontend verification, and native image verification all succeed on the new PR head.

Do not use the earlier green run as proof for the new remediation head.

- [ ] **Step 6: Verify the final PR diff and commit history**

Run:

```bash
git status --short
git log --oneline -10
gh pr diff 63 --name-only
```

Expected:
- working tree clean;
- the original issue #62 plan is now in PR history;
- the final remediation plan is in PR history;
- native-build contract and acceptance documentation commits are present;
- no unrelated implementation area was added.

- [ ] **Step 7: Mark PR #63 ready for review**

Only after Step 5 is green, run:

```bash
gh pr ready 63
```

Expected: PR is no longer draft.

- [ ] **Step 8: Trigger CodeRabbit if it does not automatically review after draft removal**

First inspect PR comments/reviews:

```bash
gh pr view 63 --comments
```

If CodeRabbit has started or completed a new review, do nothing. If the only CodeRabbit message still says the review was skipped because the PR was a draft, run exactly once:

```bash
gh pr comment 63 --body '@coderabbitai review'
```

Any new substantive CodeRabbit finding is a separate review item: validate it technically before changing code.

---

## Definition of Done

- [ ] Original issue #62 plan commit is included in PR #63.
- [ ] `server/Dockerfile` defaults `AI_NATIVE_BUILD_ENABLED=true` for direct/production Docker builds.
- [ ] Docker Compose still maps build topology from `AI_ENABLED` and defaults local Compose to AI-disabled.
- [ ] README and Tech Stack no longer require a special Render `AI_NATIVE_BUILD_ENABLED=true` build setting.
- [ ] Existing OpenRouter routing/failover implementation is unchanged by this remediation.
- [ ] `docs/BUILD_AND_VERIFY.md` records the successful real primary OpenRouter acceptance run.
- [ ] `docs/BUILD_AND_VERIFY.md` records the successful controlled primary-failure -> paid-fallback run and continued match progress.
- [ ] Exact non-secret model IDs are recorded when recoverable; no API key is exposed.
- [ ] Acceptance documentation distinguishes the local Docker Desktop EOF from the successful GitHub Actions native-image verification.
- [ ] PR body says issue #62, reflects current successful acceptance evidence, and no longer says provider acceptance was not run.
- [ ] Whole-repository verification passes on the remediation state.
- [ ] New PR-head backend, frontend, and native CI checks pass.
- [ ] PR #63 is marked ready for review only after the final checks are green.
- [ ] No unrelated architecture, dependency, retry, rate-limit, provider-framework, Phase 3, chess, or personality changes are introduced.
