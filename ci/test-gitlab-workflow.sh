#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

expected_workflow_rules=$(cat <<'EOF'
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS'
      when: never
    - if: '$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH'
    - if: '$CI_COMMIT_BRANCH'
    - if: '$CI_COMMIT_TAG'
    - if: '$CI_PIPELINE_SOURCE == "web"'
    - when: never
EOF
)
actual_workflow_rules=$(awk '
    /^workflow:/ { in_workflow = 1; next }
    in_workflow && /^  rules:$/ { in_rules = 1; next }
    in_rules && /^stages:/ { exit }
    in_rules { print }
' .gitlab-ci.yml)

if [ "$actual_workflow_rules" != "$expected_workflow_rules" ]; then
    echo 'FAIL: le bloc workflow.rules GitLab diffère de la séquence qualifiée.' >&2
    exit 1
fi

workflow_decision() {
    pipeline_source=$1
    commit_branch=$2
    default_branch=$3
    open_merge_requests=$4
    commit_tag=$5

    if [ "$pipeline_source" = merge_request_event ]; then
        printf 'RUN\n'
    elif [ "$pipeline_source" = push ] \
        && [ -n "$commit_branch" ] \
        && [ -n "$open_merge_requests" ]; then
        printf 'SKIP\n'
    elif [ -n "$commit_branch" ] && [ "$commit_branch" = "$default_branch" ]; then
        printf 'RUN\n'
    elif [ -n "$commit_branch" ]; then
        printf 'RUN\n'
    elif [ -n "$commit_tag" ]; then
        printf 'RUN\n'
    elif [ "$pipeline_source" = web ]; then
        printf 'RUN\n'
    else
        printf 'SKIP\n'
    fi
}

assert_decision() {
    expected=$1
    scenario=$2
    shift 2
    actual=$(workflow_decision "$@")
    if [ "$actual" != "$expected" ]; then
        printf 'FAIL: %s : attendu=%s obtenu=%s\n' "$scenario" "$expected" "$actual" >&2
        exit 1
    fi
}

assert_decision RUN 'pipeline de MR prioritaire' \
    merge_request_event main main 'group/project!1' ''
assert_decision RUN 'push de branche sans MR' \
    push codex/ci-001-test main '' ''
assert_decision SKIP 'push de branche avec MR ouverte' \
    push codex/ci-001-test main 'group/project!1' ''
assert_decision RUN 'push de main sans MR de promotion' \
    push main main '' ''
assert_decision SKIP 'push de main avec MR de promotion ouverte' \
    push main main 'group/project!1' ''
assert_decision RUN 'pipeline de tag' \
    push '' main '' v1.2.3
assert_decision RUN 'lancement web malgré une MR ouverte' \
    web main main 'group/project!1' ''
assert_decision RUN 'déclenchement externe malgré une MR ouverte' \
    trigger main main 'group/project!1' ''
assert_decision RUN 'push après fermeture de la MR' \
    push codex/ci-001-test main '' ''

printf 'GITLAB_WORKFLOW_DEDUPLICATION=PASS\n'
