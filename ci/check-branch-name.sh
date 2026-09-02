#!/usr/bin/env sh
set -eu

branch_name=${1:-}
context=${2:-all}

case "$context" in
    all|github-pull-request) ;;
    *)
        echo "FAIL: contexte de validation de branche inconnu : ${context:-<vide>}." >&2
        exit 1
        ;;
esac

if [ "$branch_name" = main ]; then
    if [ "$context" = github-pull-request ]; then
        echo 'FAIL: main ne peut pas être une branche source de Pull Request GitHub.' >&2
        exit 1
    fi
    printf 'BRANCH_NAME=PASS:%s\n' "$branch_name"
    exit 0
fi

if printf '%s' "$branch_name" | grep -Eq \
    '^release/(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-rc\.[1-9][0-9]*)?$'; then
    if [ "$context" = github-pull-request ]; then
        echo "FAIL: une branche de promotion GitLab ne peut pas être une source de Pull Request GitHub : $branch_name." >&2
        exit 1
    fi
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
