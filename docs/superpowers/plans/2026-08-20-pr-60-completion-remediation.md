# PR #60 Completion Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do not use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the remaining non-code blockers on PR #60 so issue #46 can be truthfully completed: establish a clean frontend Prettier baseline outside the feature PR, make the forced-failover recipe deterministic, get the root verifier green, perform the required manual/runtime Phase 2 acceptance, and record only observed evidence.

**Architecture:** Do not redesign the working AI observability implementation. Treat the existing PR code as accepted unless a verification step exposes a real defect. Keep the unrelated frontend formatting baseline isolated in its own tiny branch/PR; after that merges, rebase `feature/issue-46-ai-observability` and finish the issue-specific docs/runtime acceptance there.

**Tech Stack:** Git, PowerShell, npm/Prettier/Vitest/TypeScript/Vite, Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, Micrometer/Actuator, Docker/PostgreSQL, React 19, GitHub Actions.

**Requirements:**
- Issue #46: `https://github.com/krishna916/ai-chess-rivals/issues/46`
- PR #60: `https://github.com/krishna916/ai-chess-rivals/pull/60`
- Original implementation plan: `docs/superpowers/plans/2026-08-20-phase-2-ai-observability-resilience-acceptance.md`
- Verification guide: `docs/BUILD_AND_VERIFY.md`

## Global Constraints

- Do **not** redesign `AiGatewayMetrics`, `FailoverAiChatGateway`, `DisabledAiChatGateway`, provider configuration, MDC correlation, or frontend match activity unless a verification step exposes a reproducible defect.
- Do **not** add dependencies, database migrations, public API changes, WebSocket contract changes, dashboards, tracing infrastructure, or frontend production features.
- Do **not** mix the existing frontend Prettier baseline cleanup into PR #60; isolate it in `chore/frontend-prettier-baseline`.
- Do **not** weaken, skip, reorder, or delete verification steps to make the root verifier green. `client/scripts/verify.js` must continue to run `format:check`, `typecheck`, `lint`, `test`, and `build` in that order.
- Do **not** change provider timeouts or retry policy. Groq remains `8s`; Gemini remains `12s`; same-provider retries remain disabled.
- Do **not** expose real Groq/Gemini keys in commits, logs, screenshots, PR comments, shell history copied into GitHub, or acceptance records.
- Automated tests must remain provider-network-free.
- Manual acceptance evidence is factual evidence, not inferred evidence. Check an item only after the corresponding browser/runtime/provider interaction was actually observed.
- If Docker, browser access, or valid provider credentials are unavailable, stop at the relevant manual acceptance gate and leave PR #60 in draft state.
- Keep PR #60 draft until every issue #46 acceptance criterion is satisfied, including a green root verifier and the complete manual acceptance run.

---

## File Map

### Separate formatting-baseline branch

**Modify by formatter only:**
- Whatever files under `client/` are reported by the current Prettier baseline when `npm.cmd run format` is executed from `client/`.

**Must not modify:**
- `client/package.json`
- `client/package-lock.json`
- `client/scripts/verify.js`
- application behavior, tests, dependencies, or configuration unless Prettier itself rewrites formatting in one of those files. If `package.json`, `package-lock.json`, or `client/scripts/verify.js` changes for any reason other than deterministic Prettier whitespace/formatting, stop and inspect before committing.

### PR #60 branch

**Modify:**
- `docs/BUILD_AND_VERIFY.md` — make forced-failover instructions mechanical and later replace the incomplete acceptance record with the actually observed final record.

**Verify without intended production changes:**
- `scripts/verify.ps1`
- `client/scripts/verify.js`
- existing backend/frontend/native verification already covered by PR #60.

---

### Task 1: Isolate and Fix the Existing Frontend Prettier Baseline

**Branch:** `chore/frontend-prettier-baseline`

**Files:**
- Modify: only files changed by `npm.cmd run format` under `client/`.
- Verify unchanged behavior through the existing frontend verifier.

