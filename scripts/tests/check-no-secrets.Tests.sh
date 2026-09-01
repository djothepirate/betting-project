#!/usr/bin/env sh
set -eu

repository=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
temporary_base=$(CDPATH= cd -- "${TMPDIR:-/tmp}" && pwd)
test_root=$(mktemp -d "$temporary_base/betting-secret-scan.XXXXXX")

cleanup() {
    case "$test_root" in
        "$temporary_base"/betting-secret-scan.*)
            if [ -d "$test_root" ]; then
                rm -rf "$test_root"
            fi
            ;;
        *)
            echo 'WARN: refus de nettoyer un chemin de test inattendu.' >&2
            ;;
    esac
}

trap cleanup EXIT HUP INT TERM

mkdir "$test_root/scripts"
cp "$repository/scripts/check-no-secrets.sh" "$test_root/scripts/check-no-secrets.sh"
printf '%s\n' \
    'documentation sans identifiant sensible' \
    'BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER' \
    'Authorization: Bearer test-only-sensitive-value' >"$test_root/safe.txt"
printf '%s\n' '*.log' 'reports/' 'secrets/' '.env' >"$test_root/.gitignore"

git -C "$test_root" init --quiet
git -C "$test_root" config user.name 'DEVX secret scan test'
git -C "$test_root" config user.email 'devx-secret-scan@example.invalid'
git -C "$test_root" config core.autocrlf false
git -C "$test_root" add .
git -C "$test_root" commit --quiet -m 'safe baseline'

sh "$test_root/scripts/check-no-secrets.sh" --scope all >/dev/null
base_revision=$(git -C "$test_root" rev-parse HEAD)

ignored_log_secret='client_''secret='$(printf '%024d' 0 | tr 0 L)
ignored_report_secret='access_''token='$(printf '%024d' 0 | tr 0 R)
protected_local_secret='refresh_''token='$(printf '%024d' 0 | tr 0 P)
mkdir -p "$test_root/runtime" "$test_root/reports/nested" "$test_root/secrets"
printf '%s' "$ignored_log_secret" >"$test_root/runtime/application.log"
printf '%s' "$ignored_report_secret" >"$test_root/reports/nested/result.txt"
printf '%s' "$protected_local_secret" >"$test_root/secrets/synthetic-local-key.txt"
git -C "$test_root" check-ignore --quiet -- runtime/application.log
git -C "$test_root" check-ignore --quiet -- reports/nested/result.txt

for ignored_scope in repository all
do
    if ignored_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope "$ignored_scope" 2>&1); then
        printf 'FAIL: le scope %s a ignoré des artefacts locaux sensibles.\n' "$ignored_scope" >&2
        exit 1
    fi
    for expected_ignored_path in runtime/application.log reports/nested/result.txt
    do
        case "$ignored_output" in
            *"$expected_ignored_path"*) ;;
            *)
                printf 'FAIL: le scope %s n\047a pas signalé %s.\n' "$ignored_scope" "$expected_ignored_path" >&2
                exit 1
                ;;
        esac
    done
    for ignored_secret in "$ignored_log_secret" "$ignored_report_secret" "$protected_local_secret"
    do
        case "$ignored_output" in
            *"$ignored_secret"*)
                printf 'FAIL: le scope %s a affiché une valeur synthétique ignorée.\n' "$ignored_scope" >&2
                exit 1
                ;;
        esac
    done
    case "$ignored_output" in
        *'synthetic-local-key.txt'*)
            printf 'FAIL: le scope %s a lu un fichier local protégé.\n' "$ignored_scope" >&2
            exit 1
            ;;
    esac
done

rm "$test_root/runtime/application.log" "$test_root/reports/nested/result.txt"
rmdir "$test_root/runtime" "$test_root/reports/nested" "$test_root/reports"
for protected_scope in repository all
do
    if ! protected_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope "$protected_scope" 2>&1); then
        printf 'FAIL: le scope %s a refusé le dépôt après retrait des artefacts : %s\n' \
            "$protected_scope" "$protected_output" >&2
        exit 1
    fi
done

index_only_secret='client_''secret='$(printf '%024d' 0 | tr 0 Z)
printf '%s' "$index_only_secret" >"$test_root/safe.txt"
git -C "$test_root" add safe.txt
printf '%s' 'working tree nettoye sans restager' >"$test_root/safe.txt"
if index_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope git-changes 2>&1); then
    echo 'FAIL: un secret présent uniquement dans l’index Git a été accepté.' >&2
    exit 1
fi
case "$index_output" in
    *'safe.txt [index]'*) ;;
    *)
        echo 'FAIL: le contenu sensible de l’index Git n’a pas été identifié.' >&2
        exit 1
        ;;
esac
case "$index_output" in
    *"$index_only_secret"*)
        echo 'FAIL: la valeur synthétique présente dans l’index a été affichée.' >&2
        exit 1
        ;;
esac
git -C "$test_root" restore --staged -- safe.txt
git -C "$test_root" restore --worktree -- safe.txt

