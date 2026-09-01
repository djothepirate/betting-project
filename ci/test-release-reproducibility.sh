#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
fixture=$(mktemp -d /tmp/betting-release-fixture.XXXXXX)

cleanup() {
    rm -rf "$fixture"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$fixture/ci" "$fixture/target"
cp "$repository/ci/package-artifact.sh" "$fixture/ci/package-artifact.sh"
printf 'fixture-jar\n' >"$fixture/target/application.jar"

cat >"$fixture/mvnw" <<'MVNW'
#!/usr/bin/env sh
set -eu

for arg in "$@"; do
    if [ "$arg" = help:evaluate ]; then
        printf '%s\n' "${FIXTURE_MAVEN_VERSION:-1.2.3}"
        exit 0
    fi
done

output_dir=
project_type=
for arg in "$@"; do
    case "$arg" in
        -DoutputDirectory=*)
            output_dir=$(printf '%s' "$arg" | sed 's/^-DoutputDirectory=//')
            ;;
        -DprojectType=application)
            project_type=application
            ;;
    esac
done
if [ -z "$output_dir" ]; then
    echo 'FAIL: invocation Maven factice inattendue.' >&2
    exit 1
fi
if [ "$project_type" != application ]; then
    echo 'FAIL: projectType CycloneDX application absent.' >&2
    exit 1
fi
mkdir -p "$output_dir"
if [ "${FIXTURE_SBOM_MODE:-valid}" = invalid ]; then
    cat >"$output_dir/bom.json" <<'JSON'
{
  "bomFormat" : "CycloneDX",
  "specVersion" : "1.6",
  "metadata" : {
    "component" : {
      "type" : "library",
      "name" : "betting-project",
      "licenses" : [ { "license" : { "id" : "Apache-2.0" } } ],
      "externalReferences" : [
        { "type" : "website", "url" : "https://spring.io/projects/spring-boot/betting-project" },
        { "type" : "vcs", "url" : "https://github.com/spring-projects/spring-boot/betting-project" }
      ]
    },
    "properties" : [ {
      "name" : "cdx:reproducible",
      "value" : "enabled"
    } ]
  }
}
JSON
else
cat >"$output_dir/bom.json" <<'JSON'
{
  "bomFormat" : "CycloneDX",
  "specVersion" : "1.6",
  "metadata" : {
    "component" : {
      "type" : "application",
      "name" : "betting-project",
      "licenses" : [ { "license" : { "name" : "Proprietary" } } ],
      "externalReferences" : [
        { "type" : "website", "url" : "https://github.com/djothepirate/betting-project" },
        { "type" : "vcs", "url" : "https://github.com/djothepirate/betting-project" }
      ]
    },
    "properties" : [ {
      "name" : "cdx:reproducible",
      "value" : "enabled"
    } ]
  }
}
JSON
fi
MVNW
chmod +x "$fixture/mvnw"