**Interfaces:**
- Consumes: current `master` frontend source and current Prettier configuration.
- Produces: a formatting-only commit that allows `npm.cmd run format:check` and `npm.cmd run verify` to pass on `master` once merged.

- [ ] **Step 1: Confirm PR #60 work is committed before switching branches**

From repository root:

```powershell
git status --short
```

Expected: no output. If there are uncommitted changes, stop and resolve them before switching branches.

- [ ] **Step 2: Reproduce the baseline formatting failure from current `master`**

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

Expected before cleanup: `format:check` fails and reports the existing unformatted frontend files. This establishes that the failure is a `master` baseline problem, not a PR #60 regression.

Do not edit `client/scripts/verify.js` or remove `format:check` from verification.

- [ ] **Step 3: Create the dedicated formatting-only branch**

```powershell
git switch -c chore/frontend-prettier-baseline
```

If the branch already exists locally or remotely, stop and inspect it instead of overwriting it.

- [ ] **Step 4: Apply only the repository's configured Prettier formatter**

```powershell
Push-Location client
try {
    npm.cmd run format
}
finally {
    Pop-Location
}
```

Do not make manual refactors while this formatter branch is active.

- [ ] **Step 5: Inspect exactly what the formatter changed**

```powershell
git status --short
git diff --name-only
git diff --stat
git diff --check
```

Expected:
- every changed path is under `client/`;
- `git diff --check` exits successfully;
- changes are formatting-only;
- no dependency/version change exists;
- no generated build output is tracked.

If any server/docs/infrastructure file changed, revert it before continuing.

- [ ] **Step 6: Prove the full frontend verifier is green after formatting**

```powershell
Push-Location client
try {
    npm.cmd run verify
}
finally {
    Pop-Location
}
```

Expected sequence and result:

```text
format:check -> PASS
typecheck    -> PASS
lint         -> PASS
test         -> PASS
build        -> PASS
```

Do not continue if any non-formatting check fails. Investigate that as a separate defect rather than weakening verification.

- [ ] **Step 7: Commit only the formatting baseline**

```powershell
git add client
git diff --cached --check
git diff --cached --stat
git commit -m "style: align frontend prettier baseline"
```

Immediately inspect the commit:

```powershell
git show --stat --oneline HEAD
git show --check HEAD
```

Expected: formatting-only client changes and no whitespace errors.

- [ ] **Step 8: Push the isolated branch and open a tiny formatting-only PR**

```powershell
git push -u origin chore/frontend-prettier-baseline
```

If GitHub CLI is available:

```powershell
gh pr create --base master --head chore/frontend-prettier-baseline --title "style: align frontend prettier baseline" --body "Formatting-only cleanup of the existing frontend Prettier baseline. No behavior, dependency, API, or configuration changes. `npm run verify` passes on the branch. This is intentionally isolated so PR #60 can rebase onto a clean root-verification baseline."
```

If GitHub CLI is unavailable, create the same PR through GitHub using the pushed branch and the exact title/body above.

- [ ] **Step 9: Require green frontend CI and merge this baseline PR before continuing PR #60**

Expected CI for the formatting PR: frontend verification runs and passes because client files changed.

Do not cherry-pick the formatting commit into PR #60. Merge the formatting PR into `master`, then continue with Task 2 from the updated `master` history.

**Checkpoint:** Task 2 must not begin until the formatting-only PR is merged into `master`.

---

### Task 2: Rebase PR #60 onto the Clean Formatting Baseline

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- No intended file changes in this task.

**Interfaces:**
- Consumes: merged `chore/frontend-prettier-baseline` on `origin/master`.
- Produces: PR #60 rebased so its root verifier sees the corrected frontend baseline.

- [ ] **Step 1: Update remote state and verify the formatting fix is on `origin/master`**

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

Expected: `format:check` PASS on updated `master`.

If it still fails, stop. The baseline PR was either not merged or did not fix the complete baseline.

- [ ] **Step 2: Rebase PR #60 onto current `origin/master`**

