#!/usr/bin/env sh
set -eu

pipeline_source=${CI_PIPELINE_SOURCE:-}
if [ "$pipeline_source" != merge_request_event ]; then
    printf 'GITLAB_MR_POLICY=SKIP:%s\n' "${pipeline_source:-unset}"
    exit 0
fi

source_branch=${CI_MERGE_REQUEST_SOURCE_BRANCH_NAME:-}
target_branch=${CI_MERGE_REQUEST_TARGET_BRANCH_NAME:-}
project_id=${CI_PROJECT_ID:-}
source_project_id=${CI_MERGE_REQUEST_SOURCE_PROJECT_ID:-}
merge_request_project_id=${CI_MERGE_REQUEST_PROJECT_ID:-}
target_branch_protected=${CI_MERGE_REQUEST_TARGET_BRANCH_PROTECTED:-}

if [ -z "$project_id" ] || [ -z "$source_project_id" ] ||
   [ -z "$merge_request_project_id" ] ||
   [ "$source_project_id" != "$merge_request_project_id" ] ||
   [ "$project_id" != "$merge_request_project_id" ]; then
    echo "FAIL: une MR GitLab de promotion doit rester dans le projet cible, IDs reçus : source=${source_project_id:-<vide>}, cible=${merge_request_project_id:-<vide>}, pipeline=${project_id:-<vide>}." >&2
    exit 1
fi
if [ "$target_branch_protected" != true ]; then
    echo "FAIL: la cible GitLab ${target_branch:-<vide>} doit être protégée." >&2
    exit 1
fi
case "$target_branch" in
    release/*) ;;
    *)
        echo "FAIL: une MR GitLab doit cibler release/<SemVer>, cible reçue : ${target_branch:-<vide>}." >&2
        exit 1
        ;;
esac

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if [ "$source_branch" != main ] ||
   ! sh "$script_dir/check-branch-name.sh" "$target_branch" >/dev/null 2>&1; then
    echo "FAIL: seule une MR GitLab main -> release/<SemVer> est autorisée, reçue : ${source_branch:-<vide>} -> ${target_branch:-<vide>}." >&2
    exit 1
fi

release_version=${target_branch#release/}
project_version=${1:-}
if [ "$project_version" != "$release_version" ]; then
    echo "FAIL: la cible $target_branch exige la version Maven $release_version, reçue : ${project_version:-<vide>}." >&2
    exit 1
fi

release_tag="v$release_version"
repository=$(CDPATH= cd -- "$script_dir/.." && pwd)
if git -C "$repository" show-ref --verify --quiet "refs/tags/$release_tag"; then
    echo "FAIL: la branche $target_branch est scellée par le tag $release_tag et ne peut plus avancer." >&2
    exit 1
fi

printf 'GITLAB_MR_POLICY=PASS:%s->%s\n' "$source_branch" "$target_branch"
