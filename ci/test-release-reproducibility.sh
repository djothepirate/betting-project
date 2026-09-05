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
    parent_commit=$(git rev-parse HEAD^)
    git update-ref refs/remotes/origin/main "$head_commit"
    git update-ref refs/remotes/origin/feature/V1.2.3 "$head_commit"

    invalid_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=foo-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=99 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3 \
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
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3 \
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

    branch_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.4-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=97 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3 \
        sh ci/package-artifact.sh >"$branch_version_output" 2>&1; then
        echo 'FAIL: une branche feature a accepté une version Maven différente.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.2.3 ou 1.2.3-SNAPSHOT' \
        "$branch_version_output"; then
        echo 'FAIL: le refus du décalage branche/version Maven est ambigu.' >&2
        cat "$branch_version_output" >&2
        exit 1
    fi
    rm -f "$branch_version_output"

    # Seul le push qui crée une feature d'intégration exactement au sommet de
    # main peut conserver temporairement la version Maven héritée.
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        sh ci/package-artifact.sh >/dev/null
    seed_bundle=target/distribution/betting-project-1.2.3-snapshot.p95.g$(printf '%.12s' "$head_commit").tar.gz
    if [ ! -f "$seed_bundle" ]; then
        echo 'FAIL: l’amorçage exact du nouveau train GitHub ne produit pas son snapshot.' >&2
        exit 1
    fi
    tar -xOf "$seed_bundle" ./provenance.properties >train-seed.properties
    if ! grep -Fxq 'artifact.channel=snapshot' train-seed.properties ||
       ! grep -Fxq 'source.train.seed=true' train-seed.properties; then
        echo 'FAIL: le snapshot d’amorçage n’est pas identifié explicitement.' >&2
        exit 1
    fi

    for seed_branch in feature/V1.3.0-RC01 feature/V1.3.0-RC01-SNAPSHOT; do
        seed_output=$(mktemp)
        env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
            SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=94 \
            GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
            GITHUB_REF_TYPE=branch GITHUB_REF_NAME="$seed_branch" \
            sh ci/package-artifact.sh >"$seed_output"
        if ! grep -Fq "PACKAGE_VERSION_POLICY=PASS:exact-train-seed:${seed_branch}@${head_commit}" \
            "$seed_output"; then
            echo "FAIL: l’amorçage GitHub du train $seed_branch n’a pas emprunté la voie bornée." >&2
            cat "$seed_output" >&2
            exit 1
        fi
        rm -f "$seed_output"
    done

    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.3.0 \
        CI_PIPELINE_IID=95 CI_PIPELINE_SOURCE=push \
        CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 \
        sh ci/package-artifact.sh >/dev/null
    for seed_branch in feature/V1.3.0-RC01 feature/V1.3.0-RC01-SNAPSHOT; do
        seed_output=$(mktemp)
        env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
            CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH="$seed_branch" \
            CI_PIPELINE_IID=94 CI_PIPELINE_SOURCE=push \
            CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 \
            sh ci/package-artifact.sh >"$seed_output"
        if ! grep -Fq "PACKAGE_VERSION_POLICY=PASS:exact-train-seed:${seed_branch}@${head_commit}" \
            "$seed_output"; then
            echo "FAIL: l’amorçage GitLab du train $seed_branch n’a pas emprunté la voie bornée." >&2
            cat "$seed_output" >&2
            exit 1
        fi
        rm -f "$seed_output"
    done

    github_replay_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=false \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        sh ci/package-artifact.sh >"$github_replay_output" 2>&1; then
        echo 'FAIL: un push GitHub ultérieur a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0 ou 1.3.0-SNAPSHOT' \
        "$github_replay_output"; then
        echo 'FAIL: le refus du rejeu GitHub est ambigu.' >&2
        cat "$github_replay_output" >&2
        exit 1
    fi
    rm -f "$github_replay_output"

    gitlab_replay_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.3.0 \
        CI_PIPELINE_IID=95 CI_PIPELINE_SOURCE=push \
        CI_COMMIT_BEFORE_SHA="$parent_commit" \
        sh ci/package-artifact.sh >"$gitlab_replay_output" 2>&1; then
        echo 'FAIL: un push GitLab ultérieur a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0 ou 1.3.0-SNAPSHOT' \
        "$gitlab_replay_output"; then
        echo 'FAIL: le refus du rejeu GitLab est ambigu.' >&2
        cat "$gitlab_replay_output" >&2
        exit 1
    fi
    rm -f "$gitlab_replay_output"

    git update-ref refs/remotes/origin/main "$parent_commit"
    divergent_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        sh ci/package-artifact.sh >"$divergent_seed_output" 2>&1; then
        echo 'FAIL: un amorçage divergent du sommet canonique de main a été accepté.' >&2
        exit 1
    fi
    if ! grep -Fq 'sommet canonique exact de refs/remotes/origin/main' \
        "$divergent_seed_output"; then
        echo 'FAIL: le refus de l’amorçage divergent est ambigu.' >&2
        cat "$divergent_seed_output" >&2
        exit 1
    fi
    rm -f "$divergent_seed_output"
    git update-ref refs/remotes/origin/main "$head_commit"

    git update-ref -d refs/remotes/origin/main
    missing_seed_main_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        sh ci/package-artifact.sh >"$missing_seed_main_output" 2>&1; then
        echo 'FAIL: un amorçage sans référence canonique main a été accepté.' >&2
        exit 1
    fi
    if ! grep -Fq "exige la référence canonique refs/remotes/origin/main" \
        "$missing_seed_main_output"; then
        echo 'FAIL: le refus de l’amorçage sans main est ambigu.' >&2
        cat "$missing_seed_main_output" >&2
        exit 1
    fi
    rm -f "$missing_seed_main_output"
    git update-ref refs/remotes/origin/main "$head_commit"

    dispatch_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=workflow_dispatch SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.3.0 \
        sh ci/package-artifact.sh >"$dispatch_seed_output" 2>&1; then
        echo 'FAIL: un workflow_dispatch GitHub a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0 ou 1.3.0-SNAPSHOT' \
        "$dispatch_seed_output"; then
        echo 'FAIL: le refus de l’amorçage GitHub manuel est ambigu.' >&2
        cat "$dispatch_seed_output" >&2
        exit 1
    fi
    rm -f "$dispatch_seed_output"

    web_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_BRANCH=feature/V1.3.0 \
        CI_PIPELINE_IID=95 CI_PIPELINE_SOURCE=web \
        CI_COMMIT_BEFORE_SHA=0000000000000000000000000000000000000000 \
        sh ci/package-artifact.sh >"$web_seed_output" 2>&1; then
        echo 'FAIL: un pipeline Web GitLab a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.3.0 ou 1.3.0-SNAPSHOT' \
        "$web_seed_output"; then
        echo 'FAIL: le refus de l’amorçage GitLab hors push est ambigu.' >&2
        cat "$web_seed_output" >&2
        exit 1
    fi
    rm -f "$web_seed_output"

    work_order_seed_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=2.0.0-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=95 \
        GITHUB_EVENT_NAME=push SOURCE_REF_CREATED=true \
        GITHUB_REF_TYPE=branch \
        GITHUB_REF_NAME=feature/V1.2.3-HUMAN-CODEX-005 \
        sh ci/package-artifact.sh >"$work_order_seed_output" 2>&1; then
        echo 'FAIL: une branche de Work Order a usurpé l’amorçage du train.' >&2
        exit 1
    fi
    if ! grep -Fq 'exige la version Maven 1.2.3 ou 1.2.3-SNAPSHOT' \
        "$work_order_seed_output"; then
        echo 'FAIL: le refus de l’amorçage par une branche Work Order est ambigu.' >&2
        cat "$work_order_seed_output" >&2
        exit 1
    fi
    rm -f "$work_order_seed_output"

    work_order_version_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=2.0.0-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=97 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=123/merge \
        GITHUB_HEAD_REF=feature/V1.2.3-CODEX-CI-005 \
        sh ci/package-artifact.sh >"$work_order_version_output" 2>&1; then
        echo 'FAIL: une Pull Request de Work Order a accepté une version Maven d’un autre train.' >&2
        exit 1
    fi
    if ! grep -Fq 'feature/V1.2.3-CODEX-CI-005 exige la version Maven 1.2.3 ou 1.2.3-SNAPSHOT' \
        "$work_order_version_output"; then
        echo 'FAIL: le refus du décalage Work Order/version Maven est ambigu.' >&2
        cat "$work_order_version_output" >&2
        exit 1
    fi
    rm -f "$work_order_version_output"

    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=123/merge \
        GITHUB_HEAD_REF=feature/V1.2.3-HUMAN-CI-005 \
        sh ci/package-artifact.sh >/dev/null
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=125/merge \
        GITHUB_HEAD_REF=feature/V1.2.3-HUMAN-CODEX-005 \
        sh ci/package-artifact.sh >/dev/null
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=126/merge \
        GITHUB_HEAD_REF=feature/V1.2.3-CODEX-HUMAN-005 \
        sh ci/package-artifact.sh >/dev/null
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1 \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=124/merge \
        GITHUB_HEAD_REF=feature/V1.2.3-RC01-CODEX-CI-005 \
        sh ci/package-artifact.sh >/dev/null

    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1-SNAPSHOT \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=96 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3-RC01-SNAPSHOT \
        sh ci/package-artifact.sh >/dev/null

    # Une PR de préparation porte déjà la version Maven finale, mais elle reste
    # non taguée : le bundle doit être un snapshot, jamais une release implicite.
    umask 077
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=100 \
        GITHUB_REF_TYPE=branch GITHUB_REF_NAME=feature/V1.2.3 \
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
        git update-ref refs/remotes/origin/feature/V1.2.3 "$shallow_head"
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

    umask 002
    env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=101 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3 \
        sh ci/package-artifact.sh >/dev/null
    cp target/distribution/betting-project-1.2.3.tar.gz release-github.tar.gz

    umask 077
    git update-ref refs/remotes/origin/release/V1.2.3 "$head_commit"
    env -i PATH="$PATH" CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3 \
        CI_PIPELINE_IID=909 sh ci/package-artifact.sh >/dev/null
    cp target/distribution/betting-project-1.2.3.tar.gz release-gitlab.tar.gz

    invalid_rc_tag_output=$(mktemp)
    if env -i PATH="$PATH" SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=102 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3-RC01 \
        sh ci/package-artifact.sh >"$invalid_rc_tag_output" 2>&1; then
        echo 'FAIL: un tag RC en majuscules a été accepté.' >&2
        exit 1
    fi
    if ! grep -Fq 'tag hors convention SemVer' "$invalid_rc_tag_output"; then
        echo 'FAIL: le refus du tag RC en majuscules est ambigu.' >&2
        cat "$invalid_rc_tag_output" >&2
        exit 1
    fi
    rm -f "$invalid_rc_tag_output"

    git tag v1.2.3-rc.100
    rc_range_output=$(mktemp)
    if env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.100 \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=102 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3-rc.100 \
        sh ci/package-artifact.sh >"$rc_range_output" 2>&1; then
        echo 'FAIL: un tag rc.100 sans train RC branchable a été accepté.' >&2
        exit 1
    fi
    if ! grep -Fq 'dépasse la plage des branches RC01..RC99' "$rc_range_output"; then
        echo 'FAIL: le refus du tag rc.100 est ambigu.' >&2
        cat "$rc_range_output" >&2
        exit 1
    fi
    rm -f "$rc_range_output"

    git tag v1.2.3-rc.1
    git update-ref refs/remotes/origin/feature/V1.2.3-RC01 "$head_commit"
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1 \
        SOURCE_COMMIT_SHA="$head_commit" GITHUB_RUN_NUMBER=103 \
        GITHUB_REF_TYPE=tag GITHUB_REF_NAME=v1.2.3-rc.1 \
        sh ci/package-artifact.sh >/dev/null
    cp target/distribution/betting-project-1.2.3-rc.1.tar.gz release-rc-github.tar.gz

    git update-ref refs/remotes/origin/release/V1.2.3-RC01 "$head_commit"
    env -i PATH="$PATH" FIXTURE_MAVEN_VERSION=1.2.3-rc.1 \
        CI_COMMIT_SHA="$head_commit" CI_COMMIT_TAG=v1.2.3-rc.1 \
        CI_PIPELINE_IID=910 sh ci/package-artifact.sh >/dev/null
    cp target/distribution/betting-project-1.2.3-rc.1.tar.gz release-rc-gitlab.tar.gz
)

if ! cmp -s "$fixture/release-github.tar.gz" "$fixture/release-gitlab.tar.gz"; then
    echo 'FAIL: le payload de release dépend encore de l’identifiant de forge.' >&2
    exit 1
fi
if ! cmp -s "$fixture/release-rc-github.tar.gz" "$fixture/release-rc-gitlab.tar.gz"; then
    echo 'FAIL: le payload de release candidate dépend encore de l’identifiant de forge.' >&2
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