head_only_secret='access_''token='$(printf '%024d' 0 | tr 0 Y)
printf '%s' "$head_only_secret" >"$test_root/safe.txt"
git -C "$test_root" add safe.txt
git -C "$test_root" commit --quiet -m 'synthetic HEAD finding'
printf '%s' 'working tree nettoye apres le commit' >"$test_root/safe.txt"
if all_index_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope all 2>&1); then
    echo 'FAIL: le scan complet a ignoré un secret de l’index masqué dans le working tree.' >&2
    exit 1
fi
case "$all_index_output" in
    *'safe.txt [index]'*) ;;
    *)
        echo 'FAIL: le scan complet n’a pas identifié le blob de l’index.' >&2
        exit 1
        ;;
esac
if changed_index_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope git-changes 2>&1); then
    echo 'FAIL: le scan des changements a ignoré l’index d’un fichier modifié sans restage.' >&2
    exit 1
fi
case "$changed_index_output" in
    *'safe.txt [index]'*) ;;
    *)
        echo 'FAIL: le scan des changements n’a pas identifié le blob de l’index divergent.' >&2
        exit 1
        ;;
esac
if head_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope git-changes --base "$base_revision" 2>&1); then
    echo 'FAIL: un secret présent dans HEAD mais masqué dans le working tree a été accepté.' >&2
    exit 1
fi
case "$head_output" in
    *'safe.txt [HEAD]'*) ;;
    *)
        echo 'FAIL: le contenu sensible de HEAD n’a pas été identifié.' >&2
        exit 1
        ;;
esac
case "$head_output" in
    *"$head_only_secret"*)
        echo 'FAIL: la valeur synthétique présente dans HEAD a été affichée.' >&2
        exit 1
        ;;
esac
git -C "$test_root" add safe.txt
git -C "$test_root" commit --quiet -m 'clean HEAD fixture'
base_revision=$(git -C "$test_root" rev-parse HEAD)

history_base_revision=$base_revision
history_only_secret='refresh_''token='$(printf '%024d' 0 | tr 0 X)
printf '%s' "$history_only_secret" >"$test_root/history-secret.txt"
git -C "$test_root" add -- history-secret.txt
git -C "$test_root" commit --quiet -m 'synthetic historical finding'
rm "$test_root/history-secret.txt"
git -C "$test_root" add -u -- history-secret.txt
git -C "$test_root" commit --quiet -m 'remove synthetic historical finding'
if history_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope all --base "$history_base_revision" 2>&1); then
    echo 'FAIL: un secret ajouté puis retiré dans la plage de commits a été accepté.' >&2
    exit 1
fi
case "$history_output" in
    *'history-secret.txt [commit '*) ;;
    *)
        echo 'FAIL: la version sensible d’un commit intermédiaire n’a pas été identifiée.' >&2
        exit 1
        ;;
esac
case "$history_output" in
    *"$history_only_secret"*)
        echo 'FAIL: la valeur synthétique de l’historique Git a été affichée.' >&2
        exit 1
        ;;
esac
git -C "$test_root" update-ref refs/remotes/origin/main "$history_base_revision"
if automatic_base_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope all 2>&1); then
    echo 'FAIL: le repli automatique vers origin/main n’a pas inspecté l’historique.' >&2
    exit 1
fi
case "$automatic_base_output" in
    *'history-secret.txt [commit '*) ;;
    *)
        echo 'FAIL: la base origin/main automatique n’a pas identifié le commit sensible.' >&2
        exit 1
        ;;
esac
case "$automatic_base_output" in
    *"$history_only_secret"*)
        echo 'FAIL: la valeur synthétique du repli origin/main a été affichée.' >&2
        exit 1
        ;;
esac
zero_base=$(printf '%040d' 0)
if zero_base_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope all --base "$zero_base" 2>&1); then
    echo 'FAIL: le SHA nul d’un premier push n’a pas utilisé origin/main.' >&2
    exit 1
fi
case "$zero_base_output" in
    *'history-secret.txt [commit '*) ;;
    *)
        echo 'FAIL: le repli du SHA nul n’a pas identifié le commit sensible.' >&2
        exit 1
        ;;
esac
case "$zero_base_output" in
    *"$history_only_secret"*)
        echo 'FAIL: la valeur synthétique du repli sur SHA nul a été affichée.' >&2
        exit 1
        ;;
esac
invalid_base=$(printf '%040d' 0 | tr 0 f)
if invalid_base_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope all --base "$invalid_base" 2>&1); then
    echo 'FAIL: une base explicite invalide a été acceptée.' >&2
    exit 1
else
    invalid_base_code=$?
fi
if [ "$invalid_base_code" -ne 2 ]; then
    printf 'FAIL: une base explicite invalide devait retourner 2, reçu %s.\n' "$invalid_base_code" >&2
    exit 1
fi
case "$invalid_base_output" in
    *'FAIL: base Git explicite introuvable'*) ;;
    *)
        echo 'FAIL: le diagnostic de base Git invalide est absent.' >&2
        exit 1
        ;;
esac
case "$invalid_base_output" in
    *'PASS:'*)
        echo 'FAIL: une base explicite invalide a produit un faux PASS.' >&2
        exit 1
        ;;
