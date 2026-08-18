# PR #58 Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two PR #58 frontend correctness regressions without changing backend match semantics, and harden WebSocket snapshot coverage for stored rivalry identities.

**Architecture:** Keep the backend as the authority for both execution availability and match identity. A stopped match may show a Resume action, but `startAvailability.allowed` decides whether it is enabled; active/stopped rivalry labels come from the immutable `whitePersonality` / `blackPersonality` snapshot fields, while the active roster is used only for editable new-match setup. No new component, persistence, API, or dependency is needed.

**Tech Stack:** React 19, TypeScript 6, Zustand, Axios, Vitest, Testing Library, Java 25, Spring Boot 4.1, JUnit 5.

## Source of Truth

- Pull request: `#58 feat: add personality selection and random rivalries`
- Issue: `#44 Phase 2: Add personality selection and random rivalry setup`
- Existing implementation plan: `docs/superpowers/plans/2026-08-16-personality-selection-random-rivalry.md`
- Approved Phase 2 design: `docs/superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md`
- Review findings being addressed:
  1. A stopped match currently enables `Resume Match` even while the backend reports cooldown/quota blocking through `startAvailability`.
  2. Running/stopped rivalry selectors currently derive their visible options from the active roster, so a stored personality that later becomes inactive can be visually replaced/misrepresented even though the backend correctly preserves the original match rivalry.
  3. `MatchStateMessage.from(...)` already maps both identities correctly, but its focused mapper test does not assert them.

## Root Causes Verified on PR Head `a20a900c9c75fc436f12df9ff62815c02acad19c`

- `MatchAdminControls.tsx` computes `canStartOrResume = stopped || canCreateNewMatch`, so `STOPPED` bypasses `startAvailability.allowed`.
- `MatchAdminControls.tsx` renders `RivalrySetup` whenever `roster.length >= 2`, including running/stopped matches. `RivalrySetup` can only render active roster entries, not an immutable stored identity absent from that roster.
- Backend `MatchExecutionGuard` already applies cooldown/quota consistently to every accepted start/resume reservation. Do not change it.
- Backend match snapshots already carry authoritative `whitePersonality` and `blackPersonality`. Do not add another identity source.

## Global Constraints

- Do not change `MatchExecutionGuard`, `MatchControlService`, controller behavior, cooldown semantics, daily-limit semantics, or the `/match/start` contract.
- Do not re-resolve stopped-match personalities against the active roster.
- Do not add a new UI component or dependency for the locked rivalry display; keep it local to `MatchAdminControls`.
- Do not modify `RivalrySetup.tsx` unless a failing test proves it is necessary. The bug is in when/how it is used, not in its editable-selector behavior.
- Keep `Resume Match` visible for a stopped match, but disabled until the authoritative `startAvailability.allowed === true` and both stored identities are present.
- Roster loading/failure must not block an otherwise-allowed stopped-match resume.
- Never show editable White/Black selectors or Randomize while the match is `IN_PROGRESS` or `STOPPED`.
- Running/stopped identity labels must use the snapshot's stored `displayName` values even when those keys are absent from the active roster.
- Finished/idle matches continue using the active roster for the next editable rivalry selection.
- Preserve all existing start, stop, cooldown countdown, error handling, randomization, and owner-token behavior.
- No production backend change is expected in this follow-up.

## File Map

### Modify

