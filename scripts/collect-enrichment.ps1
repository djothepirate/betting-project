param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^ENR-[PCR][0-9]{2}$')]
    [string]$SampleId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]+$')]
    [string]$MatchId,

    [Parameter(Mandatory = $true)]
    [ValidateSet('detail', 'lineup', 'statistics', 'events', 'box-score')]
    [string]$Endpoint,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Z0-9+_-]+$')]
    [string]$Window,

    [Parameter(Mandatory = $true)]
    [string]$Scenarios,

    [string]$OutputRoot,

    [string]$Manifest,

    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path (Split-Path -Parent $repository) 'output\benchmark\enr01'
}
if ([string]::IsNullOrWhiteSpace($Manifest)) {
    $Manifest = Join-Path $repository 'docs\benchmark\enrichment-sample-v0.1.json'
}

$taskUserKey = [Environment]::GetEnvironmentVariable('HIGHLIGHTLY_API_KEY', 'User')
if ([string]::IsNullOrWhiteSpace($env:HIGHLIGHTLY_API_KEY) -and
        -not [string]::IsNullOrWhiteSpace($taskUserKey)) {
    $env:HIGHLIGHTLY_API_KEY = $taskUserKey
}
if ([string]::IsNullOrWhiteSpace($env:HIGHLIGHTLY_API_KEY)) {
    throw 'La variable HIGHLIGHTLY_API_KEY est absente. La clé doit être injectée dans l’environnement du processus.'
}

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
        '-Dloader.main=com.bettingproject.collection.adapter.cli.EnrichmentCollectorCli' `
        -cp $jar.FullName `
        org.springframework.boot.loader.launch.PropertiesLauncher `
        --sample-id $SampleId `
        --match-id $MatchId `
        --endpoint $Endpoint `
        --window $Window `
        --scenarios $Scenarios `
        --output-root $OutputRoot `
        --manifest $Manifest
    if ($LASTEXITCODE -ne 0) {
        throw "Le collecteur s'est arrêté avec le code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
