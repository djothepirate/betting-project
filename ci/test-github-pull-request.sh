#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
script="$repository/ci/check-github-pull-request.sh"
bootstrap_base=3fb224e9724698324a56b47fe5d943ecd366f197

assert_allowed() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    if ! sh "$script" "$source_branch" "$target_branch" '' '' \
            "$project_version" >/dev/null; then
        echo "FAIL: Pull Request GitHub valide refusée : $source_branch -> $target_branch ($project_version)" >&2
        exit 1
    fi
}

assert_rejected() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    if sh "$script" "$source_branch" "$target_branch" '' '' \
            "$project_version" >/dev/null 2>&1; then
        echo "FAIL: Pull Request GitHub hors politique acceptée : $source_branch -> $target_branch ($project_version)" >&2
        exit 1
    fi
}

# Work Orders : même train, document existant et version Maven cohérente.
assert_allowed feature/V0.1.0-CODEX-CI-001 feature/V0.1.0 0.1.0-SNAPSHOT
assert_allowed feature/V0.1.0-HUMAN-DEVX-001 feature/V0.1.0 0.1.0
assert_allowed feature/V1.2.3-RC01-CODEX-CI-004 feature/V1.2.3-RC01 \
    1.2.3-rc.1
assert_allowed feature/V1.2.3-RC02-SNAPSHOT-HUMAN-CI-003 \
    feature/V1.2.3-RC02-SNAPSHOT 1.2.3-rc.2-SNAPSHOT

assert_rejected feature/V0.1.0-CODEX-CI-001 feature/V0.1.0 0.2.0-SNAPSHOT
assert_rejected feature/V1.2.3-RC01-CODEX-CI-004 feature/V1.2.3-RC01 \
    1.2.3-rc.1-SNAPSHOT
assert_rejected feature/V1.2.3-RC02-SNAPSHOT-HUMAN-CI-003 \
    feature/V1.2.3-RC02-SNAPSHOT 1.2.3-rc.2

# Finalisation : la version stable doit déjà être finale ; RC et RC-SNAPSHOT sont exacts.
assert_allowed feature/V0.1.0 main 0.1.0
assert_allowed feature/V1.2.3-RC01 main 1.2.3-rc.1
assert_allowed feature/V1.2.3-RC02-SNAPSHOT main 1.2.3-rc.2-SNAPSHOT
assert_rejected feature/V0.1.0 main 0.1.0-SNAPSHOT
assert_rejected feature/V1.2.3-RC01 main 1.2.3-rc.1-SNAPSHOT
assert_rejected feature/V1.2.3-RC02-SNAPSHOT main 1.2.3-rc.2

# Routes, casse et identifiants hors convention.
assert_rejected feature/V0.1.0-CODEX-CI-001 feature/V0.2.0 0.1.0-SNAPSHOT
assert_rejected feature/V1.2.3-RC01-HUMAN-CI-001 feature/V1.2.3 1.2.3-rc.1
assert_rejected feature/V1.2.3-RC01-SNAPSHOT-HUMAN-CI-001 \
    feature/V1.2.3-RC01 1.2.3-rc.1-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-CI-001 main 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-CI-001 release/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0 feature/V0.1.0 0.1.0
assert_rejected feature/V0.1.0 release/V0.1.0 0.1.0
assert_rejected release/V0.1.0 main 0.1.0
assert_rejected main feature/V0.1.0 0.1.0
assert_rejected feature/v0.1.0-CODEX-CI-001 feature/v0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-codex-CI-001 feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-rc.1-CODEX-CI-001 feature/V0.1.0-rc.1 \
    0.1.0-rc.1
assert_rejected feature/V0.1.0-CODEX-CI-000 feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-CI-01 feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-CI-1000 feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-CI-ABC feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-CI-005-EXTRA feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-WO-SS-20260905-055 feature/V0.1.0 \
    0.1.0-SNAPSHOT
assert_rejected feature/V0.1.0-CODEX-FOO-123 feature/V0.1.0 0.1.0-SNAPSHOT
assert_rejected codex/ci-005-example main 0.1.0

# Le bootstrap s'appuie sur un vrai graphe contenant le SHA historique exact.
fixture=$(mktemp -d "${TMPDIR:-/tmp}/github-pr-policy.XXXXXX")
cleanup() {
    rm -rf "$fixture"
}
trap cleanup EXIT HUP INT TERM

git -C "$fixture" init -q
git -C "$fixture" config user.name ci-fixture
git -C "$fixture" config user.email ci-fixture.invalid@example.test
git -C "$fixture" fetch -q "$repository" "$bootstrap_base"
git -C "$fixture" checkout -q --detach "$bootstrap_base"
mkdir -p "$fixture/ci"
cp "$repository/ci/check-branch-name.sh" \
    "$repository/ci/check-github-pull-request.sh" "$fixture/ci/"
git -C "$fixture" add ci
git -C "$fixture" commit -qm 'valid bootstrap head'
valid_head=$(git -C "$fixture" rev-parse HEAD)
base_parent=$(git -C "$fixture" rev-parse "${bootstrap_base}^")
valid_tree=$(git -C "$fixture" rev-parse "${valid_head}^{tree}")
divergent_head=$(git -C "$fixture" commit-tree "$valid_tree" \
    -p "$base_parent" -m 'divergent bootstrap head')

(
    cd "$fixture"
    if ! sh ci/check-github-pull-request.sh \
        codex/ci-004-version-branch-workflow main \
        "$bootstrap_base" "$valid_head" 0.1.0-SNAPSHOT >/dev/null; then
        echo 'FAIL: le bootstrap exact fondé sur un vrai graphe Git a été refusé.' >&2
        exit 1
    fi

    assert_bootstrap_rejected() {
        expected=$1
        base=$2
        head=$3
        project_version=${4:-0.1.0-SNAPSHOT}
        output=$(mktemp)
        if sh ci/check-github-pull-request.sh \
            codex/ci-004-version-branch-workflow main \
            "$base" "$head" "$project_version" >"$output" 2>&1; then
            echo "FAIL: bootstrap hors politique accepté : $base..$head ($project_version)" >&2
            rm -f "$output"
            exit 1
        fi
        if ! grep -Fq "$expected" "$output"; then
            echo "FAIL: motif de refus du bootstrap absent : $expected" >&2
            cat "$output" >&2
            rm -f "$output"
            exit 1
        fi
        rm -f "$output"
    }

    assert_bootstrap_rejected 'exige codex/ci-004-version-branch-workflow' \
        0000000000000000000000000000000000000000 "$valid_head"
    assert_bootstrap_rejected "n'est pas ancêtre" \
        "$bootstrap_base" "$divergent_head"
    assert_bootstrap_rejected 'SHA de tête du bootstrap CI-004 est absent ou invalide' \
        "$bootstrap_base" 0000000000000000000000000000000000000000
    assert_bootstrap_rejected 'exige la version Maven 0.1.0-SNAPSHOT' \
        "$bootstrap_base" "$valid_head" 0.1.0

    if sh ci/check-github-pull-request.sh \
        codex/ci-004-version-branch-workflow feature/V0.1.0 \
        "$bootstrap_base" "$valid_head" 0.1.0-SNAPSHOT >/dev/null 2>&1; then
        echo 'FAIL: le bootstrap vers une cible autre que main a été accepté.' >&2
        exit 1
    fi
)

printf 'GITHUB_PR_POLICY_TESTS=PASS\n'
