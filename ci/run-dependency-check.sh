#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository=$(CDPATH= cd -- "$script_dir/.." && pwd)
cd "$repository"

data_directory=${DEPENDENCY_CHECK_DATA_DIRECTORY:-${MAVEN_USER_HOME:-"$repository/target"}/dependency-check-data}

set -- \
    -B \
    -ntp \
    -DskipTests \
    "-DdataDirectory=$data_directory" \
    "-DnvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz" \
    -Dformats=HTML,JSON,GITLAB \
    -DprettyPrint=true \
    -DfailOnError=true \
    -DossindexAnalyzerEnabled=false \
    -DfailBuildOnCVSS=7

# Le flux JSON 2.0 public évite l'API NVD limitée. Aucune option de clé n'est transmise, même si
# l'environnement du runner contient accidentellement NVD_API_KEY.

set -- "$@" org.owasp:dependency-check-maven:12.2.2:check
exec ./mvnw "$@"
