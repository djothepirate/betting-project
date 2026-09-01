param(
    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,

    [string]$Index,

    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Index)) {
    $Index = Join-Path $repository 'docs\benchmark\evidence\enr-001-evidence-index-v0.1.json'
}

$resolvedEvidenceRoot = (Resolve-Path -LiteralPath $EvidenceRoot).Path
$resolvedIndex = (Resolve-Path -LiteralPath $Index).Path

Push-Location $repository
try {
    if (-not $SkipBuild) {
        & (Join-Path $repository 'mvnw.cmd') -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "La construction Maven a échoué avec le code $LASTEXITCODE."
        }
    }

    $jar = Get-ChildItem -LiteralPath (Join-Path $repository 'target') `
        -Filter 'betting-project-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw 'Artefact exécutable introuvable dans target.'
    }

    & java `
        '-Dloader.main=com.bettingproject.collection.adapter.replay.EnrEvidenceCorpusVerifierCli' `
        -cp $jar.FullName `
        org.springframework.boot.loader.launch.PropertiesLauncher `
        --evidence-root $resolvedEvidenceRoot `
        --index $resolvedIndex
    if ($LASTEXITCODE -ne 0) {
        throw "La vérification hors réseau du corpus ENR-001 a échoué avec le code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
