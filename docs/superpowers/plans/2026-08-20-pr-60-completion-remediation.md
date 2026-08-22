# PR #60 Completion Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do not use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the remaining non-code blockers on PR #60 so issue #46 can be truthfully completed: isolate the existing frontend Prettier baseline, make the forced-failover recipe deterministic, get the root verifier green, perform the required manual/runtime acceptance, and record only observed evidence.

**Architecture:** Do not redesign the working AI observability implementation. Treat the current PR code as accepted unless verification exposes a reproducible defect. Keep unrelated frontend formatting outside PR #60, then rebase the feature branch and finish only the issue-specific documentation and acceptance work.

**Tech Stack:** Git, PowerShell, npm/Prettier/Vitest/TypeScript/Vite, Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, Micrometer/Actuator, Docker/PostgreSQL, React 19, GitHub Actions.

**Requirements:**
- Issue #46: `https://github.com/krishna916/ai-chess-rivals/issues/46`
- PR #60: `https://github.com/krishna916/ai-chess-rivals/pull/60`
- Original implementation plan: `docs/superpowers/plans/2026-08-20-phase-2-ai-observability-resilience-acceptance.md`
- Verification guide: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Do not redesign `AiGatewayMetrics`, `FailoverAiChatGateway`, provider configuration, MDC correlation, or frontend match activity unless verification exposes a real defect.
- Do not add dependencies, migrations, public API/WebSocket changes, dashboards, tracing, retries, persistence, or frontend features.
- Do not mix the existing frontend Prettier cleanup into PR #60.
- Do not weaken `client/scripts/verify.js`; it must continue to run `format:check`, `typecheck`, `lint`, `test`, and `build` in that order.
- Groq remains `8s`; Gemini remains `12s`; same-provider retries remain disabled.
- Automated tests remain provider-network-free.
- Never commit or paste real provider credentials.
- Check manual acceptance items only after the corresponding runtime/browser/provider interaction actually occurred.
- If Docker, browser access, or valid provider credentials are unavailable, stop at that gate and leave PR #60 draft.
- Keep PR #60 draft until every issue #46 acceptance criterion is genuinely satisfied.

---

### Task 1: Fix the Pre-Existing Frontend Prettier Baseline in a Separate PR

**Branch:** `chore/frontend-prettier-baseline`

**Files:**
- Modify: only files under `client/` changed by the repository's existing Prettier formatter.
- Do not intentionally modify application behavior, dependencies, tests, or verification scripts.

**Produces:** A formatting-only commit that makes the existing frontend verifier green on `master`.

- [ ] **Step 1: Start from a clean current `master`**

```powershell
git status --short
git fetch origin
git switch master
git pull --ff-only origin master
```

Expected: clean working tree.

- [ ] **Step 2: Reproduce the current formatting failure on `master`**

```powershell
Push-Location client
try {
    npm.cmd run format:check
}
finally {
    Pop-Location
}
```

Expected before cleanup: FAIL on the existing unformatted client files. This proves the root-verifier problem predates PR #60.

- [ ] **Step 3: Create the dedicated baseline branch and apply only Prettier**

```powershell
git switch -c chore/frontend-prettier-baseline
Push-Location client
try {
    npm.cmd run format
}
finally {
    Pop-Location
}
```

If the branch already exists, stop and inspect it instead of overwriting it.

- [ ] **Step 4: Inspect the formatter diff**

```powershell
git status --short
git diff --name-only
git diff --stat
git diff --check
```

Expected:
- every changed path is under `client/`;
- no server/docs/infrastructure change;
- no dependency/version change;
- formatting-only diff;
- `git diff --check` passes.

- [ ] **Step 5: Run the full frontend verifier**

```powershell
Push-Location client
try {
    npm.cmd run verify
}
finally {
    Pop-Location
}
```

Expected: `format:check`, `typecheck`, `lint`, `test`, and `build` all PASS.

- [ ] **Step 6: Commit and push the isolated cleanup**

