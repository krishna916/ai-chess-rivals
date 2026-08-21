# PR #60 Final Acceptance and Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the remaining verification for PR #60 / issue #46 with clean-tree proof, two short real-provider acceptance matches, deterministic Groq-to-Gemini failover evidence, and an accurate final acceptance record.

**Architecture:** Do not redesign or extend the production AI architecture. Treat this as a verification-and-evidence pass over the existing Spring AI gateway, owner controls, match lifecycle, metrics, and logs. Use the existing four-personality roster and two deliberately short matches: one randomized primary-provider match, then one forced-Groq-failure match using the two personalities not selected in the first match.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0, Micrometer/Actuator, React 19.2.7, TypeScript 6.0.2, PostgreSQL 17, PowerShell, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`

## Source of Truth

- Issue: `#46 Phase 2: Add AI observability, resilience coverage, and acceptance verification`
- PR: `#60 feat: add Phase 2 AI observability and resilience verification`
- Original implementation plan: `docs/superpowers/plans/2026-08-20-phase-2-ai-observability-resilience-acceptance.md`
- Verification guide: `docs/BUILD_AND_VERIFY.md`
- Admin rivalry UI: `client/src/features/admin/RivalrySetup.tsx`
- Admin match controls: `client/src/features/admin/MatchAdminControls.tsx`
- Match runtime configuration: `server/src/main/resources/application.yaml`

## Global Constraints

- Do not introduce a new agent, provider abstraction, framework, endpoint, database migration, frontend feature, tracing stack, dashboard, dependency, or test-only production hook.
- Do not change production Java/TypeScript code unless a verification step exposes a reproducible correctness defect. If that happens, stop and report the defect before patching it.
- Do not run `npm run format` on PR #60 merely to make a dirty working tree look green.
- Do not print, commit, paste, or document real Groq/Gemini API keys. Only check whether required environment variables are present/nonblank.
- Do not record raw prompts or full raw provider responses in the acceptance document. Record observable behavior and short paraphrases only.
- Keep automated tests credential-free. Real provider calls occur only during the manual acceptance tasks in this plan.
- Use the current four system personalities: Blaze, Vesper, Gremlin, Regent.
- Keep Groq primary and Gemini fallback.
- The forced-failover run must use a closed loopback Groq URL and must make no request to the real Groq service.
- `GAME_MOVE_THINK_TIME_MILLIS` must be at least `1`; `GameProperties` validates it with `@Min(1)`. Do not use `0`.
- Fast acceptance values for this plan are:
  - `GAME_MOVE_THINK_TIME_MILLIS=1`
  - `GAME_MAX_PLIES=6`
  - `GAME_MOVE_DELAY_MIN=0s`
  - `GAME_MOVE_DELAY_MAX=0s`
  - `MATCH_COOLDOWN=0s`
- Keep PR #60 draft until every non-optional task below is complete and the final branch head is verified.

## File Map

### Modify

- `docs/BUILD_AND_VERIFY.md` — add the complete fast-mode settings and replace the two remaining unchecked acceptance observations with facts actually observed during this pass.

### Create only if Task 1 proves the committed frontend baseline is actually broken

- No new source file. Create a separate Git branch/PR containing formatting-only changes to the existing frontend files reported by Prettier.

### Explicitly Do Not Modify During the Normal Path

- `server/src/main/java/**`
- `server/src/test/java/**`
- `client/src/**`
- `server/pom.xml`
- `client/package.json`
- database migrations
- CI workflow files

---

### Task 1: Prove the Root Verifier From a Clean Working Tree

**Files:**
- Verify only: `scripts/verify.ps1`
- Verify only: committed backend/frontend sources

**Produces:**
- Trustworthy proof that the root verifier passes against committed PR #60 content, not uncommitted formatter output.
- A formatting-only side PR only if a clean committed checkout genuinely fails Prettier.

- [ ] **Step 1: Confirm the PR branch is clean before verification**

From repository root:

```powershell
git branch --show-current
git status --short
git diff --exit-code
git diff --cached --exit-code
```

Expected:

```text
feature/issue-46-ai-observability
```

`git status --short` must print nothing, and both `git diff` commands must exit `0`.

If the tree is not clean, do not reset or format anything. Commit legitimate plan/documentation changes first or move unrelated user work elsewhere, then repeat this step.

- [ ] **Step 2: Run the complete root verifier with AI disabled**

```powershell
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
```

Expected:

