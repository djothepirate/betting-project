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

if ! grep -Fq 'if [ "${CI_PIPELINE_SOURCE:-}" = merge_request_event ]; then' \
        .gitlab-ci.yml ||
   ! grep -Fq '"$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME" feature-integration' \
        .gitlab-ci.yml ||
   ! grep -Fq '"$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" gitlab-release' \
        .gitlab-ci.yml ||
   ! grep -Fq 'elif [ -n "${CI_COMMIT_BRANCH:-}" ]; then' .gitlab-ci.yml ||
   ! grep -Fq 'sh ci/check-branch-name.sh "$CI_COMMIT_BRANCH" gitlab-branch' \
        .gitlab-ci.yml ||
   ! grep -Fq '"${CI_COMMIT_REF_PROTECTED:-}" != true' .gitlab-ci.yml ||
   ! grep -Fq "'^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-rc\\.[1-9][0-9]*)?$'" \
        .gitlab-ci.yml ||
   ! grep -Fq 'une Merge Request GitLab exige CI_COMMIT_BRANCH/CI_COMMIT_TAG vides' \
        .gitlab-ci.yml ||
   ! grep -Fq 'les variables de Merge Request GitLab doivent être vides hors merge_request_event' \
        .gitlab-ci.yml ||
   ! grep -Fq 'FAIL: source ou ref GitLab indéterminée' .gitlab-ci.yml; then
    echo 'FAIL: la pipeline GitLab doit router explicitement MR, tag, branche et absence de ref.' >&2
    exit 1
fi

gitlab_ref_policy() {
    pipeline_source=$1
    commit_branch=$2
    commit_tag=$3
    merge_request_source=$4
    merge_request_target=$5
    commit_ref_protected=${6:-}

    if [ "$pipeline_source" = merge_request_event ]; then
        if [ -n "$commit_branch" ] || [ -n "$commit_tag" ] ||
           [ -z "$merge_request_source" ] || [ -z "$merge_request_target" ]; then
            return 1
        fi
        sh ci/check-branch-name.sh "$merge_request_source" \
            feature-integration >/dev/null &&
        sh ci/check-branch-name.sh "$merge_request_target" \
            gitlab-release >/dev/null
    else
        if [ -n "$merge_request_source" ] || [ -n "$merge_request_target" ]; then
            return 1
        fi
        if [ -n "$commit_branch" ] && [ -n "$commit_tag" ]; then
            return 1
        elif [ -n "$commit_branch" ]; then
            sh ci/check-branch-name.sh "$commit_branch" gitlab-branch >/dev/null
        elif [ -n "$commit_tag" ]; then
            [ "$commit_ref_protected" = true ] &&
            printf '%s' "$commit_tag" | grep -Eq \
                '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-rc\.[1-9][0-9]*)?$'
        else
            return 1
        fi
    fi
}

assert_ref_allowed() {
    scenario=$1
    shift
    if ! gitlab_ref_policy "$@"; then
        echo "FAIL: contexte GitLab valide refusé : $scenario" >&2
        exit 1
    fi
}

assert_ref_rejected() {
    scenario=$1
    shift
    if gitlab_ref_policy "$@" >/dev/null 2>&1; then
        echo "FAIL: contexte GitLab interdit accepté : $scenario" >&2
        exit 1
    fi
}

for pipeline_source in push web schedule api trigger pipeline; do
    for branch_name in \
        main \
        feature/V0.1.0 \
        feature/V1.2.3-RC01-SNAPSHOT \
        release/V0.1.0 \
        release/V1.2.3-RC99; do
        assert_ref_allowed "$pipeline_source sur $branch_name" \
            "$pipeline_source" "$branch_name" '' '' ''
    done

    for branch_name in \
        feature/V0.1.0-CODEX-CI-005 \
        codex/ci-004-version-branch-workflow \
        codex/ci-003-dependency-cache \
        human/legacy-change \
        hotfix/inc-123-database; do
        assert_ref_rejected "$pipeline_source sur $branch_name" \
            "$pipeline_source" "$branch_name" '' '' ''
    done
