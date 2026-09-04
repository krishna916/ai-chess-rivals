# GitHub Pages Custom-Domain Frontend Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` only and execute this plan inline task-by-task. Do **not** use or dispatch `superpowers:subagent-driven-development`.

**Goal:** Deploy the React/Vite frontend to GitHub Pages at `https://ai-chess.krishnamurti.dev`, connect it to the Render backend at `https://ai-chess-api.krishnamurti.dev`, and make browser REST/WebSocket access work without weakening production origin restrictions.

**Architecture:** Keep the frontend as a static Vite build and deploy `client/dist` through the official GitHub Pages Actions flow. Use `HashRouter` so GitHub Pages does not need SPA history rewrites, keep Vite rooted at `/` because the site uses a custom host, inject the production backend URL through a GitHub Actions repository variable, and reuse the existing `APP_WEBSOCKET_ALLOWED_ORIGIN` value for the two public REST controllers so REST and WebSocket access are restricted to the same frontend origin.

**Tech Stack:** React 19, React Router 7, Vite 8, TypeScript 6, Vitest, Spring Boot 4.1, Spring MVC, GitHub Actions, GitHub Pages, Render, Cloudflare DNS.

**Spec:** GitHub issue #65 — `https://github.com/krishna916/ai-chess-rivals/issues/65`

**Source of truth:** Issue #65 as updated for the custom domains on 2026-09-04.

## Global Constraints

- Canonical frontend URL: `https://ai-chess.krishnamurti.dev`.
- Canonical backend URL: `https://ai-chess-api.krishnamurti.dev`.
- Production frontend API value: `VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1`.
- Production allowed browser origin: `https://ai-chess.krishnamurti.dev`.
- Preserve local development defaults: frontend API fallback `http://localhost:8082/api/v1` and browser origin `http://localhost:5173`.
- Keep GitHub Pages as the frontend host. Do not add Vercel, Netlify, Cloudflare Pages, SSR, or another deployment platform.
- Use `HashRouter`; do not introduce a `404.html` SPA rewrite workaround.
- Production Vite base is `/`; do not use `/ai-chess-rivals/` once the custom domain is active.
- Do not hard-code the Render backend hostname in TypeScript. Supply `VITE_API_URL` through GitHub Actions configuration.
- Do not use wildcard `*` CORS in production.
- Reuse `APP_WEBSOCKET_ALLOWED_ORIGIN` as the single external production frontend-origin value for both WebSocket and REST controller CORS.
- Keep the provider URLs (`krishna916.github.io` and `ai-chess-rivals.onrender.com`) available during cutover/rollback.
- No backend architecture changes, auth redesign, database changes, AI behavior changes, or deployment framework additions.
- Execution is inline with `superpowers:executing-plans` only.

## File Map

### Create

- `.github/workflows/pages.yml` — verifies, builds, uploads, and deploys the Vite frontend to GitHub Pages.

### Modify

- `client/src/App.tsx` — switch the application router from `BrowserRouter` to `HashRouter`.
- `client/src/App.test.tsx` — prove root and `/admin` routing through URL hashes.
- `client/vite.config.ts` — make the production root base explicit with `base: "/"`.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java` — replace the hard-coded localhost CORS origin with the configured frontend-origin value.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java` — replace the hard-coded localhost CORS origin with the configured frontend-origin value.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java` — prove the configured production origin receives the REST CORS header.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java` — prove the configured production origin receives the REST CORS header.
- `client/README.md` — replace the Vite template text with project-specific local/build/deployment guidance.
- `docs/BUILD_AND_VERIFY.md` — document Pages/Render/DNS setup and production smoke checks.

### Intentionally unchanged