```powershell
git switch feature/issue-46-ai-observability
git rebase origin/master
```

Resolve conflicts by preserving:
- PR #60 AI observability/resilience behavior;
- the merged `master` formatting baseline;
- current `docs/BUILD_AND_VERIFY.md` Phase 2 content.

Do not use conflict resolution as an opportunity for unrelated cleanup.

- [ ] **Step 3: Confirm the feature diff still contains only issue #46 work plus its plan documents**

```powershell
git status --short
git diff --stat origin/master...HEAD
git diff --name-only origin/master...HEAD
```

Expected: no mass frontend formatting diff remains in PR #60.

- [ ] **Step 4: Push the rebased branch safely**

```powershell
git push --force-with-lease origin feature/issue-46-ai-observability
```

Use `--force-with-lease`, never plain `--force`.

---

### Task 3: Make the Forced Groq -> Gemini Acceptance Recipe Mechanical

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Consumes: existing Phase 2 provider settings from `server/src/main/resources/application.yaml`.
- Produces: a deterministic failover recipe that needs no local Groq stub service and cannot accidentally call the real Groq endpoint.

- [ ] **Step 1: Replace the vague local-stub sentence in the Phase 2 verification section**

Find this current text:

```markdown
A forced Groq failure may be exercised safely by pointing `AI_GROQ_BASE_URL` at a local stub that deterministically fails;
do not use a production key against an uncontrolled endpoint.
```

Replace it with this exact operational recipe:

```markdown
For a controlled Groq -> Gemini failover run, do not start another stub service and do not send a request to the real Groq endpoint. Keep a valid local Gemini key/model, give Groq dummy non-secret values, and point Groq at a closed local port:

```text
AI_ENABLED=true
AI_GROQ_API_KEY=forced-failure-not-a-secret
AI_GROQ_BASE_URL=http://127.0.0.1:9/v1
AI_GROQ_MODEL=forced-failure
AI_GEMINI_API_KEY=<use the valid Gemini key already present in your local secret environment>
AI_GEMINI_MODEL=<use the valid Gemini model already configured locally>
GAME_MOVE_THINK_TIME_MILLIS=0
GAME_MOVE_DELAY_MIN=0s
GAME_MOVE_DELAY_MAX=0s
GAME_MAX_PLIES=12
```

The expected path is a local Groq connection failure -> fallback activation targeting `gemini` -> Gemini response. Restore or clear `AI_GROQ_BASE_URL` after the check so later runs use the normal configured Groq endpoint.
```

The angle-bracket lines above describe local secret/runtime inputs only. Do not replace them in committed documentation with real credentials.

- [ ] **Step 2: Complete the fast-mode documentation while touching the same section**

The current fast-mode snippet contains only move-delay variables. Replace it with:

```powershell
$env:GAME_MOVE_THINK_TIME_MILLIS = "0"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:GAME_MAX_PLIES = "12"
```

This keeps manual acceptance short and matches the original issue #46 implementation plan.

- [ ] **Step 3: Review the documentation diff for accidental secrets or unrelated edits**

```powershell
git diff -- docs/BUILD_AND_VERIFY.md
git diff --check
```

Explicitly confirm the diff contains no actual API key/token value.

- [ ] **Step 4: Commit the deterministic acceptance instructions**

```powershell
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: make phase 2 failover verification mechanical"
```

---

### Task 4: Get the Root Repository Verifier Green

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- Verify only unless a reproducible PR #60 defect is discovered.

**Interfaces:**
- Consumes: PR #60 implementation rebased onto the clean frontend baseline.
- Produces: issue #46's required green root-verification evidence.

- [ ] **Step 1: Disable real providers for automated repository verification**

```powershell
$env:AI_ENABLED = "false"
```

Do not set Groq/Gemini credentials for this step.

- [ ] **Step 2: Run the canonical Windows root verifier from repository root**

```powershell
.\scripts\verify.ps1
```

Expected:

```text
Backend Maven verify -> PASS
Frontend format:check -> PASS
Frontend typecheck -> PASS
Frontend lint -> PASS
Frontend Vitest -> PASS
Frontend production build -> PASS
```