```powershell
git add client
git diff --cached --check
git commit -m "style: align frontend prettier baseline"
git push -u origin chore/frontend-prettier-baseline
```

- [ ] **Step 7: Open and merge the formatting-only PR**

If GitHub CLI is available:

```powershell
gh pr create --base master --head chore/frontend-prettier-baseline --title "style: align frontend prettier baseline" --body "Formatting-only cleanup of the existing frontend Prettier baseline. No behavior, dependency, API, or configuration changes. npm run verify passes. Kept separate so PR #60 stays scoped to issue #46."
```

Require green frontend CI, review the diff as formatting-only, and merge it into `master`.

**Checkpoint:** Do not continue Task 2 until this formatting PR is merged.

---

### Task 2: Rebase PR #60 onto the Clean Baseline

**Branch:** `feature/issue-46-ai-observability`

**Files:** No intended changes.

- [ ] **Step 1: Confirm updated `master` is formatted**

```powershell
git fetch origin
git switch master
git pull --ff-only origin master
Push-Location client
try {
    npm.cmd run format:check
}
finally {
    Pop-Location
}
```

Expected: PASS. If it fails, stop; the baseline is not actually fixed.

- [ ] **Step 2: Rebase the feature branch**

```powershell
git switch feature/issue-46-ai-observability
git rebase origin/master
```

Preserve PR #60 observability behavior and the merged formatting baseline. Do not add unrelated cleanup while resolving conflicts.

- [ ] **Step 3: Verify PR #60 no longer contains mass frontend formatting**

```powershell
git status --short
git diff --stat origin/master...HEAD
git diff --name-only origin/master...HEAD
```

Expected: issue #46 implementation/docs only; no formatting-baseline client diff.

- [ ] **Step 4: Push the rebased branch safely**

```powershell
git push --force-with-lease origin feature/issue-46-ai-observability
```

Never use plain `--force`.

---

### Task 3: Make `BUILD_AND_VERIFY.md` Failover Instructions Mechanical

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

- [ ] **Step 1: Replace the vague "local stub" instruction with the closed-local-port recipe**

In the Phase 2 verification section, document these runtime values exactly except for the two developer-owned Gemini values, which remain local secrets/configuration and must never be committed:

```text
AI_ENABLED=true
AI_GROQ_API_KEY=forced-failure-not-a-secret
AI_GROQ_BASE_URL=http://127.0.0.1:9/v1
AI_GROQ_MODEL=forced-failure
AI_GEMINI_API_KEY=<use the valid Gemini key already present in the local secret environment>
AI_GEMINI_MODEL=<use the valid Gemini model already configured locally>
GAME_MOVE_THINK_TIME_MILLIS=0
GAME_MOVE_DELAY_MIN=0s
GAME_MOVE_DELAY_MAX=0s
GAME_MAX_PLIES=12
```

Document the expected path as:

```text
local Groq connection failure -> fallback activation target=gemini -> Gemini response
```

Also state that `AI_GROQ_BASE_URL` must be restored/cleared after the check.

- [ ] **Step 2: Expand the existing fast-mode snippet**

Replace the two-variable fast-mode snippet with:

```powershell
$env:GAME_MOVE_THINK_TIME_MILLIS = "0"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:GAME_MAX_PLIES = "12"
```

- [ ] **Step 3: Review and commit the docs-only change**

```powershell
git diff -- docs/BUILD_AND_VERIFY.md
git diff --check
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: make phase 2 failover verification mechanical"
```

Confirm no real key/token value exists in the diff.

---

### Task 4: Get the Canonical Root Verifier Green

**Files:** Verify only unless a reproducible PR #60 defect appears.

- [ ] **Step 1: Disable providers and run the root verifier**

From repository root:

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

Expected final sequence:

```text
backend Maven verify -> PASS
frontend format:check -> PASS
frontend typecheck -> PASS
frontend lint -> PASS
frontend Vitest -> PASS
frontend production build -> PASS
```