- `client/src/services/matchApi.ts` — already reads `VITE_API_URL` and falls back locally.
- `client/src/services/matchSocket.ts` — already derives `ws:`/`wss:` from the HTTP backend base URL.
- `server/src/main/resources/application.yaml` — already maps `APP_WEBSOCKET_ALLOWED_ORIGIN` into `app.websocket.allowed-origins` for WebSocket handshakes.
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/websocket/MatchWebSocketConfig.java` — already applies the configured WebSocket origins.
- `server/Dockerfile` and backend deployment workflow — unrelated to frontend hosting.

---

### Task 1: Make frontend routing safe for GitHub Pages and root-host deployment

**Files:**
- Modify: `client/src/App.test.tsx`
- Modify: `client/src/App.tsx`
- Modify: `client/vite.config.ts`

**Interfaces:**
- Consumes: React Router routes `/` and `/admin` already defined by `App`.
- Produces: hash routes `#/` and `#/admin`, with Vite assets rooted at `/` on the custom domain.

- [ ] **Step 1: Change the route tests first so they express hash-based navigation**

Replace `client/src/App.test.tsx` with:

```tsx
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

vi.mock("./pages/MatchViewerPage", () => ({
  MatchViewerPage: () => <h1>Read-only Match Viewer</h1>,
}));
vi.mock("./pages/AdminPage", () => ({
  AdminPage: () => <h1>Locked Owner Controls</h1>,
}));

describe("App routes", () => {
  afterEach(() => {
    cleanup();
    window.location.hash = "";
  });

  it("routes the public hash root to the read-only viewer", () => {
    window.location.hash = "#/";
    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Read-only Match Viewer" }),
    ).toBeVisible();
  });

  it("routes #/admin to the locked owner page", () => {
    window.location.hash = "#/admin";
    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Locked Owner Controls" }),
    ).toBeVisible();
  });
});
```

- [ ] **Step 2: Run the focused frontend route tests and confirm the admin hash route fails before implementation**

```bash
cd client
npm test -- src/App.test.tsx
```

Expected before implementation: the `#/admin` test fails because `BrowserRouter` reads the pathname, not the hash route.

- [ ] **Step 3: Switch `App` to `HashRouter`**

Replace `client/src/App.tsx` with:

```tsx
import { HashRouter, Route, Routes } from "react-router-dom";
import { AdminPage } from "./pages/AdminPage";
import { MatchViewerPage } from "./pages/MatchViewerPage";

function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/" element={<MatchViewerPage />} />
        <Route path="/admin" element={<AdminPage />} />
      </Routes>
    </HashRouter>
  );
}

export default App;
```

- [ ] **Step 4: Make the custom-domain Vite base explicit**

Change `client/vite.config.ts` so the `defineConfig` object begins:

```ts
export default defineConfig({
  base: "/",
  plugins: [react(), tailwindcss()],
```

Leave the existing aliases and Vitest configuration unchanged.

- [ ] **Step 5: Re-run the focused route tests**

```bash
cd client
npm test -- src/App.test.tsx
```

Expected: both route tests pass.

- [ ] **Step 6: Build with the final production API URL and inspect generated asset paths**

POSIX:

```bash
cd client
VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1 npm run build
grep -F "/ai-chess-rivals/" dist/index.html && exit 1 || true
grep -F "/assets/" dist/index.html
```

PowerShell:

```powershell
cd client
$env:VITE_API_URL = "https://ai-chess-api.krishnamurti.dev/api/v1"
npm run build
if (Select-String -Path "dist\index.html" -Pattern "/ai-chess-rivals/" -SimpleMatch) { exit 1 }
Select-String -Path "dist\index.html" -Pattern "/assets/" -SimpleMatch
```

Expected: build succeeds; no `/ai-chess-rivals/` reference exists; `/assets/` references exist.

- [ ] **Step 7: Commit Task 1**

```bash
git add client/src/App.tsx client/src/App.test.tsx client/vite.config.ts
git commit -m "feat: prepare frontend routing for GitHub Pages"
```

---

### Task 2: Make REST CORS follow the configured production frontend origin

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java`

**Interfaces:**
- Consumes: external environment value `APP_WEBSOCKET_ALLOWED_ORIGIN`.
- Produces: `Access-Control-Allow-Origin: https://ai-chess.krishnamurti.dev` for browser calls to `/api/v1/match` and `/api/v1/personalities` when Render is configured with that origin.

