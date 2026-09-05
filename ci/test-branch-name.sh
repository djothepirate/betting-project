#!/usr/bin/env sh
set -eu

for branch_name in \
    main \
    feature/V0.1.0 \
    feature/V1.2.3 \
    feature/V1.2.3-RC01 \
    feature/V1.2.3-RC99-SNAPSHOT \
    feature/V0.1.0-CODEX-CI-005 \
    feature/V0.1.0-HUMAN-MVP-001 \
    feature/V1.2.3-RC02-SNAPSHOT-CODEX-DEVX-007 \
    release/V0.1.0 \
    release/V1.2.3-RC01 \
    release/V1.2.3-RC99-SNAPSHOT; do
    if ! sh ci/check-branch-name.sh "$branch_name" >/dev/null; then
        echo "FAIL: branche versionnée valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    '' \
    develop \
    feature/v0.1.0 \
    feature/V01.2.3 \
    feature/V1.02.3 \
    feature/V1.2.03 \
    feature/V1.2 \
    feature/V1.2.3-SNAPSHOT \
    feature/V1.2.3-rc.1 \
    feature/V1.2.3-RC00 \
    feature/V1.2.3-RC1 \
    feature/V1.2.3-RC001 \
    feature/V1.2.3-RC100 \
    feature/V1.2.3-RC01-snapshot \
    feature/V1.2.3-CODEX \
    feature/V1.2.3-codex-CI-005 \
    feature/V1.2.3-BOT-CI-005 \
    feature/V1.2.3-CODEX-ci-005 \
    feature/V1.2.3-CODEX-CI-000 \
    feature/V1.2.3-CODEX-CI-01 \
    feature/V1.2.3-CODEX-CI-1000 \
    feature/V1.2.3-CODEX-CI-ABC \
    feature/V1.2.3-CODEX-CI-005-EXTRA \
    feature/V1.2.3-CODEX-WO-SS-20260905-055 \
    release/v1.2.3 \
    release/V01.2.3 \
    release/V1.2.3-SNAPSHOT \
    release/V1.2.3-rc.1 \
    release/V1.2.3-RC00 \
    release/V1.2.3-RC100 \
    release/V1.2.3-RC01-SNAPSHOT-extra \
    release/V1.2.3/extra \
    codex/ci-004-version-branch-workflow \
    codex/ci-005-example \
    human/ci-005-example \
    hotfix/inc-123-database; do
    if sh ci/check-branch-name.sh "$branch_name" >/dev/null 2>&1; then
        echo "FAIL: branche hors convention versionnée acceptée : $branch_name" >&2
        exit 1
    fi
done

if ! sh ci/check-branch-name.sh feature/V1.2.3-RC01 feature-integration >/dev/null; then
    echo 'FAIL: une branche feature d’intégration valide a été refusée.' >&2
    exit 1
fi
if sh ci/check-branch-name.sh feature/V1.2.3-RC01-CODEX-CI-005 \
    feature-integration >/dev/null 2>&1; then
    echo 'FAIL: une branche de Work Order a été confondue avec une branche d’intégration.' >&2
    exit 1
fi
if ! sh ci/check-branch-name.sh feature/V1.2.3-RC01-SNAPSHOT-HUMAN-CI-005 \
    feature-work-order >/dev/null; then
    echo 'FAIL: une branche HUMAN de Work Order valide a été refusée.' >&2
    exit 1
fi
if ! sh ci/check-branch-name.sh release/V1.2.3-RC01-SNAPSHOT \
    gitlab-release >/dev/null; then
    echo 'FAIL: une branche GitLab RC snapshot valide a été refusée.' >&2
    exit 1
fi

for branch_name in \
    main \
    feature/V0.1.0 \
    feature/V0.1.0-RC01 \
    feature/V0.1.0-RC01-SNAPSHOT \
    feature/V1.2.3-RC01 \
    feature/V1.2.3-RC99-SNAPSHOT \
    feature/V0.1.0-CODEX-CI-005 \
    feature/V1.2.3-RC01-SNAPSHOT-HUMAN-CI-005; do
    if ! sh ci/check-branch-name.sh "$branch_name" github-branch >/dev/null; then
        echo "FAIL: branche GitHub valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    release/V0.1.0 \
    release/V1.2.3-RC01 \
    release/V1.2.3-RC01-SNAPSHOT \
    feature/V1.2.3-RC00 \
    codex/ci-004-version-branch-workflow \
    human/legacy-change; do
    if sh ci/check-branch-name.sh "$branch_name" github-branch >/dev/null 2>&1; then
        echo "FAIL: branche interdite acceptée par GitHub : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    main \
    feature/V0.1.0 \
    feature/V0.1.0-RC01 \
    feature/V0.1.0-RC01-SNAPSHOT \
    feature/V1.2.3-RC01 \
    feature/V1.2.3-RC99-SNAPSHOT \
    release/V0.1.0 \
    release/V1.2.3-RC01 \
    release/V1.2.3-RC99-SNAPSHOT; do
    if ! sh ci/check-branch-name.sh "$branch_name" gitlab-branch >/dev/null; then
        echo "FAIL: branche de pipeline GitLab valide refusée : $branch_name" >&2
        exit 1
    fi
done

for branch_name in \
    feature/V0.1.0-CODEX-CI-005 \
    feature/V1.2.3-RC01-SNAPSHOT-HUMAN-CI-005 \
    codex/ci-004-version-branch-workflow \
    codex/ci-003-dependency-cache \
    human/legacy-change \
    hotfix/inc-123-database; do
    if sh ci/check-branch-name.sh "$branch_name" gitlab-branch >/dev/null 2>&1; then
        echo "FAIL: branche interdite acceptée pour une pipeline GitLab : $branch_name" >&2
        exit 1
    fi
done

if sh ci/check-branch-name.sh main contexte-inconnu >/dev/null 2>&1; then
    echo 'FAIL: un contexte de validation inconnu a été accepté.' >&2
    exit 1
fi

printf 'BRANCH_NAMING_VERSIONED=PASS\n'
