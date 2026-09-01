#!/usr/bin/env sh
set -eu

branch_name=${1:-}

if [ "$branch_name" = main ]; then
    printf 'BRANCH_NAME=PASS:%s\n' "$branch_name"
    exit 0
fi

if printf '%s' "$branch_name" | grep -Eq \
    '^((codex|human)/[a-z][a-z0-9]*(-[0-9]+)+-[a-z0-9]+(-[a-z0-9]+)*|hotfix/[a-z][a-z0-9]*(-[0-9]+)+-[a-z0-9]+(-[a-z0-9]+)*)$'; then
    printf 'BRANCH_NAME=PASS:%s\n' "$branch_name"
    exit 0
fi

echo "FAIL: branche hors convention 1A : $branch_name" >&2
exit 1