- [ ] **Step 2: Do not mask any failure**

Use this rule if red:

1. `format:check` red again -> verify Task 1 merge/rebase; do not modify `verify.js`.
2. backend issue in a PR #60 file -> reproduce with a focused Maven test and fix only that defect.
3. frontend non-format failure also exists on current `master` -> treat it as another baseline problem outside PR #60.
4. failure exists only on PR #60 -> fix that regression and rerun the focused check, then rerun the root verifier.

Do not proceed to final acceptance while the root verifier is red.

---

### Task 5: Run Credential-Free Full-Stack Resilience Acceptance

**Files:** No intended source changes.

- [ ] **Step 1: Start local PostgreSQL**

```powershell
Push-Location server
try {
    docker compose up -d postgres
    docker compose ps
}
finally {
    Pop-Location
}
```

If Docker is unavailable, stop and leave the manual acceptance incomplete.

- [ ] **Step 2: Start backend in fast AI-disabled mode**

In a backend terminal:

```powershell
cd server
$env:AI_ENABLED = "false"
$env:GAME_MOVE_THINK_TIME_MILLIS = "0"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:GAME_MAX_PLIES = "12"
$env:OWNER_CONTROL_TOKEN = [Convert]::ToHexString(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLower()
.\mvnw.cmd spring-boot:run
```

Expected: app on `8082`, management on `8081`, no provider network call.

- [ ] **Step 3: Start frontend**

```powershell
cd client
npm.cmd run dev
```

Expected: `http://localhost:5173`.

- [ ] **Step 4: Complete an AI-disabled fast match**

Using the UI:
1. Start a match with two distinct personalities.
2. Observe move/dialogue activity.
3. Let the short match complete.
4. Confirm deterministic dialogue fallback never stops chess progression.

- [ ] **Step 5: Confirm disabled-mode response metric**

```powershell
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.responses | ConvertTo-Json -Depth 8
```

Expected tags include:

```text
source=deterministic_fallback
reason=ai_disabled
```

- [ ] **Step 6: Exercise lifecycle behavior**

Perform actual browser observations:
1. Refresh after dialogue exists; verify authoritative activity returns without duplicates.
2. Toggle browser network offline then online while backend stays up; verify reconnect/hydration without duplication or reordering.
3. Stop an in-progress match and Resume; verify old dialogue remains ordered and new activity continues from authoritative state.

Start additional fast matches if `GAME_MAX_PLIES=12` completes too quickly for all interactions.

---

### Task 6: Run Four-Personality and Random-Rivalry Provider Acceptance

**Files:** No intended source changes.

- [ ] **Step 1: Require valid provider credentials from local secret storage**

Runtime configuration must include `AI_ENABLED=true`, valid Groq key/model, and valid Gemini key/model. Never print or commit the secret values.

If valid provider credentials are unavailable, stop here and leave PR #60 draft.

- [ ] **Step 2: Ensure normal Groq endpoint and fast pacing**

```powershell
Remove-Item Env:AI_GROQ_BASE_URL -ErrorAction SilentlyContinue
$env:AI_ENABLED = "true"
$env:GAME_MOVE_THINK_TIME_MILLIS = "0"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:GAME_MAX_PLIES = "12"
```

Restart the backend using the valid local provider configuration.

- [ ] **Step 3: Observe Blaze vs Vesper**

Verify both generate dialogue and their rendered voices visibly match:
- Blaze: high-energy/showboat.
- Vesper: dry/surgical strategist.

Also observe at least one line grounded in the current chess event or recent banter rather than a generic unrelated quip.

- [ ] **Step 4: Observe Gremlin vs Regent**

Verify both generate dialogue and their rendered voices visibly match:
- Gremlin: absurdist/chaos.
- Regent: pompous chess aristocrat.

- [ ] **Step 5: Exercise Random Rivalry once**

