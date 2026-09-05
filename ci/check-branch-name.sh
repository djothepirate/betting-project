#!/usr/bin/env sh
set -eu

branch_name=${1:-}
context=${2:-all}

case "$context" in
    all|feature-integration|feature-work-order|gitlab-release|gitlab-branch) ;;
    *)
        echo "FAIL: contexte de validation de branche inconnu : ${context:-<vide>}." >&2
        exit 1
        ;;
esac

semver_core='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
train_suffix='(-RC(0[1-9]|[1-9][0-9])(-SNAPSHOT)?)?'
integration_pattern="^feature/V${semver_core}${train_suffix}$"
work_order_id='[A-Z]+-(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})'
work_order_pattern="^feature/V${semver_core}${train_suffix}-(CODEX|HUMAN)-${work_order_id}$"
release_pattern="^release/V${semver_core}${train_suffix}$"

if { [ "$context" = all ] || [ "$context" = gitlab-branch ]; } &&
   [ "$branch_name" = main ]; then
    printf 'BRANCH_NAME=PASS:main:%s\n' "$branch_name"
    exit 0
fi

if [ "$context" = all ] || [ "$context" = feature-integration ] ||
   [ "$context" = gitlab-branch ]; then
    if printf '%s' "$branch_name" | grep -Eq "$integration_pattern"; then
        printf 'BRANCH_NAME=PASS:feature-integration:%s\n' "$branch_name"
        exit 0
    fi
fi

if [ "$context" = all ] || [ "$context" = feature-work-order ]; then
    if printf '%s' "$branch_name" | grep -Eq "$work_order_pattern"; then
        printf 'BRANCH_NAME=PASS:feature-work-order:%s\n' "$branch_name"
        exit 0
    fi
fi

if [ "$context" = all ] || [ "$context" = gitlab-release ] ||
   [ "$context" = gitlab-branch ]; then
    if printf '%s' "$branch_name" | grep -Eq "$release_pattern"; then
        printf 'BRANCH_NAME=PASS:gitlab-release:%s\n' "$branch_name"
        exit 0
    fi
fi

echo "FAIL: branche hors convention versionnée ($context) : ${branch_name:-<vide>}." >&2
exit 1
