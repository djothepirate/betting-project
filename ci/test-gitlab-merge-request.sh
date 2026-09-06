#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
fixture=$(mktemp -d "${TMPDIR:-/tmp}/gitlab-mr-policy.XXXXXX")
shallow_fixture="${fixture}-shallow"
cleanup() {
    rm -rf "$fixture" "$shallow_fixture"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$fixture/ci"
cp "$repository/ci/check-branch-name.sh" \
    "$repository/ci/check-gitlab-merge-request.sh" "$fixture/ci/"
git -C "$fixture" init -q
git -C "$fixture" config user.name ci-fixture
git -C "$fixture" config user.email ci-fixture.invalid@example.test
printf 'fixture\n' >"$fixture/policy-fixture.txt"
git -C "$fixture" add .
git -C "$fixture" commit -qm 'promotion root'
root_commit=$(git -C "$fixture" rev-parse HEAD)
git -C "$fixture" commit --allow-empty -qm 'release base'
target_commit=$(git -C "$fixture" rev-parse HEAD)
git -C "$fixture" commit --allow-empty -qm 'github merge commit'
source_commit=$(git -C "$fixture" rev-parse HEAD)
source_tree=$(git -C "$fixture" rev-parse "${source_commit}^{tree}")
divergent_target=$(git -C "$fixture" commit-tree "$source_tree" \
    -p "$root_commit" -m 'divergent release target')

cd "$fixture"
script=ci/check-gitlab-merge-request.sh

prepare_refs() {
    source_branch=$1
    target_branch=$2
    git update-ref refs/remotes/origin/main "$source_commit"
    if git check-ref-format "refs/remotes/origin/$source_branch" >/dev/null 2>&1; then
        git update-ref "refs/remotes/origin/$source_branch" "$source_commit"
    fi
    if git check-ref-format "refs/remotes/origin/$target_branch" >/dev/null 2>&1; then
        git update-ref "refs/remotes/origin/$target_branch" "$target_commit"
    fi
}

run_policy() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    source_project_id=${4:-100}
    merge_request_project_id=${5:-100}
    pipeline_project_id=${6:-100}
    target_protected=${7:-true}
    pipeline_sha=${8:-$source_commit}
    env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_COMMIT_SHA="$pipeline_sha" \
        CI_PROJECT_ID="$pipeline_project_id" \
        CI_MERGE_REQUEST_PROJECT_ID="$merge_request_project_id" \
        CI_MERGE_REQUEST_SOURCE_PROJECT_ID="$source_project_id" \
        CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED="$target_protected" \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME="$source_branch" \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME="$target_branch" \
        sh "$script" "$project_version"
}

assert_allowed() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    prepare_refs "$source_branch" "$target_branch"
    if ! run_policy "$source_branch" "$target_branch" "$project_version" \
            >/dev/null; then
        echo "FAIL: MR GitLab valide refusée : $source_branch -> $target_branch" >&2
        exit 1
    fi
}

assert_rejected() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    source_project_id=${4:-100}
    merge_request_project_id=${5:-100}
    pipeline_project_id=${6:-100}
    target_protected=${7:-true}
    prepare_refs "$source_branch" "$target_branch"
    if run_policy "$source_branch" "$target_branch" "$project_version" \
            "$source_project_id" "$merge_request_project_id" \
            "$pipeline_project_id" "$target_protected" >/dev/null 2>&1; then
        echo "FAIL: MR GitLab hors politique acceptée : $source_branch -> $target_branch" >&2
        exit 1
    fi
}

assert_graph_rejected() {
    expected=$1
    source_branch=$2
    target_branch=$3
    project_version=$4
    pipeline_sha=${5:-$source_commit}
    output=$(mktemp)
    if run_policy "$source_branch" "$target_branch" "$project_version" \
            100 100 100 true "$pipeline_sha" >"$output" 2>&1; then
        echo "FAIL: topologie GitLab invalide acceptée : $source_branch -> $target_branch" >&2
        rm -f "$output"
        exit 1
    fi
    if ! grep -Fq "$expected" "$output"; then
        echo "FAIL: motif de refus topologique absent : $expected" >&2
        cat "$output" >&2
        rm -f "$output"
        exit 1
    fi
    rm -f "$output"
}

if ! env CI_PIPELINE_SOURCE=push sh "$script" >/dev/null; then
    echo 'FAIL: un pipeline GitLab hors MR ne doit pas être refusé par la politique des MR.' >&2
    exit 1
fi

assert_allowed feature/V0.1.0 release/V0.1.0 0.1.0
assert_allowed feature/V1.2.3 release/V1.2.3 1.2.3
assert_allowed feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-rc.1
assert_allowed feature/V1.2.3-RC99-SNAPSHOT \
    release/V1.2.3-RC99-SNAPSHOT 1.2.3-rc.99-SNAPSHOT

