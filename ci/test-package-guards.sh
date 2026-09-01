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

printf 'PACKAGE_GIT_GUARDS=PASS\n'
