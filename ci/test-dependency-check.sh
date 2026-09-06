#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

# Les chemins attendus appartiennent à la fixture, même lorsque le runner définit
# MAVEN_USER_HOME (GitLab) ou un répertoire Dependency-Check explicite.
unset MAVEN_USER_HOME DEPENDENCY_CHECK_DATA_DIRECTORY

sandbox=$(mktemp -d "${TMPDIR:-/tmp}/dependency-check-arguments.XXXXXX")
cleanup() {
    rm -f \
        "$sandbox/unset.log" \
        "$sandbox/empty.log" \
        "$sandbox/configured.log" \
        "$sandbox/maven-home.log" \
        "$sandbox/explicit-directory.log" \
        "$sandbox/ci/run-dependency-check.sh" \
        "$sandbox/mvnw"
    rmdir "$sandbox/ci" "$sandbox" 2>/dev/null || true
}
trap cleanup EXIT HUP INT TERM
mkdir -p "$sandbox/ci"
cp ci/run-dependency-check.sh "$sandbox/ci/run-dependency-check.sh"

cat >"$sandbox/mvnw" <<'EOF'
#!/usr/bin/env sh
set -eu
: "${ARGUMENT_LOG:?ARGUMENT_LOG doit désigner le journal de test}"
printf '%s\n' "$@" >"$ARGUMENT_LOG"
EOF
chmod +x "$sandbox/mvnw" "$sandbox/ci/run-dependency-check.sh"

assert_argument() {
    expected=$1
    log=$2
    if ! grep -Fqx -- "$expected" "$log"; then
        printf 'FAIL: argument Dependency-Check absent : %s\n' "$expected" >&2
        exit 1
    fi
}

assert_no_api_key_option() {
    log=$1
    if grep -Fq -- '-DnvdApiKey' "$log"; then
        echo 'FAIL: le flux JSON public ne doit transmettre aucune option de clé NVD.' >&2
        exit 1
    fi
}

unset_log="$sandbox/unset.log"
(
    unset NVD_API_KEY
    ARGUMENT_LOG="$unset_log" sh "$sandbox/ci/run-dependency-check.sh"
)
assert_no_api_key_option "$unset_log"

empty_log="$sandbox/empty.log"
NVD_API_KEY='' ARGUMENT_LOG="$empty_log" sh "$sandbox/ci/run-dependency-check.sh"
assert_no_api_key_option "$empty_log"

fixture_value='nonempty-fixture-value'
configured_log="$sandbox/configured.log"
NVD_API_KEY="$fixture_value" ARGUMENT_LOG="$configured_log" \
    sh "$sandbox/ci/run-dependency-check.sh"
assert_no_api_key_option "$configured_log"
if grep -Fq -- "$fixture_value" "$configured_log"; then
    echo 'FAIL: la valeur de la clé NVD apparaît dans les arguments Maven.' >&2
    exit 1
fi

assert_argument '-DdataDirectory='"$sandbox"'/target/dependency-check-data' "$unset_log"
assert_argument '-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz' "$unset_log"
assert_argument '-Dformats=HTML,JSON,GITLAB' "$unset_log"
assert_argument '-DfailOnError=true' "$unset_log"
assert_argument '-DfailBuildOnCVSS=7' "$unset_log"
assert_argument 'org.owasp:dependency-check-maven:12.2.2:check' "$unset_log"

maven_home_log="$sandbox/maven-home.log"
MAVEN_USER_HOME="$sandbox/maven cache" ARGUMENT_LOG="$maven_home_log" \
    sh "$sandbox/ci/run-dependency-check.sh"
assert_argument "-DdataDirectory=$sandbox/maven cache/dependency-check-data" "$maven_home_log"
assert_no_api_key_option "$maven_home_log"

explicit_directory_log="$sandbox/explicit-directory.log"
MAVEN_USER_HOME="$sandbox/maven cache" \
    DEPENDENCY_CHECK_DATA_DIRECTORY="$sandbox/explicit cache" \
    ARGUMENT_LOG="$explicit_directory_log" sh "$sandbox/ci/run-dependency-check.sh"
assert_argument "-DdataDirectory=$sandbox/explicit cache" "$explicit_directory_log"
assert_no_api_key_option "$explicit_directory_log"

if grep -Eq '^[[:space:]]+NVD_API_KEY:' .gitlab-ci.yml; then
    echo 'FAIL: NVD_API_KEY ne doit jamais être déclarée dans le dépôt.' >&2
    exit 1
fi

dependency_job=$(awk '
    /^security:dependencies:/ { in_job = 1 }
    in_job && /^\.package:/ { exit }
    in_job { print }
' .gitlab-ci.yml)
if printf '%s\n' "$dependency_job" | grep -Eq '^  allow_failure:'; then
    echo 'FAIL: security:dependencies doit être bloquant après établissement de la baseline.' >&2
    exit 1
fi

printf 'DEPENDENCY_CHECK_ARGUMENTS=PASS\n'
