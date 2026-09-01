#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

version=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)
commit_sha=${CI_COMMIT_SHA:-${SOURCE_COMMIT_SHA:-${GITHUB_SHA:-}}}
pipeline_iid=${CI_PIPELINE_IID:-${GITHUB_RUN_NUMBER:-0}}
tag=${CI_COMMIT_TAG:-}

if [ -z "$commit_sha" ]; then
    commit_sha=$(git rev-parse HEAD)
fi
if [ -z "$tag" ] && [ "${GITHUB_REF_TYPE:-}" = tag ]; then
    tag=${GITHUB_REF_NAME:-}
fi

case "$commit_sha" in
    *[!0-9a-f]*|'')
        echo 'FAIL: SHA source invalide.' >&2
        exit 1
        ;;
esac

short_sha=$(printf '%.12s' "$commit_sha")
case "$pipeline_iid" in
    *[!0-9]*)
        echo 'FAIL: identifiant de pipeline invalide.' >&2
        exit 1
        ;;
esac

channel=snapshot
if [ -n "$tag" ]; then
    if ! printf '%s' "$tag" | grep -Eq '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-rc\.[1-9][0-9]*)?$'; then
        echo "FAIL: tag hors convention SemVer : $tag" >&2
        exit 1
    fi
    expected_version=${tag#v}
    if [ "$version" != "$expected_version" ]; then
        echo "FAIL: le tag $tag ne correspond pas à la version Maven $version." >&2
        exit 1
    fi
    artifact_version=$version
    case "$version" in
        *-rc.*) channel=release-candidate ;;
        *) channel=release ;;
    esac
else
    case "$version" in
        *-SNAPSHOT) ;;
        *)
            echo "FAIL: un build sans tag exige une version Maven -SNAPSHOT, reçu $version." >&2
            exit 1
            ;;
    esac
    base_version=${version%-SNAPSHOT}
    artifact_version="${base_version}-snapshot.p${pipeline_iid}.g${short_sha}"
fi

source_jar=
jar_count=0
for candidate in target/*.jar; do
    [ -f "$candidate" ] || continue
    case "$candidate" in
        *.original|*-sources.jar|*-javadoc.jar|*-tests.jar) continue ;;
    esac
    source_jar=$candidate
    jar_count=$((jar_count + 1))
done

if [ "$jar_count" -ne 1 ]; then
    echo "FAIL: exactement un JAR applicatif est attendu, reçu $jar_count." >&2
    exit 1
fi

./mvnw -B -ntp -DskipTests \
    org.cyclonedx:cyclonedx-maven-plugin:2.9.2:makeAggregateBom
test -f target/bom.json

distribution=target/distribution
stage=$distribution/stage
rm -rf "$distribution"
mkdir -p "$stage"

jar_name="betting-project-${artifact_version}.jar"
bundle_name="betting-project-${artifact_version}.tar.gz"
cp "$source_jar" "$stage/$jar_name"
cp target/bom.json "$stage/sbom.cdx.json"

cat >"$stage/provenance.properties" <<EOF
artifact.classification=APPLICATION_BUILD_CANDIDATE
artifact.channel=$channel
production.approved=false
vps.deployable=false
source.repository=djothepirate/betting-project
source.commit=$commit_sha
source.tag=$tag
maven.version=$version
build.pipeline.iid=$pipeline_iid
java.target=25
sbom.format=CycloneDX-JSON
EOF

(
    cd "$stage"
    sha256sum "$jar_name" sbom.cdx.json provenance.properties >SHA256SUMS
)

tar -C "$stage" -czf "$distribution/$bundle_name" .
(
    cd "$distribution"
    sha256sum "$bundle_name" >"$bundle_name.sha256"
)
rm -rf "$stage"

printf 'PACKAGE_RESULT=PASS\n'
printf 'PACKAGE_CHANNEL=%s\n' "$channel"
printf 'PACKAGE_FILE=%s\n' "$distribution/$bundle_name"
printf 'VPS_DEPLOYABLE=NO_PENDING_DEDICATED_WORK_ORDER\n'
