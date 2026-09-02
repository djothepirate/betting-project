#!/usr/bin/env sh
set -eu

repository=$(git rev-parse --show-toplevel)
cd "$repository"

sandbox=$(mktemp -d "${TMPDIR:-/tmp}/dependency-check-arguments.XXXXXX")
cleanup() {
    rm -f \
        "$sandbox/unset.log" \
        "$sandbox/empty.log" \
        "$sandbox/configured.log" \
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

assert_no_api_key_value() {
    log=$1
    if grep -Fq -- '-DnvdApiKey=' "$log"; then
        echo 'FAIL: une valeur de clé NVD est transmise directement à Maven.' >&2
        exit 1
    fi
}

unset_log="$sandbox/unset.log"
(
    unset NVD_API_KEY
    ARGUMENT_LOG="$unset_log" sh "$sandbox/ci/run-dependency-check.sh"
)
if grep -Fq -- '-DnvdApiKeyEnvironmentVariable=' "$unset_log"; then
    echo 'FAIL: le nom de variable NVD est transmis alors que la clé est absente.' >&2
    exit 1
fi
assert_no_api_key_value "$unset_log"

empty_log="$sandbox/empty.log"
NVD_API_KEY='' ARGUMENT_LOG="$empty_log" sh "$sandbox/ci/run-dependency-check.sh"
if grep -Fq -- '-DnvdApiKeyEnvironmentVariable=' "$empty_log"; then
    echo 'FAIL: le nom de variable NVD est transmis alors que la clé est vide.' >&2
    exit 1
fi
assert_no_api_key_value "$empty_log"

fixture_value='nonempty-fixture-value'
configured_log="$sandbox/configured.log"
NVD_API_KEY="$fixture_value" ARGUMENT_LOG="$configured_log" \
    sh "$sandbox/ci/run-dependency-check.sh"
assert_argument '-DnvdApiKeyEnvironmentVariable=NVD_API_KEY' "$configured_log"
assert_no_api_key_value "$configured_log"
if grep -Fq -- "$fixture_value" "$configured_log"; then
    echo 'FAIL: la valeur de la clé NVD apparaît dans les arguments Maven.' >&2
    exit 1
fi

assert_argument '-DdataDirectory='"$sandbox"'/target/dependency-check-data' "$unset_log"
assert_argument '-Dformats=HTML,JSON,GITLAB' "$unset_log"
assert_argument '-DfailOnError=true' "$unset_log"
assert_argument '-DfailBuildOnCVSS=11' "$unset_log"
assert_argument 'org.owasp:dependency-check-maven:12.2.2:check' "$unset_log"

if grep -Eq '^[[:space:]]+NVD_API_KEY:' .gitlab-ci.yml; then
    echo 'FAIL: NVD_API_KEY ne doit jamais être déclarée dans le dépôt.' >&2
    exit 1
fi

printf 'DEPENDENCY_CHECK_ARGUMENTS=PASS\n'