```text
backend Maven verify succeeds
306 backend tests pass
Spotless passes
SpotBugs reports zero findings
frontend format check passes
frontend typecheck passes
frontend lint passes
83 frontend tests pass
frontend production build succeeds
```

- [ ] **Step 3: Prove the verifier did not make the tree green by mutating tracked files**

Immediately after Step 2:

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
```

Expected: still completely clean.

If Steps 1–3 pass, the previous 23-file Prettier failure was a local-worktree artifact. **Skip Steps 4–9 and do not create a formatting PR.**

- [ ] **Step 4: Conditional — classify a clean-tree verifier failure before changing anything**

Run this only if Step 2 fails.

If the failure is not frontend `format:check`, stop and report the exact failing command/test. Do not hide a new defect inside a formatting PR.

If the only failure is Prettier on already-committed frontend files, capture the filenames from the verifier output and continue.

- [ ] **Step 5: Conditional — create an isolated formatting-only worktree from current `master`**

```powershell
git fetch origin
git worktree add --detach ..\ai-chess-rivals-format-baseline origin/master
cd ..\ai-chess-rivals-format-baseline
git switch -c chore/frontend-format-baseline
```

Expected: a new clean worktree based on `origin/master`.

- [ ] **Step 6: Conditional — apply only the repository formatter and verify frontend**

```powershell
cd client
npm ci
npm run format
npm run verify
cd ..
```

Expected: frontend verification passes.

- [ ] **Step 7: Conditional — prove the side branch contains formatting-only client changes**

```powershell
git status --short
git diff --check
git diff --name-only
```

Expected: only the frontend files reported by Prettier or files mechanically touched by the same formatter. No Java, YAML, dependency, API, test-behavior, or CI changes.

Review the diff for semantic edits. If anything beyond formatting changed, revert that file and investigate instead of committing it.

- [ ] **Step 8: Conditional — commit/push the formatting baseline and open a separate PR**

```powershell
git add client
git commit -m "chore: normalize frontend formatting"
git push -u origin chore/frontend-format-baseline
```

Open a PR targeting `master` with a body that says it is formatting-only and exists solely to restore the repository verification baseline. Do **not** add these files to PR #60.

Stop here until that side PR is reviewed and merged.

- [ ] **Step 9: Conditional — rebase PR #60 after the formatting baseline merges, then rerun Steps 1–3**

Back in the PR #60 worktree:

```powershell
git fetch origin
git rebase origin/master
git push --force-with-lease
```

Then repeat Steps 1–3. Do not continue to Task 2 until the clean-tree root verifier is green.

---

### Task 2: Make the Fast Acceptance Recipe Actually Fast

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Produces:**
- A documented six-ply acceptance configuration that avoids another long manual match.

- [ ] **Step 1: Add the complete fast-mode environment block**

In the existing `Phase 2 AI observability and resilience verification` section, replace the current two-setting fast-mode example with:

```powershell
$env:GAME_MOVE_THINK_TIME_MILLIS = "1"
$env:GAME_MAX_PLIES = "6"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:MATCH_COOLDOWN = "0s"
```

Add one sentence immediately below it:

```text
`GAME_MOVE_THINK_TIME_MILLIS` uses `1` rather than `0` because runtime configuration validates the value with a minimum of one millisecond; six plies are enough for a short acceptance match while still exercising start dialogue and move-reaction context.
```

Do not change production defaults in `application.yaml`.

- [ ] **Step 2: Review the documentation diff**

```powershell
git diff -- docs/BUILD_AND_VERIFY.md
git diff --check
```

Expected: only the fast-mode documentation changed.

- [ ] **Step 3: Commit the documentation correction**

```powershell
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: tighten Phase 2 acceptance fast mode"
```

---

### Task 3: Run a Real-Provider Random Rivalry Match

**Files:**
- No source changes.
- Runtime evidence will be recorded in `docs/BUILD_AND_VERIFY.md` in Task 5.

**Produces:**
- Random-rivalry evidence.
- Real provider-generated start dialogue for two distinct personalities.
- At least one contextual move-reaction observation.
- Primary-provider metric evidence when Groq is healthy.

- [ ] **Step 1: Check provider configuration without printing secrets**

In the PowerShell session that will start the backend:

```powershell
if ([string]::IsNullOrWhiteSpace($env:AI_GROQ_API_KEY)) { throw "AI_GROQ_API_KEY is not set; ask the owner to export it without pasting it into logs or docs." }
if ([string]::IsNullOrWhiteSpace($env:AI_GROQ_MODEL)) { throw "AI_GROQ_MODEL is not set." }
if ([string]::IsNullOrWhiteSpace($env:AI_GEMINI_API_KEY)) { throw "AI_GEMINI_API_KEY is not set; ask the owner to export it without pasting it into logs or docs." }
if ([string]::IsNullOrWhiteSpace($env:AI_GEMINI_MODEL)) { throw "AI_GEMINI_MODEL is not set." }
```

Do not echo any secret value.

- [ ] **Step 2: Configure a six-ply primary-provider acceptance run**

```powershell
$env:AI_ENABLED = "true"
$env:AI_GROQ_BASE_URL = "https://api.groq.com/openai/v1"
$env:GAME_MOVE_THINK_TIME_MILLIS = "1"
$env:GAME_MAX_PLIES = "6"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:MATCH_COOLDOWN = "0s"
```

If `OWNER_CONTROL_TOKEN` is not already set, generate one locally without committing it:

```powershell
if ([string]::IsNullOrWhiteSpace($env:OWNER_CONTROL_TOKEN)) {
  $env:OWNER_CONTROL_TOKEN = [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
  ).ToLower()
}
```

- [ ] **Step 3: Start PostgreSQL, backend, and frontend**

Terminal A:

```powershell
cd server
docker compose up -d postgres
```

Terminal B, repository root with the environment from Steps 1–2:

```powershell
server\mvnw.cmd -f server\pom.xml spring-boot:run
```

Terminal C:

```powershell
cd client
npm.cmd run dev
```

Expected:

```text
backend application: http://localhost:8082
Actuator: http://localhost:8081/actuator/health
frontend: http://localhost:5173
```

- [ ] **Step 4: Exercise Randomize Rivalry through the owner UI**

Open:

```text
http://localhost:5173/admin
```

Enter the local owner token, then:

1. Confirm the roster contains Blaze, Vesper, Gremlin, and Regent.
2. Click **Randomize Rivalry** once.
3. Record the selected White and Black display names in temporary notes as `PAIR_A`.
4. Confirm the two selected personalities are different.
5. Click **Start Match**.

Do not manually change the randomized pair before starting.

- [ ] **Step 5: Observe personality and contextual-dialogue behavior**

During the six-ply match:

1. Confirm the two game-start dialogue entries are associated with the two personalities in `PAIR_A`.
2. Confirm their wording is visibly different in style/tone rather than identical generic text.
3. Wait for at least one dialogue entry triggered after a committed move.
4. Confirm that move-reaction line is contextually related to the just-played chess event, position consequence, or recent banter. Record a short paraphrase plus the triggering ply in temporary notes; do not copy the full raw provider response into the acceptance document.

If provider output is malformed and the UI only shows deterministic fallback, this task is not complete. Check the gateway metrics/log classification before deciding whether the problem is provider configuration or application behavior.

- [ ] **Step 6: Capture primary-provider metrics**

Run:

```powershell
Invoke-RestMethod "http://localhost:8081/actuator/metrics/ai.gateway.responses?tag=source:groq&tag=reason:primary" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8081/actuator/metrics/ai.gateway.provider.duration?tag=provider:groq&tag=outcome:success" | ConvertTo-Json -Depth 6
```

Expected: each response contains a `COUNT` measurement greater than `0`.

Token usage is optional because the provider may not expose it. Probe it without making it a hard gate:

```powershell
try {
  Invoke-RestMethod "http://localhost:8081/actuator/metrics/gen_ai.client.token.usage" | ConvertTo-Json -Depth 6
} catch {
  Write-Host "Token-usage metric not available from this provider/run; record as unavailable, not failed."
}
```

- [ ] **Step 7: Record the complement pair for Task 4**

From the set:

```text
Blaze, Vesper, Gremlin, Regent
```

remove the two names in `PAIR_A`. Record the remaining two names as `PAIR_B`.

`PAIR_B` must contain exactly two different personalities. This guarantees that Tasks 3 and 4 exercise all four personalities exactly once as the selected match participants.

Stop the backend before Task 4 so the provider base URL can be changed deterministically.

---

### Task 4: Force Groq Failure and Prove Gemini Fallback With the Remaining Personalities

**Files:**
- No source changes.
- Runtime evidence will be recorded in `docs/BUILD_AND_VERIFY.md` in Task 5.

**Produces:**
- Controlled Groq failure with zero real Groq request.
- Successful Gemini fallback evidence.
- Runtime evidence for the two personalities not covered in Task 3.
- Safe correlated log evidence.

- [ ] **Step 1: Configure the closed-loopback Groq endpoint**

In the backend PowerShell session:

```powershell
$env:AI_ENABLED = "true"
$env:AI_GROQ_API_KEY = "dummy-groq-key"
$env:AI_GROQ_MODEL = "dummy-groq-model"
$env:AI_GROQ_BASE_URL = "http://127.0.0.1:9/v1"
$env:GAME_MOVE_THINK_TIME_MILLIS = "1"
$env:GAME_MAX_PLIES = "6"
$env:GAME_MOVE_DELAY_MIN = "0s"
$env:GAME_MOVE_DELAY_MAX = "0s"
$env:MATCH_COOLDOWN = "0s"
```

Keep the valid Gemini key/model already exported. Do not replace or print them.

- [ ] **Step 2: Prove port 9 is closed before starting**

```powershell
$groqPortOpen = Test-NetConnection -ComputerName 127.0.0.1 -Port 9 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($groqPortOpen) { throw "Port 9 is occupied; choose another closed loopback port and update AI_GROQ_BASE_URL before continuing." }
```

Expected: no exception.

- [ ] **Step 3: Restart only the backend with the forced-failover configuration**

From repository root:

```powershell
server\mvnw.cmd -f server\pom.xml spring-boot:run
```

Keep PostgreSQL and the frontend running.

- [ ] **Step 4: Start the second six-ply match with `PAIR_B`**

In `http://localhost:5173/admin`:

