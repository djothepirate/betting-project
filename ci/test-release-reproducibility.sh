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
        printf '1.2.3\n'
        exit 0
    fi
done

output_dir=
for arg in "$@"; do
    case "$arg" in
        -DoutputDirectory=*)
            output_dir=$(printf '%s' "$arg" | sed 's/^-DoutputDirectory=//')
            ;;
    esac
done
if [ -z "$output_dir" ]; then
    echo 'FAIL: invocation Maven factice inattendue.' >&2
    exit 1
fi
mkdir -p "$output_dir"
cat >"$output_dir/bom.json" <<'JSON'
{
  "bomFormat" : "CycloneDX",
  "specVersion" : "1.6",
  "metadata" : {
    "properties" : [ {
      "name" : "cdx:reproducible",
      "value" : "enabled"
    } ]
  }
}
JSON
MVNW
chmod +x "$fixture/mvnw"

(
    cd "$fixture"
    git init -q
    git add .
    git -c user.name=ci-fixture -c user.email=ci-fixture.invalid@example.test \
        commit -qm 'release fixture'
    head_commit=$(git rev-parse HEAD)
    git update-ref refs/remotes/origin/main "$head_commit"
    git tag v1.2.3

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
