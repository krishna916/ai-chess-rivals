#!/usr/bin/env sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

echo 'Verifying backend...'
"$repository_root/server/mvnw" -f "$repository_root/server/pom.xml" verify

echo 'Verifying frontend...'
cd "$repository_root/client"
npm run verify
