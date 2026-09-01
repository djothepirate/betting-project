#!/usr/bin/env sh
set -eu

usage() {
    echo 'Usage: check-no-secrets.sh [--scope repository|git-changes|all] [--base REVISION]' >&2
    exit 2
}

scope=all
base_ref=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --scope)
            [ "$#" -ge 2 ] || usage
            scope=$2
            shift 2
            ;;
        --base)
            [ "$#" -ge 2 ] || usage
            base_ref=$2
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

case "$scope" in
    repository|git-changes|all) ;;
    *) usage ;;
esac

script_repository=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
repository=$(git -C "$script_repository" rev-parse --show-toplevel 2>/dev/null) || {
    echo 'FAIL: le contrôle de secrets doit être exécuté dans un dépôt Git.' >&2
    exit 2
}

base_ref_missing=0
if [ -z "$base_ref" ] || printf '%s' "$base_ref" | grep -Eq '^0+$'; then
    base_ref_missing=1
fi
if [ "$base_ref_missing" -eq 1 ]; then
    if git -C "$repository" cat-file -e 'origin/main^{commit}' 2>/dev/null; then
        base_ref=origin/main
    else
        base_ref=
    fi
elif ! git -C "$repository" cat-file -e "${base_ref}^{commit}" 2>/dev/null; then
    echo 'FAIL: base Git explicite introuvable pendant le contrôle de secrets.' >&2
    exit 2
fi

candidate_file=$(mktemp "${TMPDIR:-/tmp}/betting-secret-candidates.XXXXXX")
unique_file=$(mktemp "${TMPDIR:-/tmp}/betting-secret-unique.XXXXXX")
blob_candidates=$(mktemp "${TMPDIR:-/tmp}/betting-secret-blob-candidates.XXXXXX")
blob_file=$(mktemp "${TMPDIR:-/tmp}/betting-secret-blob.XXXXXX")
commit_file=$(mktemp "${TMPDIR:-/tmp}/betting-secret-commits.XXXXXX")
trap 'rm -f "$candidate_file" "$unique_file" "$blob_candidates" "$blob_file" "$commit_file"' EXIT HUP INT TERM

list_ignored_repository_candidates() {
    # Liste positive : couvrir les sorties locales sans élargir le scan aux
    # autres fichiers ignorés.
    git -C "$repository" -c core.quotepath=false ls-files \
        --others --ignored --exclude-standard -- \
        ':(glob)**/*.log' \
        ':(glob)**/reports/**' \
        ':(glob)**/*.raw.json' \
        ':(glob)**/*.metadata.json' \
        ':(glob)**/*.replay.json'
}

list_repository_candidates() {
    git -C "$repository" -c core.quotepath=false ls-files --cached --others --exclude-standard
    list_ignored_repository_candidates
}

list_git_change_candidates() {
    if [ -n "$base_ref" ] && ! printf '%s' "$base_ref" | grep -Eq '^0+$'; then
        if git -C "$repository" cat-file -e "${base_ref}^{commit}" 2>/dev/null; then
            git -C "$repository" -c core.quotepath=false diff --name-only --diff-filter=ACMR "${base_ref}...HEAD" --
        else
            echo 'FAIL: la base Git est devenue indisponible pendant le contrôle de secrets.' >&2
            exit 2
        fi
    fi

    git -C "$repository" -c core.quotepath=false diff --cached --name-only --diff-filter=ACMR --
    git -C "$repository" -c core.quotepath=false diff --name-only --diff-filter=ACMR --
    git -C "$repository" -c core.quotepath=false ls-files --others --exclude-standard
}

case "$scope" in
    repository)
        list_repository_candidates >"$candidate_file"
        ;;
    git-changes)
        list_git_change_candidates >"$candidate_file"
        ;;
    all)
        {
            list_repository_candidates
            list_git_change_candidates
        } >"$candidate_file"
        ;;
esac

LC_ALL=C sort -u "$candidate_file" >"$unique_file"

private_key_pattern='-----BEGIN[[:space:]]+((RSA|EC|DSA|OPENSSH|PGP|ENCRYPTED)[[:space:]]+)?PRIVATE[[:space:]]+KEY([[:space:]]+BLOCK)?-----'
aws_pattern='(^|[^A-Z0-9])(AKIA|ASIA)[A-Z0-9]{16}([^A-Z0-9]|$)'
github_pattern='(^|[^A-Za-z0-9_])(gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{80,255})([^A-Za-z0-9_]|$)'
gitlab_pattern='(^|[^A-Za-z0-9_-])glpat-[A-Za-z0-9_-]{20,255}([^A-Za-z0-9_-]|$)'
google_pattern='(^|[^A-Za-z0-9_-])AIza[A-Za-z0-9_-]{35}([^A-Za-z0-9_-]|$)'
slack_pattern='(^|[^A-Za-z0-9-])xox[baprs]-[A-Za-z0-9-]{10,255}([^A-Za-z0-9-]|$)'
stripe_pattern='(^|[^A-Za-z0-9_])sk_(live|test)_[A-Za-z0-9]{16,255}([^A-Za-z0-9]|$)'
sendgrid_pattern='(^|[^A-Za-z0-9_.-])SG\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}([^A-Za-z0-9_.-]|$)'
jwt_pattern='(^|[^A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}([^A-Za-z0-9_-]|$)'
generic_pattern="(api[_-]?key|authorization|client[_-]?secret|access[_-]?token|refresh[_-]?token|password|passwd|private[_-]?key)[[:space:]]*[:=][[:space:]]*[\"']?[A-Za-z0-9+/_=.-]{20,}"
bearer_pattern='Bearer[[:space:]]+[A-Za-z0-9+/_=.-]{20,}'
sofascore_pattern='https?://[^[:space:]/]*sofascore[^[:space:]]*api[^[:space:]]*'

