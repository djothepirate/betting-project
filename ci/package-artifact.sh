#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

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

if ! resolved_commit=$(git rev-parse --verify "${commit_sha}^{commit}" 2>/dev/null); then
    echo "FAIL: commit source introuvable : $commit_sha." >&2
    exit 1
fi
head_commit=$(git rev-parse --verify 'HEAD^{commit}')
if [ "$resolved_commit" != "$head_commit" ]; then
    echo "FAIL: le SHA source $resolved_commit ne correspond pas au commit extrait $head_commit." >&2
    exit 1
fi
commit_sha=$resolved_commit

short_sha=$(printf '%.12s' "$commit_sha")
source_epoch=$(git show -s --format=%ct "$commit_sha")
case "$source_epoch" in
    *[!0-9]*|'')
        echo 'FAIL: horodatage du commit source invalide.' >&2
        exit 1
        ;;
esac
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
    if ! tagged_commit=$(git rev-parse --verify "refs/tags/${tag}^{commit}" 2>/dev/null); then
        echo "FAIL: le tag $tag est absent du checkout." >&2
        exit 1
    fi
    if [ "$tagged_commit" != "$commit_sha" ]; then
        echo "FAIL: le tag $tag ne désigne pas le commit source $commit_sha." >&2
        exit 1
    fi
    if ! shallow=$(git rev-parse --is-shallow-repository 2>/dev/null); then
        echo 'FAIL: impossible de déterminer si le dépôt est shallow.' >&2
        exit 1
    fi
    if [ "$shallow" != false ]; then
        echo 'FAIL: un historique Git complet est requis pour publier une release.' >&2
        exit 1
    fi
    canonical_main_ref=refs/remotes/origin/main
    if ! canonical_main_commit=$(git rev-parse --verify "${canonical_main_ref}^{commit}" 2>/dev/null); then
        echo "FAIL: référence canonique main introuvable : $canonical_main_ref." >&2
        exit 1
    fi
    if git merge-base --is-ancestor "$tagged_commit" "$canonical_main_commit"; then
        :
    else
        ancestry_status=$?
        if [ "$ancestry_status" -eq 1 ]; then
            echo "FAIL: le commit tagué $tagged_commit n'est pas atteignable depuis $canonical_main_ref." >&2
        else
            echo "FAIL: impossible de vérifier l'appartenance du tag $tag à $canonical_main_ref." >&2
        fi
        exit 1
    fi
fi

version=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)
if [ -n "$tag" ]; then
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

sbom_work=$(mktemp -d "${TMPDIR:-/tmp}/betting-sbom.XXXXXX")
trap 'rm -rf "$sbom_work"' EXIT

generate_sbom() {
    output_dir=$1
    mkdir -p "$output_dir"
    ./mvnw -B -ntp -DskipTests \
        -Dproject.build.outputTimestamp="$source_epoch" \
        -DincludeBomSerialNumber=false \
        -DoutputFormat=json \
        -DoutputDirectory="$output_dir" \
        -DoutputName=bom \
        -DoutputReactorProjects=false \
        -Dcyclonedx.skip=false \
        -Dcyclonedx.skipAttach=true \
        org.cyclonedx:cyclonedx-maven-plugin:2.9.2:makeAggregateBom
    if [ ! -s "$output_dir/bom.json" ]; then
        echo "FAIL: le SBOM CycloneDX est absent ou vide dans $output_dir." >&2
        exit 1
    fi
}

first_sbom=$sbom_work/run-1/bom.json
second_sbom=$sbom_work/run-2/bom.json
generate_sbom "$sbom_work/run-1"
generate_sbom "$sbom_work/run-2"
if ! cmp -s "$first_sbom" "$second_sbom"; then
    echo 'FAIL: le SBOM CycloneDX n’est pas reproductible.' >&2
    echo "Première empreinte : $(sha256sum "$first_sbom" | awk '{print $1}')" >&2
    echo "Seconde empreinte : $(sha256sum "$second_sbom" | awk '{print $1}')" >&2
    exit 1
fi
if grep -Eq '"serialNumber"[[:space:]]*:' "$first_sbom"; then
    echo 'FAIL: le SBOM reproductible ne doit pas contenir de numéro de série.' >&2
    exit 1
fi
if grep -Eq '"timestamp"[[:space:]]*:' "$first_sbom"; then
    echo 'FAIL: le SBOM reproductible ne doit pas contenir de timestamp.' >&2
    exit 1
fi
if ! grep -Eq '"bomFormat"[[:space:]]*:[[:space:]]*"CycloneDX"' "$first_sbom" ||
   ! grep -Eq '"specVersion"[[:space:]]*:[[:space:]]*"1\\.6"' "$first_sbom"; then
    echo 'FAIL: le document produit n’est pas un SBOM CycloneDX 1.6.' >&2
    exit 1
fi
if ! awk '
    /"name"[[:space:]]*:[[:space:]]*"cdx:reproducible"/ {
        found++
        expecting_value = 1
        next
    }
    expecting_value && /"value"[[:space:]]*:/ {
        if ($0 !~ /"value"[[:space:]]*:[[:space:]]*"enabled"/) exit 2
        enabled++
        expecting_value = 0
    }
    END { if (found != 1 || enabled != 1) exit 1 }
' "$first_sbom"; then
    echo 'FAIL: le SBOM ne déclare pas une fois le mode reproductible CycloneDX activé.' >&2
    exit 1
fi
cp "$first_sbom" target/bom.json

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
source.epoch=$source_epoch
source.tag=$tag
maven.version=$version
build.pipeline.iid=$pipeline_iid
java.target=25
sbom.format=CycloneDX-JSON
sbom.reproducible=true
sbom.serial-number=omitted
EOF

(
    cd "$stage"
    sha256sum "$jar_name" sbom.cdx.json provenance.properties >SHA256SUMS
)

tar --sort=name --mtime="@$source_epoch" --owner=0 --group=0 --numeric-owner \
    -C "$stage" -czf "$distribution/$bundle_name" .
(
    cd "$distribution"
    sha256sum "$bundle_name" >"$bundle_name.sha256"
)
rm -rf "$stage"

printf 'PACKAGE_RESULT=PASS\n'
printf 'PACKAGE_CHANNEL=%s\n' "$channel"
printf 'PACKAGE_FILE=%s\n' "$distribution/$bundle_name"
printf 'VPS_DEPLOYABLE=NO_PENDING_DEDICATED_WORK_ORDER\n'