Start a Random Rivalry match and verify:
- two distinct seeded personalities are selected;
- dialogue is attributed to the correct selected speaker.

Do not require a particular random pair.

---

### Task 7: Run Controlled Groq -> Gemini Failover Acceptance

**Files:** No intended source changes.

- [ ] **Step 1: Restart backend with a deterministic local Groq failure**

Set these non-secret Groq values while preserving valid local Gemini key/model values:

```powershell
$env:AI_ENABLED = "true"
$env:AI_GROQ_API_KEY = "forced-failure-not-a-secret"
$env:AI_GROQ_BASE_URL = "http://127.0.0.1:9/v1"
$env:AI_GROQ_MODEL = "forced-failure"
$env:GAME_MOVE_THINK_TIME_MILLIS = "0"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:GAME_MAX_PLIES = "12"
```

Expected: no request can reach real Groq because the base URL is loopback port `9`.

- [ ] **Step 2: Complete a short match**

Expected: Groq connection failure does not stop chess; Gemini supplies dialogue and the match completes.

If Gemini also fails and deterministic fallback completes the match, resilience is still proven but the specific Groq -> Gemini success criterion is **not** satisfied. Diagnose Gemini before checking that acceptance item.

- [ ] **Step 3: Inspect custom gateway metrics**

```powershell
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.provider.duration | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.fallback.activations | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.responses | ConvertTo-Json -Depth 8
```

Required observed semantics:

```text
provider=groq,outcome=failure
provider=gemini,outcome=success
target=gemini,reason=failure
source=gemini,reason=fallback
```

Across Tasks 5-7, metrics must also distinguish deterministic disabled-mode responses and successful direct Groq responses when Groq succeeded during the normal provider run.

- [ ] **Step 4: Inspect Spring AI metrics**

```powershell
(Invoke-RestMethod http://localhost:8081/actuator/metrics).names | Select-String "gen_ai"
```

If `gen_ai.client.token.usage` is present, record that provider token usage was available. If absent, record exactly:

```text
token usage metric not reported by provider in this acceptance run
```

Do not invent zero-token values or add token estimation.

- [ ] **Step 5: Inspect logs for correlation and leakage**

For dialogue-triggered calls, verify safe metadata includes provider/outcome plus `matchId`, `triggerType`, and `triggerPly`.

Verify normal logs contain none of:
- full prompt text;
- raw provider response text;
- deterministic fallback dialogue text;
- Groq/Gemini API keys;
- Authorization header values.

Any leak blocks acceptance.

- [ ] **Step 6: Clear forced-failure Groq overrides after the run**

```powershell
Remove-Item Env:AI_GROQ_BASE_URL -ErrorAction SilentlyContinue
Remove-Item Env:AI_GROQ_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:AI_GROQ_MODEL -ErrorAction SilentlyContinue
```

Restore normal Groq key/model only from the user's local secret source when needed later.

---

### Task 8: Replace the Incomplete Acceptance Record with Observed Evidence

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

- [ ] **Step 1: Get the actual execution date**

```powershell
Get-Date -Format "yyyy-MM-dd"
```

Use that returned date in the acceptance heading.

- [ ] **Step 2: Update the Phase 2 checklist only from observations made in Tasks 5-7**

All issue-required manual items must be genuinely observed before final completion:

```text
four personalities
contextual dialogue
random rivalry
refresh without duplicates
reconnect without duplicates/reordering
stop/resume ordering
AI-disabled completion
Groq -> Gemini failover
safe correlated logs
metric differentiation
```

If any item remains unobserved, leave it unchecked and keep PR #60 draft.

- [ ] **Step 3: Replace stale/incomplete acceptance bullets**

Update the current record so it states the final observed facts for:
- passing root verifier;
- AI-disabled full-stack match;
- Blaze/Vesper and Gremlin/Regent;
- Random Rivalry;
- contextual dialogue;
- refresh/reconnect/stop-resume;
- controlled Groq failure activating Gemini;
- custom metrics;
- Spring AI token metric present or documented unavailable;
- safe logs with no prompt/response/credential leakage.

