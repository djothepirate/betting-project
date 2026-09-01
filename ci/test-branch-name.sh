#!/usr/bin/env sh
set -eu

for branch_name in \
    main \
    codex/ci-001-gitlab-ci-bootstrap \
    human/ci-001-gitlab-ci-bootstrap \
    codex/ss-20260901-031-ci-bootstrap \
    hotfix/inc-123-rollback-database; do
    if ! sh ci/check-branch-name.sh "$branch_name" >/dev/null; then
        echo "FAIL: branche 1A valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    '' \
    develop \
    release/1.2.3 \
    codex/ \
    codex/ci-001 \
    codex/CI-001-gitlab \
    bot/ci-001-gitlab \
    hotfix/123-database; do
    if sh ci/check-branch-name.sh "$branch_name" >/dev/null 2>&1; then
        echo "FAIL: branche hors convention 1A acceptée : $branch_name" >&2
        exit 1
    fi
done

printf 'BRANCH_NAMING_1A=PASS\n'