(
    cd "$fixture"
    git init -q
    git add .
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit -qm 'release fixture'
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit --allow-empty -qm 'release fixture head'
    head_commit=$(git rev-parse HEAD)
    git update-ref refs/remotes/origin/main "$head_commit"

    invalid_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=foo-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=99 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=codex/invalid-version \
        sh ci/package-artifact.sh >"$invalid_version_output" 2>&1; then
        echo 'FAIL: une version Maven snapshot non SemVer a été acceptée.' >&2
        exit 1
    fi
    if ! grep -Fq 'version Maven snapshot hors convention SemVer' "$invalid_version_output"; then
        echo 'FAIL: le refus du snapshot Maven non SemVer est ambigu.' >&2
        cat "$invalid_version_output" >&2
        exit 1
    fi
    rm -f "$invalid_version_output"

    invalid_sbom_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_SBOM_MODE=invalid \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=98 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=codex/invalid-sbom \
        sh ci/package-artifact.sh >"$invalid_sbom_output" 2>&1; then
        echo 'FAIL: des métadonnées SBOM héritées ont été acceptées.' >&2
        exit 1
    fi
    if ! grep -Fq 'métadonnées racine du SBOM' "$invalid_sbom_output"; then
        echo 'FAIL: le refus des métadonnées SBOM héritées est ambigu.' >&2
        cat "$invalid_sbom_output" >&2
        exit 1
    fi
    rm -f "$invalid_sbom_output"

    # Une PR de préparation porte déjà la version Maven finale, mais elle reste
    # non taguée : le bundle doit être un snapshot, jamais une release implicite.
    umask 077
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=100 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=release/1.2.3 \
        sh ci/package-artifact.sh >/dev/null
    untagged_bundle=target/distribution/betting-project-1.2.3-snapshot.p100.g$(printf '%.12s' "$head_commit").tar.gz
    if [ ! -f "$untagged_bundle" ]; then
        echo 'FAIL: la préparation de release non taguée ne produit pas son snapshot.' >&2
        exit 1
    fi
    tar -xOf "$untagged_bundle" ./provenance.properties \
        >release-preparation.properties
    if ! grep -Fxq 'artifact.channel=snapshot' release-preparation.properties ||
       ! grep -Fxq 'source.tag=' release-preparation.properties ||
       ! grep -Fxq "artifact.version=1.2.3-snapshot.p100.g$(printf '%.12s' "$head_commit")" release-preparation.properties ||
       ! grep -Fxq 'production.approved=false' release-preparation.properties ||
       ! grep -Fxq 'vps.deployable=false' release-preparation.properties; then
        echo 'FAIL: la préparation non taguée est présentée comme une release promouvable.' >&2
        exit 1
    fi
    if [ -e target/distribution/betting-project-1.2.3.tar.gz ]; then
        echo 'FAIL: une version Maven finale sans tag a produit un bundle de release.' >&2
        exit 1
    fi

    git tag v1.2.3

    shallow_checkout="$fixture/shallow-checkout"
    git clone -q --depth 1 --no-tags "file://$fixture" "$shallow_checkout"
    (
        cd "$shallow_checkout"
        shallow_head=$(git rev-parse HEAD)
        git tag v1.2.3
        git update-ref refs/remotes/origin/main "$shallow_head"
        shallow_output=$(mktemp)
        if env -i PATH="$PATH" SOURCE_COMMIT_SHA="$shallow_head" \
            GITHUB_RUN_NUMBER=100 GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
            sh ci/package-artifact.sh >"$shallow_output" 2>&1; then
            echo 'FAIL: une release issue d’un historique shallow a été acceptée.' >&2
            exit 1
        fi
        if ! grep -Fq 'historique Git complet est requis' "$shallow_output"; then
            echo 'FAIL: le refus de l’historique shallow est ambigu.' >&2
            cat "$shallow_output" >&2
            exit 1
        fi
        rm -f "$shallow_output"
    )

    real_git=$(command -v git)
    git_shim="$fixture/git-shim"
    mkdir -p "$git_shim"
    cat >"$git_shim/git" <<'GIT'
#!/usr/bin/env sh
if [ "$1" = merge-base ] && [ "${2:-}" = --is-ancestor ]; then
    exit 2
fi
exec "$REAL_GIT" "$@"
GIT
    chmod +x "$git_shim/git"
    git_error_output=$(mktemp)
    if env -i PATH="$git_shim:$PATH" REAL_GIT="$real_git" \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=100 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
        sh ci/package-artifact.sh >"$git_error_output" 2>&1; then
        echo 'FAIL: une erreur interne Git a été acceptée pendant la qualification du tag.' >&2
        exit 1
    fi
    if ! grep -Fq "impossible de vérifier l'appartenance" "$git_error_output"; then
        echo 'FAIL: le refus de l’erreur interne Git est ambigu.' >&2
        cat "$git_error_output" >&2
        exit 1
    fi
    rm -f "$git_error_output"

    umask 002
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=101 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
        sh ci/package-artifact.sh >/dev/null
    cp target/distribution/betting-project-1.2.3.tar.gz release-github.tar.gz

    umask 077
    env -i PATH="$PATH" CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3 \
        CI_PIPELINE_IID=909 sh ci/package-artifact.sh >/dev/null
    cp target/distribution/betting-project-1.2.3.tar.gz release-gitlab.tar.gz
)

if ! cmp -s "$fixture/release-github.tar.gz" "$fixture/release-gitlab.tar.gz"; then
    echo 'FAIL: le payload de release dépend encore de l’identifiant de forge.' >&2
    exit 1
fi

tar -xOf "$fixture/release-github.tar.gz" ./provenance.properties \
    >"$fixture/provenance.properties"
if grep -Eq '^build\.pipeline\.' "$fixture/provenance.properties"; then
    echo 'FAIL: la provenance de release contient un identifiant de pipeline.' >&2
    exit 1
fi
if ! grep -Fxq 'artifact.version=1.2.3' "$fixture/provenance.properties"; then
    echo 'FAIL: la version canonique de release est absente de la provenance.' >&2
    exit 1
fi

printf 'RELEASE_REPRODUCIBILITY=PASS\n'