- [ ] **Step 3: If root verification fails, classify the failure before editing anything**

Use this decision rule:

1. If `format:check` fails again, stop and verify the baseline branch was actually merged/rebased. Do not edit `verify.js`.
2. If a backend test/quality gate fails in a PR #60 file, reproduce it with the focused Maven test and fix only that defect.
3. If frontend typecheck/lint/test/build fails on current `master` too, treat it as a new baseline problem and keep it out of PR #60.
4. If the failure exists only on PR #60, fix it in PR #60 and rerun the focused check before rerunning the root verifier.

Do not proceed to final acceptance while the canonical root verifier is red.

- [ ] **Step 4: Preserve the passing verifier output as local evidence**

Record the actual counts/results needed for the acceptance record, but do not paste secrets or full noisy logs into `BUILD_AND_VERIFY.md`.

No commit is required for this task unless a real issue #46 defect was fixed.

---

### Task 5: Run the Credential-Free Full-Stack Acceptance First

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- No intended source changes.

**Interfaces:**
- Consumes: local PostgreSQL, backend, frontend, existing deterministic `AI_ENABLED=false` gateway.
- Produces: observed evidence that provider unavailability/disabled mode cannot stop the match and that browser lifecycle behavior remains correct.

- [ ] **Step 1: Start PostgreSQL using the existing local topology**

From repository root:

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

Expected: PostgreSQL service is running/healthy enough for the Spring Boot application to connect.

If Docker is unavailable, stop this task and leave manual acceptance unchecked.

- [ ] **Step 2: Start the backend in fast AI-disabled mode**

Open a backend terminal and run:

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

Expected:
- application API on `http://localhost:8082`;
- management endpoint on `http://localhost:8081`;
- no real provider calls because `AI_ENABLED=false`.

Keep this backend terminal open so logs can be inspected during the browser checks.

- [ ] **Step 3: Start the Vite frontend**

Open a separate terminal:

```powershell
cd client
npm.cmd run dev
```

Expected: frontend available at `http://localhost:5173`.

- [ ] **Step 4: Complete one AI-disabled fast match**

Using the UI:

1. Start a match with any two distinct personalities.
2. Observe move + dialogue entries arriving in the unified activity feed.
3. Let the short match reach its configured completion boundary.
4. Confirm deterministic fallback dialogue did not stop chess progression or match completion.

Do not claim this proves real-provider personality quality; it proves disabled-mode resilience and full-stack dialogue persistence/rendering.

- [ ] **Step 5: Verify the disabled-mode response metric**