- [ ] **Step 1: Configure both controller slice tests with the production-like allowed origin**

Change the `@TestPropertySource` declaration in both controller test classes to:

```java
@TestPropertySource(
    properties = {
      "app.owner.control-token=test-owner-token",
      "APP_WEBSOCKET_ALLOWED_ORIGIN=https://ai-chess.krishnamurti.dev"
    })
```

- [ ] **Step 2: Add the configured-origin test to `MatchControllerTest`**

Add:

```java
@Test
void currentMatchAllowsConfiguredFrontendOrigin() throws Exception {
  Match match = TestMatchFixtures.newMatch();
  when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, false));

  mockMvc
      .perform(
          get("/api/v1/match")
              .header(HttpHeaders.ORIGIN, "https://ai-chess.krishnamurti.dev"))
      .andExpect(status().isOk())
      .andExpect(
          header()
              .string(
                  HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                  "https://ai-chess.krishnamurti.dev"));
}
```

`MatchControllerTest` already imports `HttpHeaders` and result matchers through its existing imports.

- [ ] **Step 3: Add the configured-origin test to `PersonalityControllerTest`**

Add these imports:

```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
```

```java
import org.springframework.http.HttpHeaders;
```

Then add:

```java
@Test
void listPersonalitiesAllowsConfiguredFrontendOrigin() throws Exception {
  when(personalityService.listSelectable()).thenReturn(List.of());

  mockMvc
      .perform(
          get("/api/v1/personalities")
              .header(HttpHeaders.ORIGIN, "https://ai-chess.krishnamurti.dev"))
      .andExpect(status().isOk())
      .andExpect(
          header()
              .string(
                  HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                  "https://ai-chess.krishnamurti.dev"));
}
```

- [ ] **Step 4: Run only the two controller tests and prove the new assertions fail against hard-coded localhost CORS**

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchControllerTest,PersonalityControllerTest test
```

Windows PowerShell:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchControllerTest,PersonalityControllerTest test
```

Expected before implementation: the new production-origin CORS assertions fail because both controllers currently hard-code `http://localhost:5173`.

- [ ] **Step 5: Replace the hard-coded origin in `MatchController`**

Change:

```java
@CrossOrigin(origins = "http://localhost:5173")
```

to:

```java
@CrossOrigin(origins = "${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}")
```

Do not change endpoint mappings or authorization behavior.

- [ ] **Step 6: Replace the hard-coded origin in `PersonalityController`**

Make the same annotation change:

```java
@CrossOrigin(origins = "${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}")
```

This deliberately reuses the external variable already used by WebSocket configuration instead of adding another production-origin variable.

- [ ] **Step 7: Re-run the focused backend tests**

POSIX:

```bash
./server/mvnw -f server/pom.xml -Dtest=MatchControllerTest,PersonalityControllerTest test
```

Windows PowerShell:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=MatchControllerTest,PersonalityControllerTest test
```

Expected: both controller test classes pass, including the production-origin CORS assertions.

- [ ] **Step 8: Verify the committed controller annotations retain the local default**

POSIX:

```bash
grep -F '${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}' \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java
```

PowerShell:

```powershell
Select-String -Path \
  "server\src\main\java\dev\krishnamurti\ai_chess_rivals\game\web\MatchController.java", \
  "server\src\main\java\dev\krishnamurti\ai_chess_rivals\ai\personality\PersonalityController.java" \
  -Pattern '${APP_WEBSOCKET_ALLOWED_ORIGIN:http://localhost:5173}' -SimpleMatch
