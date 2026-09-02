#!/usr/bin/env sh
set -eu

for branch_name in \
    main \
    release/0.0.0 \
    release/0.1.0 \
    release/1.2.3 \
    release/1.2.3-rc.1 \
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
    release/latest \
    release/1.2 \
    release/01.2.3 \
    release/1.02.3 \
    release/1.2.03 \
    release/1.2.3-rc.0 \
    release/1.2.3-rc.01 \
    release/1.2.3-extra \
    release/1.2.3/extra \
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

if ! sh ci/check-branch-name.sh codex/ci-001-gitlab-ci-bootstrap \
    github-pull-request >/dev/null; then
    echo 'FAIL: une branche de travail GitHub valide a été refusée.' >&2
    exit 1
fi
if sh ci/check-branch-name.sh release/1.2.3 github-pull-request >/dev/null 2>&1; then
    echo 'FAIL: une branche de promotion GitLab a été acceptée comme source de PR GitHub.' >&2
    exit 1
fi
if sh ci/check-branch-name.sh main github-pull-request >/dev/null 2>&1; then
    echo 'FAIL: main a été acceptée comme branche source de PR GitHub.' >&2
    exit 1
fi
if sh ci/check-branch-name.sh main contexte-inconnu >/dev/null 2>&1; then
    echo 'FAIL: un contexte de validation de branche inconnu a été accepté.' >&2
    exit 1
fi

printf 'BRANCH_NAMING_1A=PASS\n'