In another terminal:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.responses | ConvertTo-Json -Depth 8
```

Expected: a meter series exists that distinguishes:

```text
source=deterministic_fallback
reason=ai_disabled
```

- [ ] **Step 6: Exercise refresh, reconnect, and stop/resume using the running local application**

Perform these as separate observations:

1. **Refresh:** after dialogue exists, refresh the browser; confirm authoritative board + activity hydrate without duplicate move/dialogue entries.
2. **Reconnect:** while backend remains running, toggle browser network offline then online; confirm connection state recovers and hydration does not duplicate/reorder activity.
3. **Stop/Resume:** during an in-progress match, use Stop and then Resume; confirm persisted dialogue remains ordered and new activity continues from the authoritative board state.

If the `GAME_MAX_PLIES=12` match ends too quickly for one interaction, start another fast match. Do not increase production pacing configuration permanently; these are environment-only acceptance runs.

---

### Task 6: Run the Four-Personality and Random-Rivalry Provider Acceptance

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- No intended source changes.

**Interfaces:**
- Consumes: valid local provider credentials and existing four seeded personalities.
- Produces: the issue #46 manual evidence for distinct character voices, contextual dialogue, and random rivalry.

- [ ] **Step 1: Confirm valid provider configuration exists only in local secret environment**

Required runtime values:

```text
AI_ENABLED=true
AI_GROQ_API_KEY=<local secret source>
AI_GROQ_MODEL=<locally configured model>
AI_GEMINI_API_KEY=<local secret source>
AI_GEMINI_MODEL=<locally configured model>
```

Do not print the secret values. If valid provider credentials are unavailable, stop here and leave PR #60 draft.

- [ ] **Step 2: Restart the backend with normal provider endpoints and fast match pacing**

Before starting, clear any forced-failure Groq base URL from a prior shell:

```powershell
Remove-Item Env:AI_GROQ_BASE_URL -ErrorAction SilentlyContinue
$env:AI_ENABLED = "true"
$env:GAME_MOVE_THINK_TIME_MILLIS = "0"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:GAME_MAX_PLIES = "12"
```

Start the backend with the valid local provider secrets already present in the environment or ignored `server/.env`.

- [ ] **Step 3: Run Blaze vs Vesper**

Using the UI:

1. Select Blaze and Vesper explicitly.
2. Start the match.
3. Observe dialogue from both personalities.
4. Confirm Blaze reads as the high-energy/showboat voice and Vesper as the dry/surgical strategist voice.
5. Observe at least one generated line that references the current move/event or recent banter rather than being a generic unrelated quip.

Only mark these identities/context as verified if the rendered dialogue actually demonstrates them.

- [ ] **Step 4: Run Gremlin vs Regent**

Using the UI:

1. Select Gremlin and Regent explicitly.
2. Start the match.
3. Observe dialogue from both personalities.
4. Confirm Gremlin reads as the absurdist/chaos voice and Regent as the pompous chess-aristocrat voice.

- [ ] **Step 5: Run Random Rivalry once**

1. Choose Random Rivalry.
2. Start the match.
3. Confirm two distinct personalities are selected and rendered for the two sides.
4. Confirm dialogue is associated with the correct selected speaker.

Do not require a particular random pair; require only two distinct valid seeded personalities.

---

### Task 7: Run the Controlled Groq -> Gemini Failover Acceptance

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- No intended source changes.

**Interfaces:**
- Consumes: the closed-local-port recipe documented in Task 3 and a valid local Gemini configuration.
- Produces: observed end-to-end evidence that a Groq outage activates Gemini, safe metrics/logging remain inspectable, and the chess match continues.

- [ ] **Step 1: Restart the backend with deterministic local Groq failure and valid Gemini settings**

Set:

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

Keep the valid Gemini key/model in the local secret environment. Do not overwrite them with committed values.

Expected: no request can reach the real Groq service because the configured Groq base URL is loopback port `9`.

- [ ] **Step 2: Start and complete a short match**

Using the frontend, start a match and let it run to completion.

Expected: Groq connection failure does not stop the match; Gemini supplies valid dialogue when available. If Gemini also fails, deterministic fallback may preserve match completion, but that does **not** satisfy the specific Groq -> Gemini success observation; inspect metrics/logs before checking that criterion.

- [ ] **Step 3: Inspect gateway metrics**

Run:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.provider.duration | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.fallback.activations | ConvertTo-Json -Depth 8
Invoke-RestMethod http://localhost:8081/actuator/metrics/ai.gateway.responses | ConvertTo-Json -Depth 8
```

Expected evidence includes series equivalent to:

```text
ai.gateway.provider.duration: provider=groq,outcome=failure
ai.gateway.provider.duration: provider=gemini,outcome=success
ai.gateway.fallback.activations: target=gemini,reason=failure
ai.gateway.responses: source=gemini,reason=fallback
```

Counts/durations may differ. The tag semantics must match.

- [ ] **Step 4: Inspect Spring AI native metrics and token usage when available**

```powershell
(Invoke-RestMethod http://localhost:8081/actuator/metrics).names | Select-String "gen_ai"
```

If `gen_ai.client.token.usage` is present, inspect and record that provider token usage was available.

If it is absent, record exactly:

```text
token usage metric not reported by provider in this acceptance run
```

Do not invent zero-token data and do not add custom token estimation.

