#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

exec docker compose -f "$ROOT_DIR/server/docker-compose.yml" up -d postgres
