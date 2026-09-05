# Production WebSocket URL Fix Implementation Plan

> **For Luna:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the deployed frontend derive the match WebSocket host from the same `VITE_API_URL` source used by REST so production connects to `wss://ai-chess-api.krishnamurti.dev/ws/match` instead of `ws://localhost:8082/ws/match`.

**Architecture:** Keep one backend-origin source of truth. `client/src/services/matchApi.ts` already resolves `API_BASE_URL` from `VITE_API_URL` with the local fallback; `useMatchStream` should consume that exported value by default while preserving its explicit override parameter. `createMatchSocket` remains responsible for converting `http/https` to `ws/wss` and rooting the socket at `/ws/match`.

**Tech Stack:** React 19, TypeScript 6, Vite 8, Vitest 4, GitHub Actions Pages workflow.

**Spec:** GitHub issue #67 — `https://github.com/krishna916/ai-chess-rivals/issues/67`

## Global Constraints

- Keep `VITE_API_URL` as the only frontend backend-location environment variable.
- Do not add `VITE_WS_URL` or another WebSocket-specific environment variable.
- Preserve local fallback behavior through `API_BASE_URL`: `http://localhost:8082/api/v1`.
- Preserve `useMatchStream(baseUrl?: string)` explicit override behavior.
- Do not change backend WebSocket routing, Render port configuration, reconnect/backoff behavior, or AI provider configuration.
- Use TDD: add the regression test first, prove it fails for the current localhost default, then make the minimum implementation change.
- Run commands from `client/` unless a step explicitly says repository root.

---

### Task 1: Lock the production backend-base behavior with a failing hook test

**Files:**
- Modify: `client/src/hooks/useMatchStream.test.ts`

**Interfaces:**
- Consumes: `useMatchStream(baseUrl?: string)` and mocked `createMatchSocket(baseHttpUrl, callbacks)`.
- Produces: regression coverage proving the no-argument hook uses the configured API base while an explicit argument still overrides it.

- [ ] **Step 1: Mock the shared API base URL in `useMatchStream.test.ts`**

Add this mock next to the existing `matchSocket` mock:

```ts
vi.mock("../services/matchApi", () => ({
  API_BASE_URL: "https://ai-chess-api.krishnamurti.dev/api/v1",
}));
```

In `beforeEach`, also clear the mocked factory so assertions do not leak across tests:

```ts
vi.mocked(createMatchSocket).mockClear();
```

- [ ] **Step 2: Add the production-default regression test**

Add this test inside `describe("useMatchStream", ...)`:

```ts
it("uses the configured API base URL by default", () => {
  const { unmount } = renderHook(() => useMatchStream());

  expect(createMatchSocket).toHaveBeenCalledWith(
    "https://ai-chess-api.krishnamurti.dev/api/v1",
    expect.any(Object),
  );

  unmount();
});
```

- [ ] **Step 3: Add an explicit-override regression test**

Add:

```ts
it("preserves an explicit WebSocket backend base override", () => {
  const { unmount } = renderHook(() =>
    useMatchStream("http://localhost:9999/api/v1"),
  );

  expect(createMatchSocket).toHaveBeenCalledWith(
    "http://localhost:9999/api/v1",
    expect.any(Object),
  );

  unmount();
});
```

- [ ] **Step 4: Run only the hook test and prove the production-default case fails**

Run:

```bash
npm test -- src/hooks/useMatchStream.test.ts
```

Expected before the fix:
- the new `uses the configured API base URL by default` test FAILS;
- failure shows the current received base URL is `http://localhost:8082` rather than `https://ai-chess-api.krishnamurti.dev/api/v1`;
- the explicit-override test passes.

Do not modify production code until this failure is observed.

---

### Task 2: Make the WebSocket hook reuse `API_BASE_URL`

**Files:**
- Modify: `client/src/hooks/useMatchStream.ts`
- Test: `client/src/hooks/useMatchStream.test.ts`

**Interfaces:**
- Consumes: `API_BASE_URL: string` exported by `client/src/services/matchApi.ts`.
- Produces: `useMatchStream(baseUrl = API_BASE_URL)`; existing callers can still pass an explicit base URL.

- [ ] **Step 1: Import the shared backend base URL**

In `client/src/hooks/useMatchStream.ts`, add:

```ts
import { API_BASE_URL } from "../services/matchApi";
```

Keep the existing imports otherwise unchanged.

- [ ] **Step 2: Replace only the hard-coded default**

Change:

```ts
export function useMatchStream(baseUrl = "http://localhost:8082") {
```

to:

```ts
export function useMatchStream(baseUrl = API_BASE_URL) {
```

Do not change reconnect timing, callbacks, store interactions, or `createMatchSocket`.

- [ ] **Step 3: Run the focused test**

Run:

```bash
npm test -- src/hooks/useMatchStream.test.ts
```

Expected: PASS for all `useMatchStream` tests, including both new URL-selection tests.

- [ ] **Step 4: Run formatting checks for the touched TypeScript files**