- [ ] **Step 5: Inspect normal backend logs for safe correlation and leakage**

For dialogue-triggered provider attempts, confirm normal logs expose safe operational fields such as:

```text
provider
action/outcome or equivalent safe outcome metadata
matchId
triggerType
triggerPly
```

Confirm normal logs do **not** contain:

```text
full prompt text
raw provider response text
deterministic fallback line text
Groq API key
Gemini API key
Authorization header values
```

If any sensitive content is present, stop acceptance and fix that defect before recording success.

- [ ] **Step 6: Restore normal Groq configuration after the test**

```powershell
Remove-Item Env:AI_GROQ_BASE_URL -ErrorAction SilentlyContinue
Remove-Item Env:AI_GROQ_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:AI_GROQ_MODEL -ErrorAction SilentlyContinue
```

If the normal Groq key/model are supplied through process environment rather than `server/.env`, restore them from the user's local secret source before any later provider run. Never copy them into committed files.

---

### Task 8: Replace the Incomplete Acceptance Record with Observed Final Evidence

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Consumes: actual results from Tasks 4-7.
- Produces: issue #46's dated, auditable Phase 2 acceptance record.

- [ ] **Step 1: Get the actual execution date**

```powershell
Get-Date -Format "yyyy-MM-dd"
```

Use the returned date in the acceptance heading. Do not guess the date.

- [ ] **Step 2: Update the checklist above the acceptance record based only on actual observations**

Change `[ ]` to `[x]` only for checks actually completed in Tasks 5-7.

Before finalizing, all issue #46-required manual checks should be observed:

```text
four selectable personalities
contextual dialogue
random rivalry
refresh hydration without duplicates
reconnect hydration without duplicates/reordering
stop/resume ordering
AI-disabled match completion
controlled Groq -> Gemini failover
safe correlated logs
metric differentiation
```

If any required observation remains unchecked, keep PR #60 draft and do not write a fully successful acceptance record.

- [ ] **Step 3: Replace the current incomplete automated-evidence root-verifier line**

The existing record says root verification stopped at the frontend Prettier baseline. Replace that with the actually observed passing root verifier result from Task 4.

Do not leave both the old failure and new success as if they describe the same final state; the final record should clearly state the final verified state after the baseline fix/rebase.

- [ ] **Step 4: Replace the current "not run" manual/runtime bullets with factual observed results**

Record concise evidence for:

1. AI-disabled fast match completion and deterministic response metric.
2. Blaze vs Vesper observed personalities.
3. Gremlin vs Regent observed personalities.
4. Random Rivalry selecting distinct personalities.
5. Contextual dialogue observation.
6. Refresh/reconnect ordering and deduplication.
7. Stop/resume ordering.
8. Controlled local Groq failure activating Gemini and match completion.
9. Gateway metric tags observed for Groq failure, Gemini success/fallback, and deterministic disabled-mode response.
10. Spring AI token metric availability, or the exact documented absence statement.
11. Safe correlated logs with no prompt/response/credential leakage.

Do not paste generated banter, prompts, raw provider responses, or secret values into the record.

- [ ] **Step 5: Review only the acceptance/documentation diff**

```powershell
git diff -- docs/BUILD_AND_VERIFY.md
git diff --check
```

Expected: factual acceptance/documentation changes only.

- [ ] **Step 6: Commit the completed acceptance record**

```powershell
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: complete phase 2 acceptance"
```

---

### Task 9: Final Verification and PR Readiness Gate

**Branch:** `feature/issue-46-ai-observability`

**Files:**
- No intended changes unless final verification exposes a real defect.

**Interfaces:**
- Consumes: completed remediation and acceptance evidence.
- Produces: a PR #60 head that is ready for final review/merge.

- [ ] **Step 1: Run the root verifier one final time after the acceptance-record commit**

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

Expected: complete PASS.

This is the final verification result that matters for merge readiness.

- [ ] **Step 2: Run git hygiene checks**