assert_rejected main release/V1.2.3 1.2.3
assert_rejected feature/V1.2.3-CODEX-CI-005 release/V1.2.3 1.2.3
assert_rejected feature/V1.2.3-HUMAN-CI-005 release/V1.2.3 1.2.3
assert_rejected feature/V1.2.3 main 1.2.3
assert_rejected feature/V1.2.3 release/V1.2.4 1.2.4
assert_rejected feature/V1.2.3-RC01 release/V1.2.3 1.2.3
assert_rejected feature/V1.2.3-RC01-SNAPSHOT release/V1.2.3-RC01 1.2.3-rc.1
assert_rejected feature/v1.2.3 release/V1.2.3 1.2.3
assert_rejected feature/V1.2.3 release/v1.2.3 1.2.3
assert_rejected feature/V1.2.3 release/V01.2.3 01.2.3
assert_rejected feature/V1.2.3-rc.1 release/V1.2.3-rc.1 1.2.3-rc.1
assert_rejected feature/V1.2.3-RC00 release/V1.2.3-RC00 1.2.3-RC00
assert_rejected feature/V1.2.3-RC100 release/V1.2.3-RC100 1.2.3-rc.100
assert_rejected '' release/V1.2.3 1.2.3
assert_rejected feature/V1.2.3 '' 1.2.3
assert_rejected feature/V1.2.3 release/V1.2.3 1.2.4
assert_rejected feature/V1.2.3 release/V1.2.3 1.2.3-SNAPSHOT
assert_rejected feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-RC01
assert_rejected feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-rc.01
assert_rejected feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-rc.1-SNAPSHOT
assert_rejected feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-rc.2
assert_rejected feature/V1.2.3-RC01-SNAPSHOT \
    release/V1.2.3-RC01-SNAPSHOT 1.2.3-rc.1
assert_rejected feature/V1.2.3 release/V1.2.3 1.2.3 200 100 100 true
assert_rejected feature/V1.2.3 release/V1.2.3 1.2.3 200 100 200 true
assert_rejected feature/V1.2.3 release/V1.2.3 1.2.3 100 100 100 false

if env \
    CI_PIPELINE_SOURCE=merge_request_event \
    CI_COMMIT_SHA="$source_commit" \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
    sh "$script" 1.2.3 >/dev/null 2>&1; then
    echo 'FAIL: une MR sans identité de projet a été acceptée.' >&2
    exit 1
fi

# Source, main, SHA de pipeline et ascendance de la cible sont des invariants exécutables.
prepare_refs feature/V1.2.3 release/V1.2.3
git update-ref refs/remotes/origin/main "$target_commit"
assert_graph_rejected 'doivent désigner le même merge commit GitHub' \
    feature/V1.2.3 release/V1.2.3 1.2.3

prepare_refs feature/V1.2.3 release/V1.2.3
git update-ref refs/remotes/origin/feature/V1.2.3 "$target_commit"
assert_graph_rejected 'diffère du sommet canonique' \
    feature/V1.2.3 release/V1.2.3 1.2.3

prepare_refs feature/V1.2.3 release/V1.2.3
assert_graph_rejected 'commit extrait' \
    feature/V1.2.3 release/V1.2.3 1.2.3 "$target_commit"

prepare_refs feature/V1.2.3 release/V1.2.3
git update-ref refs/remotes/origin/release/V1.2.3 "$divergent_target"
assert_graph_rejected "n'est pas ancêtre" \
    feature/V1.2.3 release/V1.2.3 1.2.3

prepare_refs feature/V1.2.3 release/V1.2.3
git update-ref -d refs/remotes/origin/release/V1.2.3
assert_graph_rejected 'référence release canonique introuvable' \
    feature/V1.2.3 release/V1.2.3 1.2.3

prepare_refs feature/V1.2.3-RC01 release/V1.2.3-RC01
git tag v1.2.3-rc.1
assert_graph_rejected 'est scellée par le tag v1.2.3-rc.1' \
    feature/V1.2.3-RC01 release/V1.2.3-RC01 1.2.3-rc.1

git clone -q --depth 1 "file://$fixture" "$shallow_fixture"
(
    cd "$shallow_fixture"
    output=$(mktemp)
    if env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_COMMIT_SHA="$source_commit" \
        CI_PROJECT_ID=100 \
        CI_MERGE_REQUEST_PROJECT_ID=100 \
        CI_MERGE_REQUEST_SOURCE_PROJECT_ID=100 \
        CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED=true \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=feature/V1.2.3 \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/V1.2.3 \
        sh ci/check-gitlab-merge-request.sh 1.2.3 >"$output" 2>&1; then
        echo 'FAIL: une MR issue d’un historique shallow a été acceptée.' >&2
        rm -f "$output"
        exit 1
    fi
    if ! grep -Fq 'historique Git complet est requis' "$output"; then
        echo 'FAIL: le refus de l’historique shallow est ambigu.' >&2
        cat "$output" >&2
        rm -f "$output"
        exit 1
    fi
    rm -f "$output"
)

printf 'GITLAB_MR_POLICY_TESTS=PASS\n'