1. Select the two `PAIR_B` personalities explicitly, one as White and one as Black.
2. Click **Start Match**.
3. Confirm both game-start dialogue entries are associated with those two personalities.
4. Confirm the match continues and finishes normally despite every Groq connection attempt failing.

Because `generateStart` always creates one line for White and one for Black, this step completes the four-personality runtime coverage when combined with Task 3.

- [ ] **Step 5: Prove the exact failover path through metrics**

Run:

```powershell
Invoke-RestMethod "http://localhost:8081/actuator/metrics/ai.gateway.provider.duration?tag=provider:groq&tag=outcome:failure" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8081/actuator/metrics/ai.gateway.fallback.activations?tag=target:gemini&tag=reason:failure" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8081/actuator/metrics/ai.gateway.provider.duration?tag=provider:gemini&tag=outcome:success" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8081/actuator/metrics/ai.gateway.responses?tag=source:gemini&tag=reason:fallback" | ConvertTo-Json -Depth 6
```

Expected: each response contains a `COUNT` measurement greater than `0`.

If Groq is classified as `timeout` rather than `failure`, first verify that the chosen endpoint is genuinely closed. A closed local port should refuse immediately; do not weaken the acceptance criterion just to make a different metric tag pass.

If Gemini fails and the result becomes `deterministic_fallback`, the forced Groq-to-Gemini success scenario is **not complete**. Diagnose provider configuration/availability before proceeding.

