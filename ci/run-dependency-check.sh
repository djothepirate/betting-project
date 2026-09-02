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
    -Dformats=HTML,JSON,GITLAB \
    -DprettyPrint=true \
    -DfailOnError=true \
    -DossindexAnalyzerEnabled=false \
    -DfailBuildOnCVSS=11

# Dependency-Check lit la valeur dans l'environnement. Seul le nom de la variable est transmis à
# Maven, et uniquement lorsqu'une valeur non vide est réellement disponible.
if [ -n "${NVD_API_KEY:-}" ]; then
    set -- "$@" -DnvdApiKeyEnvironmentVariable=NVD_API_KEY
fi

set -- "$@" org.owasp:dependency-check-maven:12.2.2:check
exec ./mvnw "$@"