Do not paste generated banter, prompts, raw responses, or secrets into the record.

- [ ] **Step 4: Review and commit acceptance evidence**

```powershell
git diff -- docs/BUILD_AND_VERIFY.md
git diff --check
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: complete phase 2 acceptance"
```

---

### Task 9: Final Verification and PR Readiness Gate

**Files:** No intended changes unless final verification exposes a real defect.

- [ ] **Step 1: Run the canonical verifier again after the acceptance-record commit**

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

Expected: complete PASS.

- [ ] **Step 2: Run git hygiene checks**

```powershell
git status --short
git diff --check origin/master...HEAD
git diff --stat origin/master...HEAD
git diff --name-only origin/master...HEAD
```

Expected:
- clean working tree;
- no whitespace errors;
- no frontend formatting-baseline noise in PR #60;
- no `.env`/credential file tracked.

- [ ] **Step 3: Push final feature commits**

```powershell
git push origin feature/issue-46-ai-observability
```

If non-fast-forward is required because of Task 2's rebase:

```powershell
git push --force-with-lease origin feature/issue-46-ai-observability
```

- [ ] **Step 4: Require green hosted CI on the new final head**

Do not rely on the older green PR SHA. Backend and native-image checks must remain green, plus any check triggered by the final diff.

- [ ] **Step 5: Re-read issue #46 acceptance criteria**

Every statement must now be true:

```text
Metrics distinguish Groq, Gemini fallback, and deterministic fallback outcomes.
Logs correlate calls without leaking prompt/response content or secrets.
Tests require no real paid provider calls.
A provider outage cannot prevent a match from completing.
Refresh/reconnect and stop/resume preserve correct dialogue ordering.
Manual acceptance verifies distinct personalities, contextual replies, random rivalry, and graceful failover.
Root verification passes.
BUILD_AND_VERIFY.md contains a dated Phase 2 acceptance record.
```

If any statement is false, leave PR #60 draft.

- [ ] **Step 6: Mark PR #60 ready only after every gate is satisfied**

If GitHub CLI is available:

```powershell
gh pr ready 60
```

Otherwise use GitHub's **Ready for review** action.

Do not manually close issue #46; `Closes #46` on PR #60 should close it when merged.

---

## Final Self-Review Checklist

- [ ] Prettier baseline fixed and merged in a separate formatting-only PR.
- [ ] PR #60 rebased onto the fixed `master` baseline.
- [ ] PR #60 contains no mass formatting cleanup.
- [ ] Closed-local-port Groq failover recipe documented.
- [ ] Fast mode includes think time `0`, delay `0s`, and max plies `12`.
- [ ] Root `scripts\verify.ps1` passes on final PR head.
- [ ] AI-disabled match actually completed.
- [ ] Blaze, Vesper, Gremlin, and Regent actually exercised with provider dialogue.
- [ ] Random Rivalry actually exercised.
- [ ] Contextual dialogue actually observed.
- [ ] Refresh, reconnect, and stop/resume actually exercised without duplication/reordering.
- [ ] Controlled Groq failure actually activated Gemini and the match completed.
- [ ] Gateway metrics actually distinguish Groq failure, Gemini fallback/success, and deterministic disabled mode.
- [ ] Spring AI token metric presence/absence actually recorded.
- [ ] Logs actually verified for safe correlation and no leakage.
- [ ] Acceptance record contains only observed facts.
- [ ] Hosted CI green on final PR head.
- [ ] PR remains draft until every required item above is true.

## Explicit Non-Goals

Do not add tracing, dashboards, Grafana/Prometheus deployment, new provider abstractions, retries, raw request persistence, new schema, frontend redesign, extra agents/memory/orchestration, load testing, or analytics.

This remediation is complete when issue #46's existing acceptance contract is truthfully satisfied—not when more observability features have been invented.