- [ ] **Step 6: Inspect only safe operational log lines**

From the backend console/log capture, inspect lines containing:

```text
AI provider attempt
AI fallback activated
AI response selected
```

For dialogue-triggered attempts, confirm correlation fields such as `matchId`, `triggerType`, and `triggerPly` are present where applicable.

Confirm these lines do **not** contain dialogue text, prompts, API keys, authorization headers, or raw provider responses. Do not search by pasting the real key into the terminal.

---

### Task 5: Update the Acceptance Record With Observed Facts Only

**Files:**
- Modify: `docs/BUILD_AND_VERIFY.md`

**Produces:**
- Final dated evidence satisfying issue #46 without overstating what was tested.

- [ ] **Step 1: Preserve the existing 2026-08-20 evidence and add a completion subsection**

Under the existing Phase 2 acceptance record, add:

```markdown
#### Completion pass — 2026-08-21
```

Do not delete the prior AI-disabled, refresh/reconnect, stop/resume, or automated evidence.

- [ ] **Step 2: Replace the Random Rivalry unchecked item with the actual observation**

Use this shape, replacing the bracketed names/ply with facts from Task 3:

```markdown
- [x] Random Rivalry was exercised through the owner-controls UI and selected [PAIR_A_WHITE] vs [PAIR_A_BLACK]; both selections remained associated with the correct players after match start.
- [x] Real provider-generated start dialogue was observed for [PAIR_A_WHITE] and [PAIR_A_BLACK], and a move-reaction at ply [N] was contextually related to the committed chess event/recent banter.
```