esac
git -C "$test_root" update-ref refs/remotes/origin/main HEAD
base_revision=$(git -C "$test_root" rev-parse HEAD)

repeat_a_16=$(printf '%016d' 0 | tr 0 A)
repeat_a_20=$(printf '%020d' 0 | tr 0 A)
repeat_a_24=$(printf '%024d' 0 | tr 0 A)
repeat_a_35=$(printf '%035d' 0 | tr 0 A)
repeat_a_36=$(printf '%036d' 0 | tr 0 A)
repeat_b_12=$(printf '%012d' 0 | tr 0 B)
repeat_b_20=$(printf '%020d' 0 | tr 0 B)
repeat_c_12=$(printf '%012d' 0 | tr 0 C)

private_key='-----BEGIN ''PRIVATE KEY-----'
encrypted_private_key='-----BEGIN ''ENCRYPTED PRIVATE KEY-----'
pgp_private_key='-----BEGIN ''PGP PRIVATE KEY BLOCK-----'
aws_access_key='AK''IA'"$repeat_a_16"
github_token='gh''p_'"$repeat_a_36"
gitlab_token='gl''pat-'"$repeat_a_20"
google_api_key='AI''za'"$repeat_a_35"
slack_token='xox''b-'"$repeat_a_20"
stripe_secret_key='sk_''live_'"$repeat_a_24"
sendgrid_api_key='S''G.'"$repeat_a_20.$repeat_b_20"
jwt_token='ey''J'"$repeat_a_20.$repeat_b_12.$repeat_c_12"
generic_secret='client_''secret='"$repeat_a_24"
bearer_token='Bearer '"$repeat_a_24"
sofascore_endpoint='https://www.''sofa''score.com/api/v1/private-test'

printf '%s' "$private_key" >"$test_root/private-key.txt"
printf '%s' "$encrypted_private_key" >"$test_root/encrypted-private-key.txt"
printf '%s' "$pgp_private_key" >"$test_root/pgp-private-key.txt"
printf '%s' "$aws_access_key" >"$test_root/aws-access-key.txt"
printf '%s' "$github_token" >"$test_root/github-token.txt"
printf '%s' "$gitlab_token" >"$test_root/gitlab-token.txt"
printf '%s' "$google_api_key" >"$test_root/google-api-key.txt"
printf '%s' "$slack_token" >"$test_root/slack-token.txt"
printf '%s' "$stripe_secret_key" >"$test_root/stripe-secret-key.txt"
printf '%s' "$sendgrid_api_key" >"$test_root/sendgrid-api-key.txt"
printf '%s' "$jwt_token" >"$test_root/jwt.txt"
printf '%s' "$generic_secret" >"$test_root/generic-secret-assignment.txt"
printf '%s' "$bearer_token" >"$test_root/bearer-token.txt"
printf '%s' "$sofascore_endpoint" >"$test_root/private-sofascore-endpoint.txt"
mkdir "$test_root/.github"
printf '%s' "$generic_secret" >"$test_root/.github/hidden-secret.txt"

git -C "$test_root" add .
git -C "$test_root" commit --quiet -m 'synthetic findings'

if finding_output=$(sh "$test_root/scripts/check-no-secrets.sh" --scope git-changes --base "$base_revision" 2>&1); then
    echo 'FAIL: le scan devait refuser les valeurs synthétiques.' >&2
    exit 1
fi

for rule_id in \
    private-key \
    aws-access-key \
    github-token \
    gitlab-token \
    google-api-key \
    slack-token \
    stripe-secret-key \
    sendgrid-api-key \
    jwt \
    generic-secret-assignment \
    bearer-token \
    private-sofascore-endpoint
do
    case "$finding_output" in
        *"$rule_id"*) ;;
        *)
            printf 'FAIL: la règle %s n\047a pas été signalée.\n' "$rule_id" >&2
            exit 1
            ;;
    esac
done

case "$finding_output" in
    *'.github/hidden-secret.txt'*) ;;
    *)
        echo 'FAIL: un fichier sensible sous un répertoire pointé doit être scanné.' >&2
        exit 1
        ;;
esac

for expected_private_key_path in encrypted-private-key.txt pgp-private-key.txt
do
    case "$finding_output" in
        *"$expected_private_key_path"*) ;;
        *)
            printf 'FAIL: le format de clé privée %s n\047a pas été signalé.\n' "$expected_private_key_path" >&2
            exit 1
            ;;
    esac
done

for secret_value in \
    "$private_key" \
    "$encrypted_private_key" \
    "$pgp_private_key" \
    "$aws_access_key" \
    "$github_token" \
    "$gitlab_token" \
    "$google_api_key" \
    "$slack_token" \
    "$stripe_secret_key" \
    "$sendgrid_api_key" \
    "$jwt_token" \
    "$generic_secret" \
    "$bearer_token" \
    "$sofascore_endpoint"
do
    case "$finding_output" in
        *"$secret_value"*)
            echo 'FAIL: le scanner a affiché une valeur synthétique.' >&2
            exit 1
            ;;
    esac
done

echo 'PASS: tests POSIX du contrôle de secrets.'