```

Expected: one match in each controller.

- [ ] **Step 9: Commit Task 2**

```bash
git add \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java
git commit -m "fix: configure frontend REST origin"
```

---

### Task 3: Add the GitHub Pages deployment workflow

**Files:**
- Create: `.github/workflows/pages.yml`

**Interfaces:**
- Consumes: GitHub Actions repository variable `VITE_API_URL` with value `https://ai-chess-api.krishnamurti.dev/api/v1`.
- Produces: verified `client/dist` GitHub Pages artifact and deployment to the `github-pages` environment.

- [ ] **Step 1: Create `.github/workflows/pages.yml`**

Create exactly:

```yaml
name: Deploy frontend to GitHub Pages

on:
  push:
    branches:
      - master
    paths:
      - "client/**"
      - ".github/workflows/pages.yml"
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  deploy:
    name: Verify, build and deploy frontend
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: client
    env:
      VITE_API_URL: ${{ vars.VITE_API_URL }}
    steps:
      - name: Check out repository
        uses: actions/checkout@v7

      - name: Set up Node.js 22
        uses: actions/setup-node@v7
        with:
          node-version: "22"
          cache: npm
          cache-dependency-path: client/package-lock.json

      - name: Configure GitHub Pages
        uses: actions/configure-pages@v6

      - name: Install frontend dependencies
        run: npm ci

      - name: Validate production API URL
        shell: bash
        run: |
          set -euo pipefail
          if [[ -z "${VITE_API_URL}" ]]; then
            echo "GitHub Actions repository variable VITE_API_URL is not configured."
            exit 1
          fi

      - name: Verify and build frontend
        run: npm run verify

      - name: Reject localhost API fallback in production bundle
        shell: bash
        run: |
          set -euo pipefail
          if grep -R -F "http://localhost:8082/api/v1" dist; then
            echo "Production bundle contains the localhost API fallback."
            exit 1
          fi

      - name: Upload GitHub Pages artifact
        uses: actions/upload-pages-artifact@v5
        with:
          path: client/dist

      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v5
```

`npm run verify` already runs format check, typecheck, lint, tests, and `npm run build`; do not add a second production-build step.

Current official Pages action majors verified on 2026-09-04:
- `actions/configure-pages@v6`
- `actions/upload-pages-artifact@v5`
- `actions/deploy-pages@v5`

Keep repository conventions `actions/checkout@v7` and `actions/setup-node@v7`.

- [ ] **Step 2: Reproduce the workflow's frontend verification locally with the exact production URL**

POSIX:

```bash
cd client
npm ci
VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1 npm run verify
```

Windows PowerShell:

```powershell
cd client
npm ci
$env:VITE_API_URL = "https://ai-chess-api.krishnamurti.dev/api/v1"
npm run verify
```

Expected: all frontend checks pass and `dist/` is produced by the final build check.

- [ ] **Step 3: Prove the generated bundle does not contain the localhost API fallback**

POSIX:

```bash
cd client
if grep -R -F "http://localhost:8082/api/v1" dist; then exit 1; fi
```

PowerShell:

```powershell
cd client
$match = Get-ChildItem -Recurse -File dist | Select-String -Pattern "http://localhost:8082/api/v1" -SimpleMatch
if ($match) { exit 1 }
```

Expected: no match.

- [ ] **Step 4: Check workflow whitespace and diff**

```bash
git diff --check
git diff -- .github/workflows/pages.yml
```

Expected: `git diff --check` exits 0; workflow contains only the intended Pages flow.

- [ ] **Step 5: Commit Task 3**

```bash
git add .github/workflows/pages.yml
git commit -m "ci: deploy frontend to GitHub Pages"
```

---

### Task 4: Replace template frontend documentation and document production cutover

**Files:**
- Modify: `client/README.md`
- Modify: `docs/BUILD_AND_VERIFY.md`

**Interfaces:**
- Consumes: deployment decisions from Tasks 1-3.
- Produces: exact local, GitHub Pages, Render, DNS, and smoke-test instructions for future maintenance.

- [ ] **Step 1: Replace the Vite template `client/README.md` with project-specific frontend instructions**

Use this content:

````markdown
# AI Chess Rivals Client