Do not paste the full generated response.

- [ ] **Step 3: Record all-four-personality coverage from the two matches**

Add one factual line:

```markdown
- [x] Across the two short provider-enabled matches, Blaze, Vesper, Gremlin, and Regent were each exercised as a selected participant and produced provider-backed game-start dialogue.
```

Only add this line if Tasks 3 and 4 actually covered all four.

- [ ] **Step 4: Replace the real-provider/failover unchecked item with the exact metrics-backed observation**

Use this shape:

```markdown
- [x] Controlled Groq failure used a confirmed-closed loopback endpoint (`127.0.0.1:9`) with dummy Groq credentials; Gemini produced the fallback dialogue for [PAIR_B_WHITE] vs [PAIR_B_BLACK]. Gateway metrics recorded Groq `failure`, Gemini fallback activation, Gemini `success`, and response source `gemini` with reason `fallback`.
- [x] Runtime provider/fallback logs contained operational correlation metadata only; no prompt, dialogue content, credential, authorization header, or raw provider response was observed in the inspected log lines.
```

If any part was not observed, leave that part unchecked instead of inferring it from automated tests.

- [ ] **Step 5: Record the clean-tree verifier evidence accurately**

If Task 1 passed without a formatting side PR, add:

```markdown
- [x] Root verification passed from a clean PR #60 working tree with `AI_ENABLED=false`, and `git status`/`git diff` remained clean before and after the verifier; the earlier 23-file Prettier failure was not reproducible from committed branch content.
```

If a formatting side PR was required, instead name that PR/merge commit and state that PR #60 was rebased after it merged before the root verifier passed.

- [ ] **Step 6: Review and commit the final acceptance record**

```powershell
git diff -- docs/BUILD_AND_VERIFY.md
git diff --check
git add docs/BUILD_AND_VERIFY.md
git commit -m "docs: complete Phase 2 acceptance record"
```

Expected: documentation-only commit with no secrets or raw provider payloads.

---

### Task 6: Verify the Final Branch Head and Move PR #60 Out of Draft

**Files:**
- Verify only: whole repository
- PR metadata: #60

**Produces:**
- Fresh verification on the exact final branch head.
- Hosted CI evidence.
- PR ready for normal review/merge.

- [ ] **Step 1: Run the root verifier one final time from a clean tree**

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
$env:AI_ENABLED = "false"
.\scripts\verify.ps1
git status --short
git diff --exit-code
git diff --cached --exit-code
```

Expected: all checks pass and the working tree remains clean.

- [ ] **Step 2: Push the final branch head**

```powershell
git push origin feature/issue-46-ai-observability
```

If Task 1 required a rebase, the earlier `--force-with-lease` push already updated history; a normal push is expected after the final documentation commits.

- [ ] **Step 3: Wait for hosted CI on this exact pushed SHA and inspect every relevant job**

Required green jobs for the PR's final source diff:

```text
CI / Detect changes
CI / Backend verification
CI / Native image verification
```

`CI / Frontend verification` may be skipped when the final #60 diff contains no `client/**`, script, or workflow changes. The local root verifier from Steps 1 and Task 1 is the full-repository frontend proof.

Do not cite an older CI run number in the final acceptance record or PR body.

- [ ] **Step 4: Update the PR description to remove stale draft blockers**

Replace the sentence saying Random Rivalry and real-provider failover were not exercised with a short final verification summary containing only the observations from Tasks 3–6.

Keep the PR description concise; `docs/BUILD_AND_VERIFY.md` is the detailed acceptance evidence.

- [ ] **Step 5: Mark PR #60 ready for review, but do not merge it automatically**

If GitHub CLI is available:

```powershell
gh pr ready 60
```

Otherwise use the GitHub UI to select **Ready for review**.

Expected: PR #60 is no longer draft.

- [ ] **Step 6: Final completion report**

Report exactly:

```text
- final branch SHA
- clean-tree root verifier result
- final hosted CI run/result
- Random Rivalry pair
- complementary forced-failover pair
- confirmation that all four personalities were exercised
- Groq -> Gemini metric evidence
- whether token usage was available or unavailable
- confirmation that no secrets/raw prompts/responses were added to docs/log evidence
- PR #60 ready-for-review state
```

Do not claim merge readiness if any required item above is still incomplete.
