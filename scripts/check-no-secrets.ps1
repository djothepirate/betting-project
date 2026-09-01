[CmdletBinding()]
param(
    [ValidateSet('Repository', 'GitChanges', 'All')]
    [string]$Scope = 'All',

    [string]$BaseRef
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$repository = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$repositoryPrefix = $repository.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar

$excludedPrefixes = @(
    '.git/',
    'target/'
)

$knownPlaceholders = @(
    'TEST_ONLY_PLACEHOLDER',
    'REPLACE_WITH_LOCAL_ONLY_PASSWORD',
    'test-only-sensitive-value'
)

$rules = @(
    @{
        Id = 'private-key'
        Pattern = '(?i)-----BEGIN[ ]+(?:(?:RSA|EC|DSA|OPENSSH|PGP|ENCRYPTED)[ ]+)?PRIVATE[ ]+KEY(?:[ ]+BLOCK)?-----'
    },
    @{
        Id = 'aws-access-key'
        Pattern = '(?<![A-Z0-9])(?:AKIA|ASIA)[A-Z0-9]{16}(?![A-Z0-9])'
    },
    @{
        Id = 'github-token'
        Pattern = '(?<![A-Za-z0-9_])(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{80,255})(?![A-Za-z0-9_])'
    },
    @{
        Id = 'gitlab-token'
        Pattern = '(?<![A-Za-z0-9_-])glpat-[A-Za-z0-9_-]{20,255}(?![A-Za-z0-9_-])'
    },
    @{
        Id = 'google-api-key'
        Pattern = '(?<![A-Za-z0-9_-])AIza[A-Za-z0-9_-]{35}(?![A-Za-z0-9_-])'
    },
    @{
        Id = 'slack-token'
        Pattern = '(?<![A-Za-z0-9-])xox[baprs]-[A-Za-z0-9-]{10,255}(?![A-Za-z0-9-])'
    },
    @{
        Id = 'stripe-secret-key'
        Pattern = '(?<![A-Za-z0-9_])sk_(?:live|test)_[A-Za-z0-9]{16,255}(?![A-Za-z0-9])'
    },
    @{
        Id = 'sendgrid-api-key'
        Pattern = '(?<![A-Za-z0-9_.-])SG\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}(?![A-Za-z0-9_.-])'
    },
    @{
        Id = 'jwt'
        Pattern = '(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}(?![A-Za-z0-9_-])'
    },
    @{
        Id = 'generic-secret-assignment'
        Pattern = '(?i)(?:api[_-]?key|authorization|client[_-]?secret|access[_-]?token|refresh[_-]?token|password|passwd|private[_-]?key)\s*[:=]\s*["'']?[A-Za-z0-9+/_=.-]{20,}'
    },
    @{
        Id = 'bearer-token'
        Pattern = '(?i)\bBearer[ \t]+[A-Za-z0-9+/_=.-]{20,}'
    },
    @{
        Id = 'private-sofascore-endpoint'
        Pattern = '(?i)https?://[^\s/]*sofascore[^\s]*api[^\s]*'
    }
)

function Invoke-GitForOutput {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & git -C $repository @Arguments 2>$null
    $gitExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($gitExitCode -ne 0) {
        throw 'La commande Git a echoue pendant le controle de secrets.'
    }
    return @($output)
}

function Test-GitRevision {
    param([string]$Revision)

    if ([string]::IsNullOrWhiteSpace($Revision) -or $Revision -match '^0+$') {
        return $false
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & git -C $repository cat-file -e ("{0}^{{commit}}" -f $Revision) 2>$null
    $gitExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    return $gitExitCode -eq 0
}

function Get-RepositoryCandidates {
    Invoke-GitForOutput -Arguments @('-c', 'core.quotepath=false', 'ls-files', '--cached', '--others', '--exclude-standard')
}

function Get-GitChangeCandidates {
    $candidates = New-Object System.Collections.Generic.List[string]

    if (-not [string]::IsNullOrWhiteSpace($BaseRef)) {
        if (Test-GitRevision -Revision $BaseRef) {
            Invoke-GitForOutput -Arguments @(
                '-c', 'core.quotepath=false', 'diff', '--name-only', '--diff-filter=ACMR', ("{0}...HEAD" -f $BaseRef), '--'
            ) | ForEach-Object { $candidates.Add($_) }
        } else {
            throw 'La base Git est devenue indisponible pendant le controle de secrets.'
        }
    }

    Invoke-GitForOutput -Arguments @('-c', 'core.quotepath=false', 'diff', '--cached', '--name-only', '--diff-filter=ACMR', '--') |
        ForEach-Object { $candidates.Add($_) }
    Invoke-GitForOutput -Arguments @('-c', 'core.quotepath=false', 'diff', '--name-only', '--diff-filter=ACMR', '--') |
        ForEach-Object { $candidates.Add($_) }
    Invoke-GitForOutput -Arguments @('-c', 'core.quotepath=false', 'ls-files', '--others', '--exclude-standard') |
        ForEach-Object { $candidates.Add($_) }

    return $candidates
}

function Resolve-Candidate {
    param(
        [string]$RelativePath,
        [switch]$AllowMissing
    )

    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        return $null
    }

    $normalized = $RelativePath.Replace('\', '/')
    while ($normalized.StartsWith('./', [System.StringComparison]::Ordinal)) {
        $normalized = $normalized.Substring(2)
    }
    foreach ($prefix in $excludedPrefixes) {
        if ($normalized.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $null
        }
    }

    $fullPath = [System.IO.Path]::GetFullPath((Join-Path $repository $normalized))
    if (-not $fullPath.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Chemin Git hors depot refuse pendant le controle de secrets.'
    }
    if (-not $AllowMissing -and -not [System.IO.File]::Exists($fullPath)) {
        return $null
    }

    return @{
        RelativePath = $normalized
        FullPath = $fullPath
    }
}

function Get-GitBlobContent {
    param([string]$ObjectSpec)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $blobLines = & git -C $repository cat-file blob $ObjectSpec 2>$null
    $gitExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($gitExitCode -ne 0) {
        return @{ Found = $false; Content = '' }
    }

    return @{ Found = $true; Content = (@($blobLines) -join "`n") }
}

& git -C $repository rev-parse --is-inside-work-tree 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Le controle de secrets doit etre execute dans un depot Git.'
}

$baseRefIsOptional = [string]::IsNullOrWhiteSpace($BaseRef) -or $BaseRef -match '^0+$'
if ($baseRefIsOptional) {
    if (Test-GitRevision -Revision 'origin/main') {
        $BaseRef = 'origin/main'
    } else {
        $BaseRef = $null
    }
} elseif (-not (Test-GitRevision -Revision $BaseRef)) {
    [Console]::Error.WriteLine('FAIL: base Git explicite introuvable pendant le controle de secrets.')
    exit 2
}

$candidatePaths = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
if ($Scope -eq 'Repository' -or $Scope -eq 'All') {
    Get-RepositoryCandidates | ForEach-Object { [void]$candidatePaths.Add($_) }
}
if ($Scope -eq 'GitChanges' -or $Scope -eq 'All') {
    Get-GitChangeCandidates | ForEach-Object { [void]$candidatePaths.Add($_) }
}

$findings = @{}
function Test-ContentForSecrets {
    param(
        [string]$DisplayPath,
        [string]$Content
    )

    if ($null -eq $Content) {
        return
    }
    if ($content.IndexOf([char]0) -ge 0) {
        return
    }

    foreach ($rule in $rules) {
        $contentToInspect = $content
        if ($rule.Id -eq 'generic-secret-assignment' -or $rule.Id -eq 'bearer-token') {
            foreach ($placeholder in $knownPlaceholders) {
                $contentToInspect = $contentToInspect.Replace($placeholder, 'placeholder')
            }
        }

        if ([System.Text.RegularExpressions.Regex]::IsMatch($contentToInspect, $rule.Pattern)) {
            if (-not $findings.ContainsKey($DisplayPath)) {
                $findings[$DisplayPath] = New-Object System.Collections.Generic.List[string]
            }
            $findings[$DisplayPath].Add($rule.Id)
        }
    }
}

foreach ($candidatePath in $candidatePaths) {
    $candidate = Resolve-Candidate -RelativePath $candidatePath
    if ($null -eq $candidate) {
        continue
    }

    try {
        $content = [System.IO.File]::ReadAllText($candidate.FullPath)
    } catch [System.Text.DecoderFallbackException] {
        continue
    } catch [System.IO.IOException] {
        throw "Impossible de lire un fichier candidat pendant le controle de secrets : $($candidate.RelativePath)"
    }

    Test-ContentForSecrets -DisplayPath $candidate.RelativePath -Content $content
}

$indexCandidatePaths = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
if ($Scope -eq 'Repository' -or $Scope -eq 'All') {
    Invoke-GitForOutput -Arguments @('-c', 'core.quotepath=false', 'ls-files', '--cached') |
        ForEach-Object { [void]$indexCandidatePaths.Add($_) }
} else {
    Invoke-GitForOutput -Arguments @(
        '-c', 'core.quotepath=false', 'diff', '--cached', '--name-only', '--diff-filter=ACMR', '--'
    ) | ForEach-Object { [void]$indexCandidatePaths.Add($_) }
    Invoke-GitForOutput -Arguments @(
        '-c', 'core.quotepath=false', 'diff', '--name-only', '--diff-filter=ACMR', '--'
    ) | ForEach-Object { [void]$indexCandidatePaths.Add($_) }
}

foreach ($candidatePath in $indexCandidatePaths) {
    $candidate = Resolve-Candidate -RelativePath $candidatePath -AllowMissing
    if ($null -eq $candidate) {
        continue
    }
    $blob = Get-GitBlobContent -ObjectSpec (":{0}" -f $candidate.RelativePath)
    if ($blob.Found) {
        Test-ContentForSecrets -DisplayPath ("{0} [index]" -f $candidate.RelativePath) -Content $blob.Content
    }
}

if ($Scope -eq 'GitChanges' -or $Scope -eq 'All') {
    if (Test-GitRevision -Revision $BaseRef) {
        $headCandidates = Invoke-GitForOutput -Arguments @(
            '-c', 'core.quotepath=false', 'diff', '--name-only', '--diff-filter=ACMR', ("{0}...HEAD" -f $BaseRef), '--'
        )
        foreach ($candidatePath in $headCandidates) {
            $candidate = Resolve-Candidate -RelativePath $candidatePath -AllowMissing
            if ($null -eq $candidate) {
                continue
            }
            $blob = Get-GitBlobContent -ObjectSpec ("HEAD:{0}" -f $candidate.RelativePath)
            if ($blob.Found) {
                Test-ContentForSecrets -DisplayPath ("{0} [HEAD]" -f $candidate.RelativePath) -Content $blob.Content
            }
        }

        $rangeCommits = Invoke-GitForOutput -Arguments @('rev-list', '--reverse', ("{0}..HEAD" -f $BaseRef))
        foreach ($commit in $rangeCommits) {
            if ([string]::IsNullOrWhiteSpace($commit)) {
                continue
            }
            $commitCandidatePaths = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)
            Invoke-GitForOutput -Arguments @(
                '-c', 'core.quotepath=false', 'diff-tree', '--root', '-m', '--no-commit-id', '--name-only', '-r',
                '--diff-filter=ACMR', $commit, '--'
            ) | ForEach-Object { [void]$commitCandidatePaths.Add($_) }

            $shortCommit = $commit.Substring(0, [Math]::Min(12, $commit.Length))
            foreach ($candidatePath in $commitCandidatePaths) {
                $candidate = Resolve-Candidate -RelativePath $candidatePath -AllowMissing
                if ($null -eq $candidate) {
                    continue
                }
                $blob = Get-GitBlobContent -ObjectSpec ("{0}:{1}" -f $commit, $candidate.RelativePath)
                if ($blob.Found) {
                    Test-ContentForSecrets `
                        -DisplayPath ("{0} [commit {1}]" -f $candidate.RelativePath, $shortCommit) `
                        -Content $blob.Content
                }
            }
        }
    }
}

if ($findings.Count -gt 0) {
    foreach ($path in ($findings.Keys | Sort-Object)) {
        $ruleIds = $findings[$path] | Sort-Object -Unique
        [Console]::Error.WriteLine(("FAIL: valeur sensible potentielle ({0}) dans {1}." -f ($ruleIds -join ','), $path))
    }
    exit 1
}

Write-Output ("PASS: aucun secret a forte confiance ni endpoint prive SofaScore detecte ({0})." -f $Scope)
