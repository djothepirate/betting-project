#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

head_commit=$(git rev-parse 'HEAD^{commit}')
parent_commit=$(git rev-parse 'HEAD^{commit}^')
canonical_main_ref=refs/remotes/origin/main
original_main=$(git rev-parse --verify "${canonical_main_ref}^{commit}" 2>/dev/null || true)
test_tag="v999999.999999.$$-rc.1"
test_tag_ref="refs/tags/$test_tag"
canonical_release_ref="refs/remotes/origin/release/${test_tag#v}"
original_release=$(git rev-parse --verify "${canonical_release_ref}^{commit}" 2>/dev/null || true)

if git show-ref --verify --quiet "$test_tag_ref"; then
    echo "FAIL: le tag de test existe déjà : $test_tag" >&2
    exit 1
fi

cleanup() {
    git update-ref -d "$test_tag_ref" >/dev/null 2>&1 || true
    if [ -n "$original_main" ]; then
        git update-ref "$canonical_main_ref" "$original_main" >/dev/null 2>&1 || true
    else
        git update-ref -d "$canonical_main_ref" >/dev/null 2>&1 || true
    fi
    if [ -n "$original_release" ]; then
        git update-ref "$canonical_release_ref" "$original_release" >/dev/null 2>&1 || true
    else
        git update-ref -d "$canonical_release_ref" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT HUP INT TERM

assert_rejected() {
    expected=$1
    shift
    output_file=$(mktemp "${TMPDIR:-/tmp}/package-guard.XXXXXX")
    if "$@" >"$output_file" 2>&1; then
        echo "FAIL: le scénario devait être refusé : $expected" >&2
        rm -f "$output_file"
        exit 1
    fi
    if ! grep -Fq "$expected" "$output_file"; then
        echo "FAIL: motif de refus absent : $expected" >&2
        cat "$output_file" >&2
        rm -f "$output_file"
        exit 1
    fi
    rm -f "$output_file"
}

assert_rejected 'ne correspond pas au commit extrait' \
    env CI_COMMIT_SHA="$parent_commit" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

assert_rejected "le tag $test_tag est absent du checkout" \
    env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

git update-ref "$test_tag_ref" "$head_commit"
git update-ref -d "$canonical_main_ref"
assert_rejected 'référence canonique main introuvable' \
    env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

git update-ref "$canonical_main_ref" "$parent_commit"
assert_rejected "n'est pas atteignable depuis" \
    env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

git update-ref "$test_tag_ref" "$parent_commit"
assert_rejected 'ne désigne pas le commit source' \
    env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

git update-ref "$test_tag_ref" "$head_commit"
git update-ref "$canonical_main_ref" "$head_commit"
git update-ref -d "$canonical_release_ref"
assert_rejected "branche de promotion introuvable : $canonical_release_ref" \
    env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

git update-ref "$canonical_release_ref" "$parent_commit"
assert_rejected "ne désigne pas le SHA promu par $canonical_release_ref" \
    env CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG="$test_tag" CI_PIPELINE_IID=1 \
    sh ci/package-artifact.sh

if grep -Fq 'build.pipeline.iid=' ci/package-artifact.sh; then
    echo 'FAIL: une provenance immuable ne doit pas contenir l’IID de la forge.' >&2
    exit 1
fi
if ! grep -Fq 'artifact.version=$artifact_version' ci/package-artifact.sh; then
    echo 'FAIL: la provenance doit porter la version canonique de l’artefact.' >&2
    exit 1
fi
if grep -Fq 'MAVEN_CACHE_POLICY' .gitlab-ci.yml; then
    echo 'FAIL: le dépôt Maven général ne doit jamais être placé dans un cache GitLab partagé.' >&2
    exit 1
fi
cache_count=$(grep -Ec '^  cache:$' .gitlab-ci.yml || true)
if [ "$cache_count" -ne 1 ]; then
    echo 'FAIL: seul le cache NVD qualifié de security:dependencies est autorisé.' >&2
    exit 1
fi
dependency_cache=$(awk '
    /^security:dependencies:/ { in_job = 1 }
    in_job && /^\.package:/ { exit }
    in_job { print }
' .gitlab-ci.yml)
if printf '%s\n' "$dependency_cache" | grep -Fq '.m2/repository/'; then
    echo 'FAIL: le cache Dependency-Check ne doit pas contenir le dépôt Maven général.' >&2
    exit 1
fi
if ! printf '%s\n' "$dependency_cache" |
       grep -Fq 'key: "dependency-check-12-2-2-nvd-json-2-0-${CI_COMMIT_REF_SLUG}"' ||
   ! printf '%s\n' "$dependency_cache" |
       grep -Fq -- '- .m2/dependency-check-data/' ||
   ! printf '%s\n' "$dependency_cache" |
       grep -Fq 'policy: pull-push'; then
    echo 'FAIL: le cache NVD doit rester versionné, isolé par ref et limité à sa base locale.' >&2
    exit 1
fi
if grep -Fq 'target/*.jar' .gitlab-ci.yml; then
    echo 'FAIL: un JAR exécutable ne doit pas être conservé depuis les tests GitLab de branches ou MR.' >&2
    exit 1
fi
if ! grep -Fq "github.event_name == 'push' && github.ref == 'refs/heads/main'" \
    .github/workflows/ci.yml; then
    echo 'FAIL: GitHub ne doit conserver un snapshot exécutable que depuis un push de main.' >&2
    exit 1
fi
if ! grep -Fq 'check-branch-name.sh "$BRANCH_NAME" github-pull-request' \
    .github/workflows/ci.yml; then
    echo 'FAIL: les Pull Requests GitHub doivent exclure les branches de promotion GitLab.' >&2
    exit 1
fi
if ! awk '
    /^  pull_request:/ { in_pull_request = 1; next }
    in_pull_request && /^    branches:/ { has_branches = 1; next }
    in_pull_request && /^      - main$/ { has_main = 1; exit }
    in_pull_request && /^[^ ]/ { exit }
    END { if (!has_branches || !has_main) exit 1 }
' .github/workflows/ci.yml; then
    echo 'FAIL: les Pull Requests GitHub doivent cibler exclusivement main.' >&2
    exit 1
fi
if grep -Fq "!startsWith(github.ref, 'refs/tags/')" .github/workflows/ci.yml; then
    echo 'FAIL: GitHub doit valider le bundle d’un tag sans le téléverser.' >&2
    exit 1
fi
if ! grep -Fq '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH' \
    .gitlab-ci.yml; then
    echo 'FAIL: GitLab ne doit conserver un snapshot que depuis un push de la branche par défaut.' >&2
    exit 1
fi
if ! grep -Fq '$CI_COMMIT_REF_PROTECTED == "true" && $CI_COMMIT_TAG =~' \
    .gitlab-ci.yml; then
    echo 'FAIL: une release GitLab exige un tag protégé.' >&2
    exit 1
fi
if ! grep -Fq '+refs/heads/${release_branch}:refs/remotes/origin/${release_branch}' \
    .gitlab-ci.yml; then
    echo 'FAIL: le pipeline de tag GitLab doit extraire la branche de promotion correspondante.' >&2
    exit 1
fi
if ! grep -Fq 'git fetch --tags origin' .gitlab-ci.yml ||
   ! grep -Fq 'check-gitlab-merge-request.sh "$project_version"' .gitlab-ci.yml; then
    echo 'FAIL: le pipeline de MR GitLab doit contrôler la version et les tags déjà publiés.' >&2
    exit 1
fi

sh ci/test-branch-name.sh
sh ci/test-gitlab-workflow.sh
sh ci/test-gitlab-merge-request.sh
sh ci/test-release-reproducibility.sh

printf 'PACKAGE_GIT_GUARDS=PASS\n'