findings=0
scan_content_file() {
    content_path=$1
    display_path=$2
    matched_rules=
    for rule_definition in \
        "private-key:$private_key_pattern" \
        "aws-access-key:$aws_pattern" \
        "github-token:$github_pattern" \
        "gitlab-token:$gitlab_pattern" \
        "google-api-key:$google_pattern" \
        "slack-token:$slack_pattern" \
        "stripe-secret-key:$stripe_pattern" \
        "sendgrid-api-key:$sendgrid_pattern" \
        "jwt:$jwt_pattern" \
        "generic-secret-assignment:$generic_pattern" \
        "bearer-token:$bearer_pattern" \
        "private-sofascore-endpoint:$sofascore_pattern"
    do
        rule_id=${rule_definition%%:*}
        rule_pattern=${rule_definition#*:}
        if [ "$rule_id" = generic-secret-assignment ] || [ "$rule_id" = bearer-token ]; then
            if sed \
                -e 's/TEST_ONLY_PLACEHOLDER/placeholder/g' \
                -e 's/REPLACE_WITH_LOCAL_ONLY_PASSWORD/placeholder/g' \
                -e 's/test-only-sensitive-value/placeholder/g' \
                "$content_path" 2>/dev/null | LC_ALL=C grep -Eiq -e "$rule_pattern"; then
                rule_matches=1
            else
                rule_matches=0
            fi
        elif LC_ALL=C grep -Eiq -e "$rule_pattern" "$content_path" 2>/dev/null; then
            rule_matches=1
        else
            rule_matches=0
        fi

        if [ "$rule_matches" -eq 1 ]; then
            if [ -z "$matched_rules" ]; then
                matched_rules=$rule_id
            else
                matched_rules=$matched_rules,$rule_id
            fi
        fi
    done

    if [ -n "$matched_rules" ]; then
        printf 'FAIL: valeur sensible potentielle (%s) dans %s.\n' "$matched_rules" "$display_path" >&2
        findings=1
    fi
}

while IFS= read -r relative_path; do
    [ -n "$relative_path" ] || continue
    case "$relative_path" in
        .git/*|target/*)
            continue
            ;;
    esac

    candidate_path=$repository/$relative_path
    [ -f "$candidate_path" ] || continue
    scan_content_file "$candidate_path" "$relative_path"
done <"$unique_file"

case "$scope" in
    repository|all)
        git -C "$repository" -c core.quotepath=false ls-files --cached >"$blob_candidates"
        ;;
    git-changes)
        {
            git -C "$repository" -c core.quotepath=false diff --cached --name-only --diff-filter=ACMR --
            git -C "$repository" -c core.quotepath=false diff --name-only --diff-filter=ACMR --
        } | LC_ALL=C sort -u >"$blob_candidates"
        ;;
esac

while IFS= read -r relative_path; do
    [ -n "$relative_path" ] || continue
    case "$relative_path" in
        .git/*|target/*)
            continue
            ;;
    esac
    if git -C "$repository" cat-file blob ":$relative_path" >"$blob_file" 2>/dev/null; then
        scan_content_file "$blob_file" "$relative_path [index]"
    fi
done <"$blob_candidates"

if [ "$scope" = git-changes ] || [ "$scope" = all ]; then
    if [ -n "$base_ref" ] \
        && ! printf '%s' "$base_ref" | grep -Eq '^0+$' \
        && git -C "$repository" cat-file -e "${base_ref}^{commit}" 2>/dev/null; then
        git -C "$repository" -c core.quotepath=false diff --name-only --diff-filter=ACMR "${base_ref}...HEAD" -- >"$blob_candidates"
        while IFS= read -r relative_path; do
            [ -n "$relative_path" ] || continue
            case "$relative_path" in
                .git/*|target/*)
                    continue
                    ;;
            esac
            if git -C "$repository" cat-file blob "HEAD:$relative_path" >"$blob_file" 2>/dev/null; then
                scan_content_file "$blob_file" "$relative_path [HEAD]"
            fi
        done <"$blob_candidates"

        git -C "$repository" rev-list --reverse "${base_ref}..HEAD" >"$commit_file"
        while IFS= read -r commit; do
            [ -n "$commit" ] || continue
            git -C "$repository" -c core.quotepath=false diff-tree --root -m --no-commit-id --name-only -r \
                --diff-filter=ACMR "$commit" -- | LC_ALL=C sort -u >"$blob_candidates"
            short_commit=$(printf '%.12s' "$commit")
            while IFS= read -r relative_path; do
                [ -n "$relative_path" ] || continue
                case "$relative_path" in
                    .git/*|target/*)
                        continue
                        ;;
                esac
                if git -C "$repository" cat-file blob "$commit:$relative_path" >"$blob_file" 2>/dev/null; then
                    scan_content_file "$blob_file" "$relative_path [commit $short_commit]"
                fi
            done <"$blob_candidates"
        done <"$commit_file"
    fi
fi

if [ "$findings" -ne 0 ]; then
    exit 1
fi

printf 'PASS: aucun secret à forte confiance ni endpoint privé SofaScore détecté (%s).\n' "$scope"
