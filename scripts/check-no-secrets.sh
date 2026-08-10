#!/usr/bin/env sh
set -eu

repository=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
pattern="(api[_-]?key|authorization|client[_-]?secret|access[_-]?token)[[:space:]]*[:=][[:space:]]*['\"]?[A-Za-z0-9_.-]{20,}|https?://[^[:space:]/]*sofascore[^[:space:]]*api[^[:space:]]*"

if grep -RInE "$pattern" "$repository" \
    --exclude-dir=.git \
    --exclude-dir=target \
    --exclude=.env.example \
    --exclude=check-no-secrets.sh; then
    echo 'FAIL: valeur sensible potentielle détectée.' >&2
    exit 1
fi

echo 'PASS: aucun secret à forte confiance ni endpoint privé SofaScore détecté.'
