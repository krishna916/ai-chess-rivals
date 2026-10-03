#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname -- "${BASH_SOURCE[0]}")"

export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/aichessrivals"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_FLYWAY_URL="jdbc:postgresql://localhost:5433/aichessrivals"
export SPRING_FLYWAY_USER="postgres"

database_password="${SPRING_DATASOURCE_PASSWORD:-${SPRING_FLYWAY_PASSWORD:-}}"
if [[ -z "$database_password" ]]; then
    echo "Set SPRING_DATASOURCE_PASSWORD (or SPRING_FLYWAY_PASSWORD) before running this script." >&2
    exit 1
fi

export SPRING_DATASOURCE_PASSWORD="$database_password"
export SPRING_FLYWAY_PASSWORD="$database_password"
export STOCKFISH_PATH="stockfish/stockfish"
export SERVER_PORT="8082"

exec ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
