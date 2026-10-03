#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR/server"

export STOCKFISH_PATH="stockfish/stockfish"

exec ./mvnw spring-boot:run
