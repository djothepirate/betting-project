$ErrorActionPreference = 'Stop'

$repository = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scannerSource = Join-Path $repository 'scripts/check-no-secrets.ps1'
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("betting-secret-scan-{0}" -f [guid]::NewGuid().ToString('N'))
$temporaryPrefix = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar

function Invoke-Git {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & git -C $temporaryRoot @Arguments *> $null
    $gitExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($gitExitCode -ne 0) {
        throw 'Git a echoue pendant le test du controle de secrets.'
    }
}

function Invoke-Scanner {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $temporaryRoot 'scripts/check-no-secrets.ps1') @Arguments 2>&1 | Out-String
    $scannerExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    return @{
        ExitCode = $scannerExitCode
        Output = $output
    }
}

try {
    [void](New-Item -ItemType Directory -Path (Join-Path $temporaryRoot 'scripts') -Force)
    Copy-Item -LiteralPath $scannerSource -Destination (Join-Path $temporaryRoot 'scripts/check-no-secrets.ps1')
    [System.IO.File]::WriteAllText(
        (Join-Path $temporaryRoot 'safe.txt'),
        "documentation sans identifiant sensible`nBETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER`nAuthorization: Bearer test-only-sensitive-value"
    )

    Invoke-Git -Arguments @('init', '--quiet')
    Invoke-Git -Arguments @('config', 'user.name', 'DEVX secret scan test')
    Invoke-Git -Arguments @('config', 'user.email', 'devx-secret-scan@example.invalid')
    Invoke-Git -Arguments @('add', '.')
    Invoke-Git -Arguments @('commit', '--quiet', '-m', 'safe baseline')

    $safeResult = Invoke-Scanner -Arguments @('-Scope', 'All')
    if ($safeResult.ExitCode -ne 0) {
        throw "Le depot temoin sain a ete refuse : $($safeResult.Output)"
    }

    $baseRevision = (& git -C $temporaryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Impossible de determiner la revision de base du test.'
    }

    $indexOnlySecret = (('client_' + 'secret') + '=' + ('Z' * 24))
    [System.IO.File]::WriteAllText((Join-Path $temporaryRoot 'safe.txt'), $indexOnlySecret)
    Invoke-Git -Arguments @('add', 'safe.txt')
    [System.IO.File]::WriteAllText((Join-Path $temporaryRoot 'safe.txt'), 'working tree nettoye sans restager')
    $indexResult = Invoke-Scanner -Arguments @('-Scope', 'GitChanges')
    if ($indexResult.ExitCode -ne 1 -or $indexResult.Output -notmatch 'safe\.txt \[index\]') {
        throw "Un secret present uniquement dans l'index Git n'a pas ete refuse : $($indexResult.Output)"
    }
    if ($indexResult.Output.Contains($indexOnlySecret)) {
        throw "La valeur synthetique presente dans l'index a ete affichee."
    }
    Invoke-Git -Arguments @('restore', '--staged', '--', 'safe.txt')
    Invoke-Git -Arguments @('restore', '--worktree', '--', 'safe.txt')

    $headOnlySecret = (('access_' + 'token') + '=' + ('Y' * 24))
    [System.IO.File]::WriteAllText((Join-Path $temporaryRoot 'safe.txt'), $headOnlySecret)
    Invoke-Git -Arguments @('add', 'safe.txt')
    Invoke-Git -Arguments @('commit', '--quiet', '-m', 'synthetic HEAD finding')
    [System.IO.File]::WriteAllText((Join-Path $temporaryRoot 'safe.txt'), 'working tree nettoye apres le commit')
    $allIndexResult = Invoke-Scanner -Arguments @('-Scope', 'All')
    if ($allIndexResult.ExitCode -ne 1 -or $allIndexResult.Output -notmatch 'safe\.txt \[index\]') {
        throw "Le scan complet a ignore un secret de l'index masque dans le working tree : $($allIndexResult.Output)"
    }
    $changedIndexResult = Invoke-Scanner -Arguments @('-Scope', 'GitChanges')
    if ($changedIndexResult.ExitCode -ne 1 -or $changedIndexResult.Output -notmatch 'safe\.txt \[index\]') {
        throw "Le scan des changements a ignore l'index d'un fichier modifie sans restage : $($changedIndexResult.Output)"
    }
    $headResult = Invoke-Scanner -Arguments @('-Scope', 'GitChanges', '-BaseRef', $baseRevision)
    if ($headResult.ExitCode -ne 1 -or $headResult.Output -notmatch 'safe\.txt \[HEAD\]') {
        throw "Un secret present dans HEAD mais masque dans le working tree n'a pas ete refuse : $($headResult.Output)"
    }
    if ($headResult.Output.Contains($headOnlySecret)) {
        throw 'La valeur synthetique presente dans HEAD a ete affichee.'
    }
    Invoke-Git -Arguments @('add', 'safe.txt')
    Invoke-Git -Arguments @('commit', '--quiet', '-m', 'clean HEAD fixture')
    $baseRevision = (& git -C $temporaryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Impossible de determiner la nouvelle revision de base du test.'
    }

    $historyBaseRevision = $baseRevision
    $historyOnlySecret = (('refresh_' + 'token') + '=' + ('X' * 24))
    $historySecretPath = Join-Path $temporaryRoot 'history-secret.txt'
    [System.IO.File]::WriteAllText($historySecretPath, $historyOnlySecret)
    Invoke-Git -Arguments @('add', '--', 'history-secret.txt')
    Invoke-Git -Arguments @('commit', '--quiet', '-m', 'synthetic historical finding')
    [System.IO.File]::Delete($historySecretPath)
    Invoke-Git -Arguments @('add', '-u', '--', 'history-secret.txt')
    Invoke-Git -Arguments @('commit', '--quiet', '-m', 'remove synthetic historical finding')
    $historyResult = Invoke-Scanner -Arguments @('-Scope', 'All', '-BaseRef', $historyBaseRevision)
    if ($historyResult.ExitCode -ne 1 -or $historyResult.Output -notmatch 'history-secret\.txt \[commit') {
        throw "Un secret ajoute puis retire dans la plage de commits n'a pas ete refuse : $($historyResult.Output)"
    }
    if ($historyResult.Output.Contains($historyOnlySecret)) {
        throw 'La valeur synthetique de l historique Git a ete affichee.'
    }
    Invoke-Git -Arguments @('update-ref', 'refs/remotes/origin/main', $historyBaseRevision)
    $automaticBaseResult = Invoke-Scanner -Arguments @('-Scope', 'All')
    if ($automaticBaseResult.ExitCode -ne 1 -or $automaticBaseResult.Output -notmatch 'history-secret\.txt \[commit') {
        throw "Le repli automatique vers origin/main n'a pas inspecte l historique : $($automaticBaseResult.Output)"
    }
    if ($automaticBaseResult.Output.Contains($historyOnlySecret)) {
        throw 'La valeur synthetique du repli origin/main a ete affichee.'
    }
    $zeroBaseResult = Invoke-Scanner -Arguments @('-Scope', 'All', '-BaseRef', ('0' * 40))
    if ($zeroBaseResult.ExitCode -ne 1 -or $zeroBaseResult.Output -notmatch 'history-secret\.txt \[commit') {
        throw "Le SHA nul d'un premier push n'a pas utilise origin/main : $($zeroBaseResult.Output)"
    }
    if ($zeroBaseResult.Output.Contains($historyOnlySecret)) {
        throw 'La valeur synthetique du repli sur SHA nul a ete affichee.'
    }
    $invalidBaseResult = Invoke-Scanner -Arguments @('-Scope', 'All', '-BaseRef', ('f' * 40))
    if ($invalidBaseResult.ExitCode -ne 2 -or $invalidBaseResult.Output -notmatch 'FAIL: base Git explicite introuvable') {
        throw "Une base explicite invalide n'a pas fait echouer le scan : $($invalidBaseResult.Output)"
    }
    if ($invalidBaseResult.Output -match '(?m)^PASS:') {
        throw 'Une base explicite invalide a produit un faux PASS.'
    }
    Invoke-Git -Arguments @('update-ref', 'refs/remotes/origin/main', 'HEAD')
    $baseRevision = (& git -C $temporaryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Impossible de determiner la base apres le test d historique.'
    }

    $secretValues = [ordered]@{
        'private-key' = ('-----BEGIN ' + 'PRIVATE KEY-----')
        'encrypted-private-key' = ('-----BEGIN ' + 'ENCRYPTED PRIVATE KEY-----')
        'pgp-private-key' = ('-----BEGIN ' + 'PGP PRIVATE KEY BLOCK-----')
        'aws-access-key' = ('AK' + 'IA' + ('A' * 16))
        'github-token' = ('gh' + 'p_' + ('A' * 36))
        'gitlab-token' = ('gl' + 'pat-' + ('A' * 20))
        'google-api-key' = ('AI' + 'za' + ('A' * 35))
        'slack-token' = ('xox' + 'b-' + ('A' * 20))
        'stripe-secret-key' = ('sk_' + 'live_' + ('A' * 24))
        'sendgrid-api-key' = ('S' + 'G.' + ('A' * 20) + '.' + ('B' * 20))
        'jwt' = ('ey' + 'J' + ('A' * 12) + '.' + ('B' * 12) + '.' + ('C' * 12))
        'generic-secret-assignment' = (('client_' + 'secret') + '=' + ('A' * 24))
        'bearer-token' = ('Bearer ' + ('A' * 24))
        'private-sofascore-endpoint' = ('https://www.' + 'sofa' + 'score.com/api/v1/private-test')
    }

    foreach ($entry in $secretValues.GetEnumerator()) {
        [System.IO.File]::WriteAllText((Join-Path $temporaryRoot ("{0}.txt" -f $entry.Key)), $entry.Value)
    }
    [void](New-Item -ItemType Directory -Path (Join-Path $temporaryRoot '.github') -Force)
    [System.IO.File]::WriteAllText(
        (Join-Path $temporaryRoot '.github/hidden-secret.txt'),
        $secretValues['generic-secret-assignment']
    )
    Invoke-Git -Arguments @('add', '.')
    Invoke-Git -Arguments @('commit', '--quiet', '-m', 'synthetic findings')

    $findingResult = Invoke-Scanner -Arguments @('-Scope', 'GitChanges', '-BaseRef', $baseRevision)
    if ($findingResult.ExitCode -ne 1) {
        throw "Le scan devait echouer avec le code 1, code recu : $($findingResult.ExitCode)."
    }

    foreach ($entry in $secretValues.GetEnumerator()) {
        if ($findingResult.Output -notmatch [regex]::Escape($entry.Key)) {
            throw "La regle $($entry.Key) n'a pas ete signalee."
        }
        if ($findingResult.Output.Contains($entry.Value)) {
            throw "Une valeur synthetique a ete affichee par le scanner pour $($entry.Key)."
        }
    }
    if ($findingResult.Output -notmatch [regex]::Escape('.github/hidden-secret.txt')) {
        throw 'Un fichier sensible sous un repertoire pointe doit etre scanne.'
    }

    Write-Output 'PASS: tests PowerShell du controle de secrets.'
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
        if (-not $resolvedTemporaryRoot.StartsWith($temporaryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refus de nettoyer un chemin de test hors du repertoire temporaire.'
        }
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}
