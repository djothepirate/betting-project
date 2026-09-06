#!/usr/bin/env sh
set -eu

source_branch=${1:-}
target_branch=${2:-}
base_sha=${3:-}
head_sha=${4:-}
project_version=${5:-}
script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
repository=$(CDPATH= cd -- "$script_dir/.." && pwd)

bootstrap_source=codex/ci-004-version-branch-workflow
bootstrap_target=main
bootstrap_base=3fb224e9724698324a56b47fe5d943ecd366f197
bootstrap_version=0.1.0-SNAPSHOT

if [ "$source_branch" = "$bootstrap_source" ]; then
    if [ "$target_branch" != "$bootstrap_target" ] ||
       [ "$base_sha" != "$bootstrap_base" ]; then
        echo "FAIL: le bootstrap CI-004 exige ${bootstrap_source} -> ${bootstrap_target} sur la base ${bootstrap_base}." >&2
        exit 1
    fi
    if [ "$project_version" != "$bootstrap_version" ]; then
        echo "FAIL: le bootstrap CI-004 exige la version Maven ${bootstrap_version}, reçue : ${project_version:-<vide>}." >&2
        exit 1
    fi
    if ! git -C "$repository" rev-parse --verify "${head_sha}^{commit}" >/dev/null 2>&1; then
        echo "FAIL: le SHA de tête du bootstrap CI-004 est absent ou invalide : ${head_sha:-<vide>}." >&2
        exit 1
    fi
    if ! git -C "$repository" merge-base --is-ancestor \
            "$bootstrap_base" "$head_sha"; then
        echo "FAIL: la base $bootstrap_base n'est pas ancêtre du SHA de tête $head_sha pour le bootstrap CI-004." >&2
        exit 1
    fi
    if ! bootstrap_merge_base=$(git -C "$repository" merge-base \
            "$bootstrap_base" "$head_sha" 2>/dev/null) ||
       [ "$bootstrap_merge_base" != "$bootstrap_base" ]; then
        echo "FAIL: le merge-base du bootstrap CI-004 doit être exactement $bootstrap_base, reçu : ${bootstrap_merge_base:-<introuvable>}." >&2
        exit 1
    fi
    printf 'GITHUB_PR_POLICY=PASS:bootstrap:%s->%s@%s..%s\n' \
        "$source_branch" "$target_branch" "$base_sha" "$head_sha"
    exit 0
fi

train_to_maven() {
    train=$1
    case "$train" in
        *-RC*-SNAPSHOT)
            candidate=${train%-SNAPSHOT}
            core=${candidate%-RC*}
            rc=${candidate##*-RC}
            rc=${rc#0}
            printf '%s-rc.%s-SNAPSHOT\n' "$core" "$rc"
            ;;
        *-RC*)
            core=${train%-RC*}
            rc=${train##*-RC}
            rc=${rc#0}
            printf '%s-rc.%s\n' "$core" "$rc"
            ;;
        *) printf '%s\n' "$train" ;;
    esac
}

if sh "$script_dir/check-branch-name.sh" "$source_branch" \
        feature-integration >/dev/null 2>&1; then
    if [ "$target_branch" = main ]; then
        source_train=${source_branch#feature/V}
        expected_version=$(train_to_maven "$source_train")
        if [ "$project_version" != "$expected_version" ]; then
            echo "FAIL: la Pull Request finale $source_branch -> main exige la version Maven $expected_version, reçue : ${project_version:-<vide>}." >&2
            exit 1
        fi
        printf 'GITHUB_PR_POLICY=PASS:final:%s->%s\n' \
            "$source_branch" "$target_branch"
        exit 0
    fi
    echo "FAIL: une branche feature d'intégration doit cibler main, reçu : ${source_branch} -> ${target_branch:-<vide>}." >&2
    exit 1
fi

if sh "$script_dir/check-branch-name.sh" "$source_branch" \
        feature-work-order >/dev/null 2>&1; then
    if ! sh "$script_dir/check-branch-name.sh" "$target_branch" \
            feature-integration >/dev/null 2>&1; then
        echo "FAIL: une branche de Work Order doit cibler feature/<TRAIN>, reçu : ${source_branch} -> ${target_branch:-<vide>}." >&2
        exit 1
    fi

    source_version=$(printf '%s' "$source_branch" | sed -E \
        's#^feature/(V(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-RC(0[1-9]|[1-9][0-9])(-SNAPSHOT)?)?)-(CODEX|HUMAN)-.*$#\1#')
    target_version=${target_branch#feature/}
    if [ "$source_version" != "$target_version" ]; then
        echo "FAIL: les versions source et cible de la Pull Request diffèrent : ${source_version} != ${target_version}." >&2
        exit 1
    fi

    source_train=${source_version#V}
    expected_version=$(train_to_maven "$source_train")
    case "$source_train" in
        *-RC*)
            if [ "$project_version" != "$expected_version" ]; then
                echo "FAIL: la branche de Work Order $source_branch exige la version Maven $expected_version, reçue : ${project_version:-<vide>}." >&2
                exit 1
            fi
            ;;
        *)
            if [ "$project_version" != "$expected_version" ] &&
               [ "$project_version" != "${expected_version}-SNAPSHOT" ]; then
                echo "FAIL: la branche de Work Order $source_branch exige la version Maven $expected_version ou ${expected_version}-SNAPSHOT, reçue : ${project_version:-<vide>}." >&2
                exit 1
            fi
            ;;
    esac

    work_order_suffix=${source_branch#"$target_branch"-}
    case "$work_order_suffix" in
        CODEX-*) work_order_id=${work_order_suffix#CODEX-} ;;
        HUMAN-*) work_order_id=${work_order_suffix#HUMAN-} ;;
        *)
            echo "FAIL: impossible d'extraire le Work Order depuis $source_branch." >&2
            exit 1
            ;;
    esac
    work_order_path="$repository/docs/work-orders/$work_order_id.md"
    if [ ! -f "$work_order_path" ] ||
       ! grep -Eq "^# ${work_order_id}([[:space:]]|$)" "$work_order_path"; then
        echo "FAIL: la branche $source_branch ne référence aucun Work Order versionné valide dans docs/work-orders/$work_order_id.md." >&2
        exit 1
    fi

    printf 'GITHUB_PR_POLICY=PASS:work-order:%s->%s\n' \
        "$source_branch" "$target_branch"
    exit 0
fi

echo "FAIL: route de Pull Request GitHub interdite : ${source_branch:-<vide>} -> ${target_branch:-<vide>}." >&2
exit 1
