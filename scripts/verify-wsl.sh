#!/usr/bin/env sh
set -eu

repository=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repository"

./mvnw -version
./mvnw verify
./scripts/check-no-secrets.sh

if command -v docker >/dev/null 2>&1; then
    ./mvnw -Pintegration verify
else
    echo 'WARN: Docker indisponible ; tests Testcontainers non exécutés.' >&2
fi