Run:

```bash
npx prettier --check src/hooks/useMatchStream.ts src/hooks/useMatchStream.test.ts
```

Expected: PASS. If it fails, run:

```bash
npx prettier --write src/hooks/useMatchStream.ts src/hooks/useMatchStream.test.ts
```

then rerun the check.

- [ ] **Step 5: Commit the behavior fix**

From repository root:

```bash
git add client/src/hooks/useMatchStream.ts client/src/hooks/useMatchStream.test.ts
git commit -m "fix: derive match WebSocket from API base URL"
```

---

### Task 3: Strengthen the Pages production-bundle localhost guard

**Files:**
- Modify: `.github/workflows/pages.yml`

**Interfaces:**
- Consumes: production `client/dist` generated with repository variable `VITE_API_URL`.
- Produces: deployment failure if any compiled frontend code still references the local backend `localhost:8082`.

- [ ] **Step 1: Replace the overly narrow localhost grep**

Find the existing Pages step that rejects the exact REST fallback:

```yaml
- name: Reject localhost API fallback in production bundle
  shell: bash
  run: |
    set -euo pipefail
    if grep -R -F "http://localhost:8082/api/v1" dist; then
      echo "Production bundle contains the localhost API fallback."
      exit 1
    fi
```

Replace it with:

```yaml
- name: Reject localhost backend references in production bundle
  shell: bash
  run: |
    set -euo pipefail
    if grep -R -F "localhost:8082" dist; then
      echo "Production bundle contains a localhost backend reference."
      exit 1
    fi
```

This intentionally catches both REST and WebSocket regressions without introducing another deployment mechanism.

- [ ] **Step 2: Build exactly like production and run the same guard locally**

From `client/`, run in a POSIX-compatible shell:

```bash
VITE_API_URL="https://ai-chess-api.krishnamurti.dev/api/v1" npm run build
```

Then run:

```bash
if grep -R -F "localhost:8082" dist; then
  echo "Production bundle contains a localhost backend reference."
  exit 1
fi
```

Expected: build succeeds and grep finds no `localhost:8082` reference.

- [ ] **Step 3: Commit the deployment regression guard**

From repository root:

```bash
git add .github/workflows/pages.yml
git commit -m "ci: reject localhost backend references in Pages build"
```

---

### Task 4: Run full frontend verification and prepare the PR

**Files:**
- Verify only; no new files unless a verification failure proves a bug in the changes above.

**Interfaces:**
- Produces: evidence that the narrow WebSocket fix does not regress frontend behavior or production build output.

- [ ] **Step 1: Run the complete frontend verification suite**

From `client/`:

```bash
npm run verify
```

Expected: lint, formatting, typecheck, tests, and build all PASS according to the existing verify script.

- [ ] **Step 2: Run a final production build with the deployed API URL**

```bash
VITE_API_URL="https://ai-chess-api.krishnamurti.dev/api/v1" npm run build
```

Expected: PASS.

- [ ] **Step 3: Confirm no production localhost backend reference remains**

```bash
if grep -R -F "localhost:8082" dist; then
  echo "Unexpected localhost backend reference remains in production bundle."
  exit 1
fi
```

Expected: command exits successfully with no matches.

- [ ] **Step 4: Inspect the final diff for scope discipline**

From repository root:

```bash
git diff master...HEAD -- client/src/hooks/useMatchStream.ts client/src/hooks/useMatchStream.test.ts .github/workflows/pages.yml
git diff --check master...HEAD
```

Expected:
- only the shared API-base default, focused regression tests, and generalized Pages localhost guard are present;
- no `VITE_WS_URL`;
- no backend code changes;
- no reconnect/backoff changes;
- `git diff --check` reports no whitespace errors.

- [ ] **Step 5: Push and open a PR linked to issue #67**

```bash
git push -u origin fix/issue-67-production-websocket-url
```

Open a PR to `master` with a body containing:

```markdown
Closes #67

## Summary
- derive the match WebSocket backend from the existing `API_BASE_URL`
- preserve explicit hook overrides and local fallback behavior
- reject any `localhost:8082` reference from the production Pages bundle

## Verification
- `npm test -- src/hooks/useMatchStream.test.ts`
- `npm run verify`
- production build with `VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1`
- production bundle contains no `localhost:8082`
```

- [ ] **Step 6: After merge and Pages deployment, perform the production acceptance check**

Open:

```text
https://ai-chess.krishnamurti.dev/#/
```

In browser DevTools → Network → WS, verify the active match socket is:

```text
wss://ai-chess-api.krishnamurti.dev/ws/match
```

Verify all of the following:
- there is no `ws://localhost:8082/ws/match` request;
- the socket handshake succeeds;
- live match messages continue to arrive;
- REST requests still target `https://ai-chess-api.krishnamurti.dev/api/v1/...`.

If Luna cannot access the deployed browser environment, stop after the merged CI/Pages deployment is green and report this exact manual acceptance check to the user rather than claiming it was performed.
