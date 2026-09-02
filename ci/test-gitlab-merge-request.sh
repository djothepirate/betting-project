#!/usr/bin/env sh
set -eu

script=ci/check-gitlab-merge-request.sh

assert_allowed() {
    source_branch=$1
    target_branch=$2
    project_version=$3
    if ! env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_PROJECT_ID=100 \
        CI_MERGE_REQUEST_PROJECT_ID=100 \
        CI_MERGE_REQUEST_SOURCE_PROJECT_ID=100 \
        CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED=true \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME="$source_branch" \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME="$target_branch" \
        sh "$script" "$project_version" >/dev/null; then
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
    if env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_PROJECT_ID="$pipeline_project_id" \
        CI_MERGE_REQUEST_PROJECT_ID="$merge_request_project_id" \
        CI_MERGE_REQUEST_SOURCE_PROJECT_ID="$source_project_id" \
        CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED="$target_protected" \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME="$source_branch" \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME="$target_branch" \
        sh "$script" "$project_version" >/dev/null 2>&1; then
        echo "FAIL: MR GitLab hors politique acceptée : $source_branch -> $target_branch" >&2
        exit 1
    fi
}

if ! env \
    CI_PIPELINE_SOURCE=push \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=codex/ci-001-example \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME=main \
    sh "$script" >/dev/null; then
    echo 'FAIL: un pipeline GitLab hors MR ne doit pas être refusé par la politique des MR.' >&2
    exit 1
fi

assert_allowed main release/0.1.0 0.1.0
assert_allowed main release/1.2.3 1.2.3
assert_allowed main release/1.2.3-rc.1 1.2.3-rc.1

assert_rejected codex/ci-001-example release/1.2.3 1.2.3
assert_rejected human/ci-001-example release/1.2.3 1.2.3
assert_rejected main main 1.2.3
assert_rejected main release/latest latest
assert_rejected main release/01.2.3 01.2.3
assert_rejected main release/1.2.3-rc.0 1.2.3-rc.0
assert_rejected '' release/1.2.3 1.2.3
assert_rejected main '' 1.2.3
assert_rejected main release/1.2.3 1.2.4
assert_rejected main release/1.2.3 1.2.3-SNAPSHOT
assert_rejected main release/1.2.3 1.2.3 200 100 100 true
assert_rejected main release/1.2.3 1.2.3 200 100 200 true
assert_rejected main release/1.2.3 1.2.3 100 100 100 false

if env \
    CI_PIPELINE_SOURCE=merge_request_event \
    CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=main \
    CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/1.2.3 \
    sh "$script" 1.2.3 >/dev/null 2>&1; then
    echo 'FAIL: une MR sans identité de projet a été acceptée.' >&2
    exit 1
fi

fixture=$(mktemp -d "${TMPDIR:-/tmp}/gitlab-mr-policy.XXXXXX")
cleanup() {
    rm -rf "$fixture"
}
trap cleanup EXIT HUP INT TERM
mkdir -p "$fixture/ci"
cp ci/check-branch-name.sh ci/check-gitlab-merge-request.sh "$fixture/ci/"
(
    cd "$fixture"
    git init -q
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit --allow-empty -qm 'release already tagged'
    git tag v1.2.3
    if env \
        CI_PIPELINE_SOURCE=merge_request_event \
        CI_PROJECT_ID=100 \
        CI_MERGE_REQUEST_PROJECT_ID=100 \
        CI_MERGE_REQUEST_SOURCE_PROJECT_ID=100 \
        CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED=true \
        CI_MERGE_REQUEST_SOURCE_BRANCH_NAME=main \
        CI_MERGE_REQUEST_TARGET_BRANCH_NAME=release/1.2.3 \
        sh ci/check-gitlab-merge-request.sh 1.2.3 >/dev/null 2>&1; then
        echo 'FAIL: une branche de release déjà scellée par son tag peut encore avancer.' >&2
        exit 1
    fi
)

printf 'GITLAB_MR_POLICY_TESTS=PASS\n'
