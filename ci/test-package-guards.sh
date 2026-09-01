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

if grep -Fq 'build.pipeline.iid=' ci/package-artifact.sh; then
    echo 'FAIL: une provenance immuable ne doit pas contenir l’IID de la forge.' >&2
    exit 1
fi
if ! grep -Fq 'artifact.version=$artifact_version' ci/package-artifact.sh; then
    echo 'FAIL: la provenance doit porter la version canonique de l’artefact.' >&2
    exit 1
fi
if grep -Eq 'MAVEN_CACHE_POLICY|^[[:space:]]*cache:' .gitlab-ci.yml; then
    echo 'FAIL: le cache GitLab partagé reste interdit sans isolation serveur qualifiée.' >&2
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

sh ci/test-branch-name.sh
sh ci/test-release-reproducibility.sh

printf 'PACKAGE_GIT_GUARDS=PASS\n'