```powershell
git status --short
git diff --check origin/master...HEAD
git diff --stat origin/master...HEAD
git diff --name-only origin/master...HEAD
```

Expected:
- working tree clean;
- no whitespace errors;
- PR #60 contains issue #46 implementation/docs only;
- no mass frontend Prettier baseline diff;
- no `.env` or credential file tracked.

- [ ] **Step 3: Push final PR #60 commits**

```powershell
git push origin feature/issue-46-ai-observability
```

If the earlier rebase means the remote still requires a non-fast-forward update, use:

```powershell
git push --force-with-lease origin feature/issue-46-ai-observability
```

Never use plain `--force`.

- [ ] **Step 4: Confirm hosted CI is green on the final head**

Required hosted checks for the final PR head must pass. In particular, backend and native-image verification must remain green; any check newly triggered by the final diff must also pass.

Do not rely on an older green SHA after pushing new commits.

- [ ] **Step 5: Re-read issue #46 acceptance criteria against the final state**

Before marking the PR ready, verify every criterion is now supported by actual evidence:

```text
Metrics distinguish Groq, Gemini fallback, and deterministic fallback outcomes.
Logs correlate calls without leaking prompt/response content or secrets.
Tests require no real paid provider calls.
A provider outage cannot prevent a match from completing.
Refresh/reconnect and stop/resume preserve correct dialogue ordering.
Manual acceptance verifies distinct personalities, contextual replies, random rivalry, and graceful failover.
Root verification passes.
BUILD_AND_VERIFY.md contains the dated Phase 2 acceptance record.
```

If any item is false, leave the PR draft.

- [ ] **Step 6: Mark PR #60 ready only after all gates are green**

If GitHub CLI is available:

```powershell
gh pr ready 60
```

Otherwise mark PR #60 "Ready for review" in GitHub.

Do not manually close issue #46; PR #60 already uses `Closes #46`, so merge should close it automatically.

---

## Final Self-Review Checklist

Before handing PR #60 back for final review, confirm:

- [ ] The frontend Prettier baseline was fixed and merged in a separate formatting-only PR.
- [ ] PR #60 was rebased onto that clean `master` baseline.
- [ ] PR #60 does not contain the mass frontend formatting cleanup.
- [ ] `docs/BUILD_AND_VERIFY.md` uses the deterministic closed-local-port Groq failover recipe.
- [ ] Fast acceptance mode documents `GAME_MOVE_THINK_TIME_MILLIS=0` and `GAME_MAX_PLIES=12` in addition to zero move delays.
- [ ] Canonical root `scripts\verify.ps1` passes from the final PR head.
- [ ] AI-disabled full-stack match actually completed.
- [ ] Blaze, Vesper, Gremlin, and Regent were actually exercised with provider-generated dialogue.
- [ ] Random Rivalry was actually exercised and selected two distinct personalities.
- [ ] At least one contextual line was actually observed.
- [ ] Refresh, reconnect, and stop/resume were actually exercised without dialogue duplication/reordering.
- [ ] Controlled Groq connection failure actually activated Gemini and the match completed.
- [ ] Gateway metrics actually exposed Groq failure, Gemini fallback/success, and deterministic disabled-mode response distinctly.
- [ ] Spring AI token usage was either observed or explicitly recorded as unavailable for that acceptance run.
- [ ] Normal logs showed safe match/event/provider correlation and no prompt/raw-response/credential leakage.
- [ ] The dated acceptance record contains only observed facts.
- [ ] Final hosted CI is green on the current PR head SHA.
- [ ] PR #60 remains draft until every required item above is true.

## Explicit Non-Goals

Do not add any of the following while executing this remediation plan:

- OpenTelemetry/tracing stack
- dashboards
- Prometheus/Grafana deployment work
- new AI provider abstraction
- provider retries
- prompt/raw-response persistence
- new database schema
- frontend feature redesign
- extra agent/memory/orchestration systems
- load testing
- analytics

This remediation is complete when issue #46's existing acceptance contract is truthfully satisfied—not when more observability features have been invented.