done

assert_ref_allowed 'Merge Request feature vers release' \
    merge_request_event '' '' feature/V1.2.3-RC01 release/V1.2.3-RC01
assert_ref_rejected 'Merge Request depuis une branche de Work Order' \
    merge_request_event '' '' \
    feature/V1.2.3-RC01-CODEX-CI-005 release/V1.2.3-RC01
assert_ref_rejected 'Merge Request vers main' \
    merge_request_event '' '' feature/V1.2.3-RC01 main
assert_ref_rejected 'Merge Request sans branche source' \
    merge_request_event '' '' '' release/V1.2.3-RC01
assert_ref_rejected 'Merge Request sans branche cible' \
    merge_request_event '' '' feature/V1.2.3-RC01 ''
assert_ref_rejected 'Merge Request portant aussi CI_COMMIT_BRANCH' \
    merge_request_event feature/V1.2.3-RC01 '' \
    feature/V1.2.3-RC01 release/V1.2.3-RC01
assert_ref_rejected 'Merge Request portant aussi CI_COMMIT_TAG' \
    merge_request_event '' v1.2.3 \
    feature/V1.2.3 release/V1.2.3 true
assert_ref_allowed 'pipeline de tag stable protégé' push '' v1.2.3 '' '' true
assert_ref_allowed 'pipeline de tag RC protégé' push '' v1.2.3-rc.100 '' '' true
assert_ref_rejected 'pipeline de tag stable non protégé' push '' v1.2.3 '' '' false
assert_ref_rejected 'pipeline de tag sans statut protégé' push '' v1.2.3 '' '' ''
for invalid_tag in \
    1.2.3 \
    V1.2.3 \
    v01.2.3 \
    v1.02.3 \
    v1.2.03 \
    v1.2 \
    v1.2.3-RC01 \
    v1.2.3-rc.0 \
    v1.2.3-rc.01 \
    v1.2.3-SNAPSHOT; do
    assert_ref_rejected "pipeline de tag mal formé : $invalid_tag" \
        push '' "$invalid_tag" '' '' true
done
assert_ref_rejected 'pipeline portant simultanément branche et tag' \
    push main v1.2.3 '' '' true
assert_ref_rejected 'pipeline de branche portant des variables de Merge Request' \
    web main '' feature/V1.2.3 release/V1.2.3
assert_ref_rejected 'pipeline de tag portant des variables de Merge Request' \
    push '' v1.2.3 feature/V1.2.3 release/V1.2.3 true

for pipeline_source in '' push web schedule api trigger pipeline; do
    assert_ref_rejected "source sans branche, tag ni Merge Request : ${pipeline_source:-vide}" \
        "$pipeline_source" '' '' '' ''
done

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
    merge_request_event feature/V1.2.3 main 'group/project!1' ''
assert_decision RUN 'push de branche feature sans MR' \
    push feature/V1.2.3 main '' ''
assert_decision SKIP 'push de branche feature avec MR ouverte' \
    push feature/V1.2.3 main 'group/project!1' ''
assert_decision RUN 'push de main sans MR' \
    push main main '' ''
assert_decision RUN 'pipeline de tag' \
    push '' main '' v1.2.3
assert_decision RUN 'lancement web malgré une MR ouverte' \
    web feature/V1.2.3 main 'group/project!1' ''
assert_decision RUN 'déclenchement externe malgré une MR ouverte' \
    trigger feature/V1.2.3 main 'group/project!1' ''
assert_decision RUN 'pipeline planifiée sur une feature' \
    schedule feature/V1.2.3 main '' ''
assert_decision RUN 'pipeline API sur une release' \
    api release/V1.2.3 main '' ''
assert_decision RUN 'pipeline enfant sur main' \
    pipeline main main '' ''
assert_decision RUN 'push après fermeture de la MR' \
    push feature/V1.2.3 main '' ''

printf 'GITLAB_WORKFLOW_DEDUPLICATION=PASS\n'