- `client/src/features/admin/MatchAdminControls.tsx` — make resume availability server-authoritative and render immutable rivalry identity while locked.
- `client/src/features/admin/MatchAdminControls.test.tsx` — regression tests for cooldown-gated resume, roster-independent resume, and authoritative locked identity display.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java` — assert both personality fields on `MATCH_STATE` snapshot mapping.

### Explicitly Do Not Modify

- `client/src/features/admin/RivalrySetup.tsx`
- `client/src/features/admin/rivalrySelection.ts`
- `client/src/store/matchViewerStore.ts`
- `server/src/main/java/**`
- database migrations, Maven/npm dependencies, Stockfish behavior, or AI provider/dialogue behavior.

---

### Task 1: Make Stopped-Match Resume Obey Authoritative Start Availability

**Files:**
- Modify: `client/src/features/admin/MatchAdminControls.test.tsx`
- Modify: `client/src/features/admin/MatchAdminControls.tsx`

**Interfaces:**
- Consumes: Zustand `matchStatus`, `startAvailability`, `whitePersonality`, `blackPersonality`.
- Produces: `Resume Match` remains visible in `STOPPED`, but is enabled only when the server says starting is allowed and the immutable stored pair is known.

- [ ] **Step 1: Add a failing cooldown-gated resume regression test**

Add this test beside the existing stopped-match resume test in `MatchAdminControls.test.tsx`:

```tsx
it("keeps Resume disabled while the server blocks a stopped match", async () => {
  const blocked = {
    ...allowed,
    allowed: false,
    blockedBy: "MATCH_COOLDOWN_ACTIVE" as const,
    retryAfterSeconds: 60,
  };
  const storedWhite = { key: "stored-white", displayName: "Stored White" };
  const storedBlack = { key: "stored-black", displayName: "Stored Black" };

  useMatchViewerStore.setState({
    matchStatus: "STOPPED",
    startAvailability: blocked,
    whitePersonality: storedWhite,
    blackPersonality: storedBlack,
  });
  vi.mocked(matchApi.getCurrentMatch).mockResolvedValue({
    ...snapshot,
    whitePersonality: storedWhite,
    blackPersonality: storedBlack,
    running: false,
    startAvailability: blocked,
  });

  render(
    <MatchAdminControls
      token="owner-token"
      onLock={vi.fn()}
      onUnauthorized={vi.fn()}
    />,
  );

  const resume = screen.getByRole("button", { name: "Resume Match" });
  expect(resume).toBeDisabled();
  fireEvent.click(resume);
  expect(adminMatchApi.startMatch).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run the focused frontend test and verify RED**

From `client/` run:

```bash
npm test -- MatchAdminControls.test.tsx
```

Expected: the new test fails because current code uses `canStartOrResume = stopped || canCreateNewMatch`, which enables Resume whenever `matchStatus === "STOPPED"`.

- [ ] **Step 3: Update the existing roster-failure resume test to model authoritative availability**

In `resumes a stopped match with stored identities when roster loading fails`, add `startAvailability: allowed` to the store state and make the refresh mock return the same stored identities with an allowed stopped snapshot:

```tsx
const storedWhite = { key: "stored-white", displayName: "Stored White" };
const storedBlack = { key: "stored-black", displayName: "Stored Black" };

useMatchViewerStore.setState({
  matchStatus: "STOPPED",
  startAvailability: allowed,
  whitePersonality: storedWhite,
  blackPersonality: storedBlack,
});
vi.mocked(matchApi.getCurrentMatch).mockResolvedValue({
  ...snapshot,
  whitePersonality: storedWhite,
  blackPersonality: storedBlack,
  running: false,
  startAvailability: allowed,
});
```

Keep the existing `personalityApi.listSelectable` rejection and the assertion that `adminMatchApi.startMatch` receives the stored keys. This proves roster availability is irrelevant to resume once the server allows it.

- [ ] **Step 4: Implement the minimal resume gate in `MatchAdminControls.tsx`**

Keep the existing `showStart` behavior so stopped matches still show a Resume button. Replace the unconditional stopped shortcut in `canStartOrResume` with an explicit `canResume`:

```tsx
const canResume =
  stopped &&
  startAvailability?.allowed === true &&
  whitePersonality !== undefined &&
  blackPersonality !== undefined;

const canStartOrResume = stopped ? canResume : canCreateNewMatch;
```

Do not change `startRequest`: a stopped match must continue posting the authoritative stored keys.

- [ ] **Step 5: Re-run the focused frontend test and verify GREEN**

```bash
npm test -- MatchAdminControls.test.tsx
```

Expected: all tests in `MatchAdminControls.test.tsx` pass, including:
- Resume disabled during cooldown.
- Resume still succeeds with stored identities when roster loading fails and `startAvailability.allowed` is true.

- [ ] **Step 6: Commit Task 1**

```bash
git add client/src/features/admin/MatchAdminControls.tsx client/src/features/admin/MatchAdminControls.test.tsx
git commit -m "fix: gate stopped match resume by availability"
```

---

### Task 2: Render Stored Rivalry Identities Instead of Active-Roster Selectors While Locked

**Files:**
- Modify: `client/src/features/admin/MatchAdminControls.test.tsx`
- Modify: `client/src/features/admin/MatchAdminControls.tsx`

**Interfaces:**
- Consumes: authoritative store fields `whitePersonality` and `blackPersonality` from `MATCH_STARTED` / `MATCH_STATE` hydration.
- Produces: read-only White/Black identity labels for `IN_PROGRESS` and `STOPPED`; `RivalrySetup` remains the editable UI only for idle/finished pre-match setup.

- [ ] **Step 1: Add a failing test for a stored rivalry absent from the active roster**

Add this test to `MatchAdminControls.test.tsx`:

```tsx
it("renders stored rivalry identities instead of roster selectors while stopped", async () => {
  const storedWhite = { key: "retired-white", displayName: "Retired White" };
  const storedBlack = { key: "retired-black", displayName: "Retired Black" };

  useMatchViewerStore.setState({
    matchStatus: "STOPPED",
    startAvailability: allowed,
    whitePersonality: storedWhite,
    blackPersonality: storedBlack,
  });
  vi.mocked(matchApi.getCurrentMatch).mockResolvedValue({
    ...snapshot,
    whitePersonality: storedWhite,
    blackPersonality: storedBlack,
    running: false,
    startAvailability: allowed,
  });

  render(
    <MatchAdminControls
      token="owner-token"
      onLock={vi.fn()}
      onUnauthorized={vi.fn()}
    />,
  );

  await waitFor(() =>
    expect(personalityApi.listSelectable).toHaveBeenCalledTimes(1),
  );

  expect(screen.getByText("Retired White")).toBeVisible();
  expect(screen.getByText("Retired Black")).toBeVisible();
  expect(screen.queryByLabelText("White personality")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("Black personality")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Randomize Rivalry" }),
  ).not.toBeInTheDocument();
});
```

This deliberately uses keys that do not exist in the mocked active `roster`.

- [ ] **Step 2: Run the focused frontend test and verify RED**

```bash
npm test -- MatchAdminControls.test.tsx
```

Expected: the new test fails after the active roster loads because current code renders `RivalrySetup` whenever `roster.length >= 2` and cannot render the stored inactive identities.

- [ ] **Step 3: Make editability depend on match lifecycle, not roster contents**

In `MatchAdminControls.tsx`, after `running` / `stopped`, add:

```tsx
const rivalryEditable = !running && !stopped;
```

Remove the now-unnecessary `rivalryLocked` variable.

- [ ] **Step 4: Restrict roster loading/error UI to editable pre-match setup**

Change the existing render guards to:

```tsx
{rivalryEditable && rosterLoading && (
  <p className="text-sm text-muted-foreground">Loading personalities…</p>
)}

{rivalryEditable && rosterError && (
  <div className="space-y-2" role="alert">
    <p className="text-sm text-destructive">{rosterError}</p>
    <Button
      type="button"
      variant="outline"
      size="sm"
      onClick={() => void loadRoster()}
    >
      Retry
    </Button>
  </div>
)}
```

Delete the special-case `stopped && ... && roster.length < 2` fallback paragraph; the authoritative summary in the next step replaces it for every running/stopped case.

- [ ] **Step 5: Render the authoritative stored pair while running or stopped**

Immediately before the editable `RivalrySetup`, render a small read-only summary:

```tsx
{(running || stopped) && whitePersonality && blackPersonality && (
  <div
    className="grid gap-4 rounded-md border p-3 sm:grid-cols-2"
    aria-label="Current rivalry"
  >
    <div className="space-y-1">
      <p className="text-sm font-medium">White personality</p>
      <p className="text-sm text-muted-foreground">
        {whitePersonality.displayName}
      </p>
    </div>
    <div className="space-y-1">
      <p className="text-sm font-medium">Black personality</p>
      <p className="text-sm text-muted-foreground">
        {blackPersonality.displayName}
      </p>
    </div>
  </div>
)}
```

This display must not inspect `roster` at all.

- [ ] **Step 6: Render `RivalrySetup` only for editable lifecycle states**

Change the existing guard and disabled prop to:

```tsx
{rivalryEditable && roster.length >= 2 && (
  <RivalrySetup
    roster={roster}
    whitePersonalityKey={whitePersonalityKey}
    blackPersonalityKey={blackPersonalityKey}
    disabled={isPending}
    onWhiteChange={setWhitePersonalityKey}
    onBlackChange={setBlackPersonalityKey}
    onRandomize={() => {
      const next = randomizeRivalry(roster);
      setWhitePersonalityKey(next.whitePersonalityKey);
      setBlackPersonalityKey(next.blackPersonalityKey);
    }}
  />
)}
```

Do not change `RivalrySetup.tsx` itself.

- [ ] **Step 7: Re-run focused frontend tests and verify GREEN**

```bash
npm test -- MatchAdminControls.test.tsx RivalrySetup.test.tsx rivalrySelection.test.ts
```

Expected: all focused admin rivalry tests pass. Existing editable selection/randomization behavior remains unchanged.

- [ ] **Step 8: Commit Task 2**

```bash
git add client/src/features/admin/MatchAdminControls.tsx client/src/features/admin/MatchAdminControls.test.tsx
git commit -m "fix: show authoritative rivalry while match is locked"
```

---

### Task 3: Harden `MATCH_STATE` Identity Mapping Coverage

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java`

**Interfaces:**
- Consumes: existing `MatchStateMessage.from(MatchSnapshot)` production behavior.
- Produces: focused regression assertions that reconnect/snapshot hydration contains both immutable match identities.

This task is coverage-only: the production mapper is already correct. Do not invent a production change merely to create a RED phase.

- [ ] **Step 1: Import `MatchPersonalityResponse` and simplify existing started-message assertions**

Add:

```java
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
```

In `mapsMatchStartedEventToExplicitPayload`, replace the two fully-qualified constructor references with the imported type:

```java
assertEquals(
    new MatchPersonalityResponse("white-test", "White Test"), payload.whitePersonality());
assertEquals(
    new MatchPersonalityResponse("black-test", "Black Test"), payload.blackPersonality());
```

- [ ] **Step 2: Assert both identities in `createsMatchStatePayloadFromSnapshot`**

Immediately after `assertEquals(match.id(), payload.matchId());`, add:

```java
assertEquals(
    new MatchPersonalityResponse("white-test", "White Test"), payload.whitePersonality());
assertEquals(
    new MatchPersonalityResponse("black-test", "Black Test"), payload.blackPersonality());
```

- [ ] **Step 3: Run the focused backend test**

From repository root:

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchStreamMessageMapperTest test
```

Expected: PASS. If these identity assertions fail, stop and investigate the mapper; do not weaken the assertions.

- [ ] **Step 4: Commit Task 3**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java
git commit -m "test: assert match state rivalry identities"
```

---

### Task 4: Format, Verify, and Manually Exercise the Corrected Lifecycle

**Files:**
- No intended production edits beyond Tasks 1-2.
- Formatting may touch only files already modified by this plan.

- [ ] **Step 1: Run frontend formatting on the modified TS/TSX files**

From `client/`:

```bash
npm run format
```

Review `git diff` afterward. Revert unrelated formatting changes if the formatter touched unrelated files.

- [ ] **Step 2: Run focused frontend tests again after formatting**

```bash
npm test -- MatchAdminControls.test.tsx RivalrySetup.test.tsx rivalrySelection.test.ts
```

Expected: PASS.

- [ ] **Step 3: Run focused backend test again**

From repository root:

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchStreamMessageMapperTest test
```

Expected: PASS.

- [ ] **Step 4: Run full repository verification**

Use the repository verification script for the current shell:

Windows PowerShell:

```powershell
.\scripts\verify.ps1
```

Linux/macOS/Git Bash:

```bash
./scripts/verify.sh
```

Expected: exit code `0` with backend and frontend verification green. Do not claim completion from focused tests alone.

- [ ] **Step 5: Perform manual owner-control smoke test**

Run the application using the existing local development workflow, then verify these exact behaviors in the browser:

1. On an idle/finished match, active White/Black selectors and `Randomize Rivalry` are visible and editable.
2. Start a match. The setup becomes read-only and shows the authoritative White/Black names; selectors and Randomize are absent.
3. Stop the match. `Resume Match` remains visible but is disabled while the cooldown message is active.
4. Wait for the authoritative cooldown refresh. Resume becomes enabled only after `startAvailability.allowed` becomes true.
5. Resume the match and confirm the same White/Black names remain displayed.
6. Refresh/reconnect while active or stopped and confirm the names are restored from the match snapshot.

The inactive-personality edge is covered by the automated test using stored keys absent from the active roster; do not add personality-editing UI or database tooling just for manual simulation.

- [ ] **Step 6: Inspect final diff for scope**

```bash
git status --short
git diff --stat origin/master...HEAD
git diff origin/master...HEAD -- client/src/features/admin/MatchAdminControls.tsx client/src/features/admin/MatchAdminControls.test.tsx server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchStreamMessageMapperTest.java
```

Confirm this follow-up introduced no backend production changes, no new dependency, and no unrelated refactor.

- [ ] **Step 7: Commit any formatting-only follow-up if needed**

Only if Step 1 changed already-touched files after the task commits:

```bash
git add client/src/features/admin/MatchAdminControls.tsx client/src/features/admin/MatchAdminControls.test.tsx
git commit -m "style: format PR 58 review fixes"
```

If formatting produced no diff, skip this commit.

---

## Completion Checklist

- [ ] `Resume Match` is visible but disabled while a stopped match is cooldown/quota blocked.
- [ ] Resume requires both stored identities and `startAvailability.allowed === true`.
- [ ] Roster API failure does not block an otherwise-allowed stopped-match resume.
- [ ] Running/stopped controls show snapshot-derived White/Black display names.
- [ ] Running/stopped controls never show editable personality selectors or Randomize.
- [ ] A stored identity absent from the active roster is still displayed correctly.
- [ ] Idle/finished pre-match selection and randomization are unchanged.
- [ ] `MATCH_STATE` mapper test explicitly asserts both identity payloads.
- [ ] No backend production behavior changed.
- [ ] Focused frontend/backend tests pass.
- [ ] Full repository verification passes.
- [ ] Manual start → stop → cooldown → resume → refresh lifecycle behaves correctly.

## Execution Handoff

Execute this plan on `feature/issue-44-personality-selection` using `superpowers:executing-plans` inline only. Work task-by-task, run each task's verification before committing it, and stop on any unexpected failure rather than broadening the fix.