React/Vite frontend for the AI Chess Rivals showcase.

## Local development

```bash
npm ci
npm run dev
```

Local defaults:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8082/api/v1`

`VITE_API_URL` can override the backend API base for a build or local invocation.

## Verification

```bash
npm run verify
```

## Production

The frontend is deployed to GitHub Pages through `.github/workflows/pages.yml`.

Canonical production URLs:

- Frontend: `https://ai-chess.krishnamurti.dev`
- Backend: `https://ai-chess-api.krishnamurti.dev`
- Production API value: `VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1`

GitHub Pages uses the custom host root, so Vite base is `/`. Routing uses `HashRouter`, therefore the owner page is `https://ai-chess.krishnamurti.dev/#/admin`.

The GitHub Actions repository variable `VITE_API_URL` is public configuration, not a secret. Do not put credentials or the owner control token in Vite variables or the frontend bundle.
````

- [ ] **Step 2: Add a `Frontend production deployment` section to `docs/BUILD_AND_VERIFY.md`**

Document this exact setup and cutover order:

1. Render image-backed backend:
   - add custom domain `ai-chess-api.krishnamurti.dev`;
   - DNS CNAME `ai-chess-api` -> `ai-chess-rivals.onrender.com`;
   - set `APP_WEBSOCKET_ALLOWED_ORIGIN=https://ai-chess.krishnamurti.dev`;
   - keep `ai-chess-rivals.onrender.com` available during verification.
2. Cloudflare DNS:
   - CNAME `ai-chess` -> `krishna916.github.io`;
   - CNAME `ai-chess-api` -> `ai-chess-rivals.onrender.com`;
   - if the currently-created record is `api-chess`, rename/remove it so the canonical backend hostname is `ai-chess-api.krishnamurti.dev`;
   - use DNS-only mode while GitHub Pages and Render validate domains/certificates; Cloudflare proxying is not required for the MVP.
3. GitHub repository settings:
   - Settings -> Pages -> Source = GitHub Actions;
   - custom domain = `ai-chess.krishnamurti.dev`;
   - enable Enforce HTTPS after GitHub verifies the domain and certificate;
   - Settings -> Secrets and variables -> Actions -> Variables -> `VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1`.
4. Deploy/re-run `.github/workflows/pages.yml` after Pages settings, DNS, and the repository variable are ready.
5. Verify both REST and WebSocket behavior before treating provider URLs as rollback-only paths.

Also document that this design does not use a committed `CNAME` file, generated `dist/` files, or a `gh-pages` branch.

- [ ] **Step 3: Add exact production smoke checks to `docs/BUILD_AND_VERIFY.md`**

REST CORS:

```bash
curl -i \
  -H "Origin: https://ai-chess.krishnamurti.dev" \
  https://ai-chess-api.krishnamurti.dev/api/v1/match
```

Acceptance:
- the application returns its normal match response (`200` when state exists, or its normal not-found response when no match exists);
- response includes `Access-Control-Allow-Origin: https://ai-chess.krishnamurti.dev`.

Frontend HTTPS:

```bash
curl -I https://ai-chess.krishnamurti.dev
```

Acceptance: the custom frontend hostname responds over HTTPS.

Browser acceptance:
- open `https://ai-chess.krishnamurti.dev/#/`;
- confirm viewer REST state loads;
- confirm the browser establishes `wss://ai-chess-api.krishnamurti.dev/ws/match`;
- open `https://ai-chess.krishnamurti.dev/#/admin` directly and refresh;
- confirm the admin route remains loaded after refresh.

- [ ] **Step 4: Run documentation diff checks**

```bash
git diff --check
git diff -- client/README.md docs/BUILD_AND_VERIFY.md
```

Expected: no whitespace errors and no credential/token values in documentation.

- [ ] **Step 5: Commit Task 4**

```bash
git add client/README.md docs/BUILD_AND_VERIFY.md
git commit -m "docs: document frontend production deployment"
```

