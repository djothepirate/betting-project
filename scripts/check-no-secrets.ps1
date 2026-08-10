$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$repository = Split-Path -Parent $PSScriptRoot
$excluded = @(
    (Join-Path $repository '.git'),
    (Join-Path $repository 'target'),
    (Join-Path $repository '.env.example'),
    $PSCommandPath
)

$patterns = @(
    '(?i)(api[_-]?key|authorization|client[_-]?secret|access[_-]?token)\s*[:=]\s*["'']?[A-Za-z0-9_\-\.]{20,}',
    '(?i)https?://[^\s/]*sofascore[^\s]*api[^\s]*'
)

$findings = New-Object System.Collections.Generic.List[string]
Get-ChildItem -LiteralPath $repository -Recurse -File | ForEach-Object {
    $path = $_.FullName
    if ($excluded | Where-Object { $path.StartsWith($_, [System.StringComparison]::OrdinalIgnoreCase) }) {
        return
    }
    $content = Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    foreach ($pattern in $patterns) {
        if ($content -match $pattern) {
            $findings.Add($path)
            break
        }
    }
}

if ($findings.Count -gt 0) {
    $findings | Sort-Object -Unique | ForEach-Object { Write-Error "Valeur sensible potentielle : $_" }
    exit 1
}

Write-Output 'PASS: aucun secret a forte confiance ni endpoint prive SofaScore detecte.'
