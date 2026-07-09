---
name: sync-env-example
description: Keep `server/.env.example` aligned with the current backend environment variable contract. Use when `server/.env`, `server/.env.example`, `server/docker-compose.yml`, `server/src/main/resources/application.yaml`, deployment env docs, or other env-backed backend configuration changes add, remove, rename, or repurpose environment variables.
---

# Sync Env Example

## Core Rule
Whenever the backend environment variable contract changes, update `server/.env.example` in the same change.

## Scope
Treat these files as the primary signals that the example env file may need an update:

- `server/.env`
- `server/.env.example`
- `server/docker-compose.yml`
- `server/src/main/resources/application.yaml`
- Backend documentation that lists required env vars

## Required Workflow
1. Inspect the current env-backed configuration sources.
2. Build the authoritative key list from the code and deployment contract, not from memory.
3. Compare that list against `server/.env.example`.
4. Add missing keys, remove obsolete keys, and rename outdated keys.
5. Keep placeholder-only values in the example file. Never copy real secrets or local-only credentials into it.
6. Preserve `server/.env` as the real local untracked file for developer-specific values.

## Placeholder Rules
- Use descriptive placeholders such as `<jdbc-database-url>` or `<database-password>`.
- Do not use realistic-looking secrets such as `secretpassword`.
- Keep stable non-secret defaults only when they are intentional runtime defaults, for example `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` or `STOCKFISH_PATH=stockfish/stockfish`.

## Red Flags
- Updating `server/.env` without checking `server/.env.example`.
- Adding a new `${ENV_VAR:...}` binding in `server/src/main/resources/application.yaml` and leaving the example file stale.
- Leaving Docker-local values in the example file when the file is intended to document the broader backend env contract.