---

### Task 5: Run full repository verification before opening the PR

**Files:**
- No new files expected.

**Interfaces:**
- Consumes: all implementation from Tasks 1-4.
- Produces: verified branch ready for review/merge.

- [ ] **Step 1: Run the full backend verifier**

POSIX:

```bash
./server/mvnw -f server/pom.xml verify
```

Windows PowerShell:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run the full frontend verifier with the production API URL**

POSIX:

```bash
cd client
VITE_API_URL=https://ai-chess-api.krishnamurti.dev/api/v1 npm run verify
```

Windows PowerShell:

```powershell
cd client
$env:VITE_API_URL = "https://ai-chess-api.krishnamurti.dev/api/v1"
npm run verify
```

Expected: format check, typecheck, lint, tests, and production build all pass.

- [ ] **Step 3: Verify the intended diff only**

```bash
git status --short
git diff --check
git diff master...HEAD --stat
git diff master...HEAD --name-only
```

Expected implementation paths are limited to:

```text
.github/workflows/pages.yml
client/README.md
client/src/App.test.tsx
client/src/App.tsx
client/vite.config.ts
docs/BUILD_AND_VERIFY.md
server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityController.java
server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchController.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/personality/PersonalityControllerTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/game/web/MatchControllerTest.java
```

The plan document itself is also expected because it was committed before implementation.

- [ ] **Step 4: Open the PR against `master`**

Suggested title:

```text
ci: deploy frontend to GitHub Pages
```

Suggested body:

```markdown
## Summary

- deploy the verified Vite frontend to GitHub Pages through the official Pages Actions flow
- use hash routing and root-host Vite assets for `ai-chess.krishnamurti.dev`
- configure production REST/WebSocket origin handling for the custom frontend domain
- document GitHub Pages, Render, Cloudflare DNS, and custom-domain cutover

## Production URLs

- Frontend: `https://ai-chess.krishnamurti.dev`
- Backend: `https://ai-chess-api.krishnamurti.dev`

## Verification

- backend Maven verifier passes
- frontend verifier passes with the production API URL
- focused CORS tests prove the configured frontend origin is allowed

Refs #65
```

---

### Task 6: Perform the one-time external production setup after implementation is ready

**Files:**
- No repository files unless observed provider behavior requires a documentation correction.

**Interfaces:**
- Consumes: merged implementation, GitHub Pages workflow, Render image-backed backend.
- Produces: live custom frontend and backend domains.

> **External-access checkpoint:** Luna must not claim these steps are complete unless it can actually observe/configure the relevant GitHub Pages, Render, and DNS settings. If those accounts are not available to the execution environment, stop here and hand this exact checklist to the user.

- [ ] **Step 1: Correct the backend DNS hostname**

Canonical record:

```text
Type: CNAME
Name: ai-chess-api
Target: ai-chess-rivals.onrender.com
Proxy: DNS only during setup
TTL: Auto
```

The screenshot/current setup showed `api-chess.krishnamurti.dev`; rename/remove that record so application configuration uses only `ai-chess-api.krishnamurti.dev` as the canonical backend hostname.

- [ ] **Step 2: Add the frontend DNS record**

```text
Type: CNAME
Name: ai-chess
Target: krishna916.github.io
Proxy: DNS only during setup
TTL: Auto
```

Leave the existing `krishnamurti.dev -> krishna916.github.io` portfolio record unchanged; the hostnames are distinct.

- [ ] **Step 3: Configure the Render custom backend domain**

In the image-backed Render service:

```text
Custom domain: ai-chess-api.krishnamurti.dev
APP_WEBSOCKET_ALLOWED_ORIGIN=https://ai-chess.krishnamurti.dev
```

Wait until Render reports the custom domain/TLS as verified before production browser acceptance.

- [ ] **Step 4: Configure GitHub Pages and the build variable**

Repository settings:

```text
Pages source: GitHub Actions
Pages custom domain: ai-chess.krishnamurti.dev
Actions variable name: VITE_API_URL
Actions variable value: https://ai-chess-api.krishnamurti.dev/api/v1
```

Enable `Enforce HTTPS` after GitHub reports the custom-domain certificate ready.

- [ ] **Step 5: Run or re-run the Pages deployment workflow**

Use `workflow_dispatch` if the merge-triggered deployment occurred before Pages settings or `VITE_API_URL` were configured.

Expected: `Verify, build and deploy frontend` completes successfully.

- [ ] **Step 6: Verify REST CORS through the backend custom domain**

```bash
curl -i \
  -H "Origin: https://ai-chess.krishnamurti.dev" \
  https://ai-chess-api.krishnamurti.dev/api/v1/match
