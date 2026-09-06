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

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
if ! sh "$script_dir/check-branch-name.sh" "$source_branch" \
        feature-integration >/dev/null 2>&1 ||
   ! sh "$script_dir/check-branch-name.sh" "$target_branch" \
        gitlab-release >/dev/null 2>&1; then
    echo "FAIL: seule une MR GitLab feature/<TRAIN> -> release/<TRAIN> est autorisée, reçue : ${source_branch:-<vide>} -> ${target_branch:-<vide>}." >&2
    exit 1
fi

source_train=${source_branch#feature/V}
release_train=${target_branch#release/V}
if [ "$source_train" != "$release_train" ]; then
    echo "FAIL: les trains source et cible de la MR GitLab diffèrent : ${source_train} != ${release_train}." >&2
    exit 1
fi

case "$release_train" in
    *-RC*-SNAPSHOT)
        release_candidate=${release_train%-SNAPSHOT}
        release_core=${release_candidate%-RC*}
        release_rc=${release_candidate##*-RC}
        release_rc=${release_rc#0}
        expected_project_version="${release_core}-rc.${release_rc}-SNAPSHOT"
        ;;
    *-RC*)
        release_core=${release_train%-RC*}
        release_rc=${release_train##*-RC}
        release_rc=${release_rc#0}
        expected_project_version="${release_core}-rc.${release_rc}"
        ;;
    *) expected_project_version=$release_train ;;
esac

project_version=${1:-}
if [ "$project_version" != "$expected_project_version" ]; then
    echo "FAIL: la cible $target_branch exige la version Maven $expected_project_version, reçue : ${project_version:-<vide>}." >&2
    exit 1
fi

repository=$(CDPATH= cd -- "$script_dir/.." && pwd)
if ! shallow=$(git -C "$repository" rev-parse --is-shallow-repository 2>/dev/null); then
    echo 'FAIL: impossible de déterminer si le dépôt GitLab est shallow.' >&2
    exit 1
fi
if [ "$shallow" != false ]; then
    echo 'FAIL: un historique Git complet est requis pour qualifier la topologie de promotion.' >&2
    exit 1
fi

pipeline_sha=${CI_COMMIT_SHA:-}
if ! pipeline_commit=$(git -C "$repository" rev-parse --verify \
        "${pipeline_sha}^{commit}" 2>/dev/null); then
    echo "FAIL: SHA de pipeline MR absent ou invalide : ${pipeline_sha:-<vide>}." >&2
    exit 1
fi
checkout_commit=$(git -C "$repository" rev-parse --verify 'HEAD^{commit}')
if [ "$checkout_commit" != "$pipeline_commit" ]; then
    echo "FAIL: le commit extrait $checkout_commit diffère du SHA de pipeline MR $pipeline_commit." >&2
    exit 1
fi

canonical_source_ref="refs/remotes/origin/$source_branch"
canonical_main_ref=refs/remotes/origin/main
canonical_target_ref="refs/remotes/origin/$target_branch"
if ! canonical_source_commit=$(git -C "$repository" rev-parse --verify \
        "${canonical_source_ref}^{commit}" 2>/dev/null); then
    echo "FAIL: référence feature canonique introuvable : $canonical_source_ref." >&2
    exit 1
fi
if ! canonical_main_commit=$(git -C "$repository" rev-parse --verify \
        "${canonical_main_ref}^{commit}" 2>/dev/null); then
    echo "FAIL: référence main canonique introuvable : $canonical_main_ref." >&2
    exit 1
fi
if ! canonical_target_commit=$(git -C "$repository" rev-parse --verify \
        "${canonical_target_ref}^{commit}" 2>/dev/null); then
    echo "FAIL: référence release canonique introuvable : $canonical_target_ref." >&2
    exit 1
fi
if [ "$pipeline_commit" != "$canonical_source_commit" ]; then
    echo "FAIL: le SHA de pipeline MR $pipeline_commit diffère du sommet canonique de $canonical_source_ref ($canonical_source_commit)." >&2
    exit 1
fi
if [ "$canonical_source_commit" != "$canonical_main_commit" ]; then
    echo "FAIL: $canonical_source_ref ($canonical_source_commit) et $canonical_main_ref ($canonical_main_commit) doivent désigner le même merge commit GitHub." >&2
    exit 1
fi
if git -C "$repository" merge-base --is-ancestor \
        "$canonical_target_commit" "$canonical_source_commit"; then
    :
else
    ancestry_status=$?
    if [ "$ancestry_status" -eq 1 ]; then
        echo "FAIL: la cible $canonical_target_ref ($canonical_target_commit) n'est pas ancêtre de la source $canonical_source_ref ($canonical_source_commit)." >&2
    else
        echo "FAIL: impossible de vérifier l'ascendance de $canonical_target_ref vers $canonical_source_ref." >&2
    fi
    exit 1
fi

case "$expected_project_version" in
    *-SNAPSHOT) ;;
    *)
        release_tag="v$expected_project_version"
        if git -C "$repository" show-ref --verify --quiet "refs/tags/$release_tag"; then
            echo "FAIL: la branche $target_branch est scellée par le tag $release_tag et ne peut plus avancer." >&2
            exit 1
        fi
        ;;
esac

printf 'GITLAB_MR_POLICY=PASS:%s->%s\n' "$source_branch" "$target_branch"
