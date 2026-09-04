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