```

Expected response header:

```text
Access-Control-Allow-Origin: https://ai-chess.krishnamurti.dev
```

- [ ] **Step 7: Verify browser end to end**

Check all four endpoints/routes:

```text
https://ai-chess.krishnamurti.dev/#/
https://ai-chess.krishnamurti.dev/#/admin
https://ai-chess-api.krishnamurti.dev/api/v1/match
wss://ai-chess-api.krishnamurti.dev/ws/match
```

Acceptance:
- frontend assets load without 404s;
- frontend REST calls use the custom API domain, not localhost or `ai-chess-rivals.onrender.com`;
- browser shows no REST CORS error;
- WebSocket connects through the custom API domain;
- `#/admin` survives direct navigation and refresh.

- [ ] **Step 8: Keep rollback paths until the custom-domain flow is proven stable**

Do not immediately disable:
- the GitHub Pages provider hostname;
- `ai-chess-rivals.onrender.com`;
- the old Git-backed Render backend if its separate retirement checklist is not complete.

No additional infrastructure is required once the custom-domain flow is stable.

---

## Final Verification Checklist

- [ ] `HashRouter` is used and `/` plus `/admin` tests pass via hashes.
- [ ] Vite base is `/`, not `/ai-chess-rivals/`.
- [ ] `VITE_API_URL` is injected through GitHub Actions and the workflow fails before verification if it is missing.
- [ ] Production bundle does not contain `http://localhost:8082/api/v1`.
- [ ] Both public REST controllers use the configured frontend origin instead of hard-coded localhost.
- [ ] WebSocket origin handling still uses `APP_WEBSOCKET_ALLOWED_ORIGIN`.
- [ ] GitHub Pages deployment uses official `configure-pages`, `upload-pages-artifact`, and `deploy-pages` actions.
- [ ] No `gh-pages` branch, committed `dist/`, or custom SPA 404 hack is introduced.
- [ ] No wildcard CORS is introduced.
- [ ] Local development defaults remain intact.
- [ ] Backend full verification passes.
- [ ] Frontend full verification passes with the production API URL.
- [ ] DNS uses `ai-chess` and `ai-chess-api` as the canonical subdomains.
- [ ] GitHub Pages serves `https://ai-chess.krishnamurti.dev` over HTTPS.
- [ ] Render serves `https://ai-chess-api.krishnamurti.dev` over HTTPS.
- [ ] REST and WebSocket work end to end through the two custom domains.

## Cost / Complexity Assessment

- **Entertainment Impact:** Medium — it makes the project publicly usable and portfolio-ready; it does not directly change match entertainment.
- **Learning Value:** High — practical static deployment, CI/CD, DNS, custom domains, CORS, and WebSocket origin configuration.
- **Complexity:** Low-Medium — one small workflow, one router change, two CORS annotations, tests, and one-time DNS/provider configuration.
- **Cost:** Low — GitHub Pages is free for this public repository and this design does not add another hosting provider.
- **MVP Recommendation:** Must Have for a public showcase.
- **Cheaper/Simpler Alternative:** Keep provider-generated `github.io` and `onrender.com` URLs, but that gives a weaker portfolio-facing experience and does not materially reduce implementation complexity now that the custom domain already exists.
