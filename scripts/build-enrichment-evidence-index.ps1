[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,

    [string]$OutputPath,

    [ValidateRange(1, 10000)]
    [int]$ExpectedCallCount = 127
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repository 'docs\benchmark\evidence\enr-001-evidence-index-v0.1.json'
}

$resolvedRoot = (Resolve-Path -LiteralPath $EvidenceRoot).Path.TrimEnd('\', '/')
$rootPrefix = $resolvedRoot + [System.IO.Path]::DirectorySeparatorChar
$metadataFiles = @(Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File -Filter '*.metadata.json' |
    Sort-Object FullName)
if ($metadataFiles.Count -ne $ExpectedCallCount) {
    throw "Le corpus contient $($metadataFiles.Count) métadonnées au lieu de $ExpectedCallCount."
}

function Get-LogicalPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Le chemin sort de la racine des preuves : $fullPath"
    }
    return $fullPath.Substring($rootPrefix.Length).Replace('\', '/')
}

function Get-CanonicalStartedAt {
    param([Parameter(Mandatory = $true)][string]$RunId)

    if ($RunId -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z-[0-9a-fA-F]{8}$') {
        throw "Run id de preuve invalide : $RunId"
    }
    $timestamp = $RunId.Substring(0, 24)
    $styles = [System.Globalization.DateTimeStyles]::AssumeUniversal -bor
        [System.Globalization.DateTimeStyles]::AdjustToUniversal
    $parsed = [DateTime]::ParseExact(
        $timestamp,
        "yyyy-MM-dd'T'HH-mm-ss-fff'Z'",
        [System.Globalization.CultureInfo]::InvariantCulture,
        $styles)
    return $parsed.ToString('yyyy-MM-ddTHH:mm:ss.fffZ', [System.Globalization.CultureInfo]::InvariantCulture)
}

$ordinalByKey = @{}
$entries = [System.Collections.Generic.List[object]]::new()
foreach ($metadataFile in $metadataFiles) {
    $metadataPath = Get-LogicalPath -Path $metadataFile.FullName
    $pathParts = $metadataPath.Split('/')
    if ($pathParts.Count -lt 5) {
        throw "Chemin de métadonnée inattendu : $metadataPath"
    }
    $runId = $pathParts[0]
    $metadata = Get-Content -LiteralPath $metadataFile.FullName -Raw -Encoding utf8 | ConvertFrom-Json
    $rawFile = Join-Path $metadataFile.DirectoryName ([string]$metadata.rawFile)
    $replayFile = Join-Path $metadataFile.DirectoryName ($metadataFile.Name.Replace('.metadata.json', '.replay.json'))
    if (-not (Test-Path -LiteralPath $rawFile -PathType Leaf) -or
            -not (Test-Path -LiteralPath $replayFile -PathType Leaf)) {
        throw "Triplet de preuve incomplet : $metadataPath"
    }

    $rawHash = (Get-FileHash -LiteralPath $rawFile -Algorithm SHA256).Hash.ToLowerInvariant()
    $rawBytes = (Get-Item -LiteralPath $rawFile).Length
    if ($rawHash -ne ([string]$metadata.sha256).ToLowerInvariant() -or
            $rawBytes -ne [long]$metadata.bytes) {
        throw "Empreinte ou taille incohérente : $metadataPath"
    }
    try {
        $null = Get-Content -LiteralPath $rawFile -Raw -Encoding utf8 | ConvertFrom-Json
    }
    catch {
        throw "Payload JSON invalide : $metadataPath"
    }

    $replay = Get-Content -LiteralPath $replayFile -Raw -Encoding utf8 | ConvertFrom-Json
    if ([string]$replay.status -ne 'PASS') {
        throw "Replay non conforme : $metadataPath"
    }
    $provider = [string]$metadata.provider
    $replaySchema = if (-not [string]::IsNullOrWhiteSpace([string]$replay.schemaVersion)) {
        [string]$replay.schemaVersion
    }
    elseif ($provider -eq 'football-data.org') {
        'football-data-legacy-replay-v1'
    }
    else {
        throw "Schéma de replay absent pour une preuve non legacy : $metadataPath"
    }
    $parserVersion = if (-not [string]::IsNullOrWhiteSpace([string]$replay.parserVersion)) {
        [string]$replay.parserVersion
    }
    elseif (-not [string]::IsNullOrWhiteSpace([string]$metadata.parserVersion)) {
        [string]$metadata.parserVersion
    }
    elseif ($provider -eq 'football-data.org') {
        'football-data-detail-parser-legacy-v1'
    }
    else {
        'json-structure-v1'
    }
    $parserVersionSource = if (-not [string]::IsNullOrWhiteSpace([string]$replay.parserVersion)) {
        'RECORDED_REPLAY'
    }
    elseif (-not [string]::IsNullOrWhiteSpace([string]$metadata.parserVersion)) {
        'RECORDED_METADATA'
    }
    else {
        'FINALIZATION_CLASSIFICATION'
    }

    $sampleId = [string]$metadata.sampleId
    $endpointFamily = [string]$metadata.endpointFamily
    $ordinalKey = "$sampleId|$provider|$endpointFamily"
    $ordinal = 1 + [int]($ordinalByKey[$ordinalKey])
    $ordinalByKey[$ordinalKey] = $ordinal
    $providerSegment = if ($provider -eq 'football-data.org') { '-FD' } else { '' }
    $logicalIdFamily = $endpointFamily.Replace('_', '-')
    $logicalId = '{0}{1}-{2}-{3:D3}' -f $sampleId, $providerSegment, $logicalIdFamily, $ordinal

    $entries.Add([ordered]@{
        logicalId = $logicalId
        runId = $runId
        callId = [string]$metadata.callId
        provider = $provider
        sampleId = $sampleId
        endpointFamily = $endpointFamily
        collectionWindow = [string]$metadata.collectionWindow
        startedAt = Get-CanonicalStartedAt -RunId $runId
        httpStatus = [int]$metadata.httpStatus
        bytes = [long]$metadata.bytes
        sha256 = ([string]$metadata.sha256).ToLowerInvariant()
        connectorVersion = [string]$metadata.connectorVersion
        parserVersion = $parserVersion
        parserVersionSource = $parserVersionSource
        replaySchema = $replaySchema
        replayStatus = [string]$replay.status
        metadataPath = $metadataPath
        rawPath = Get-LogicalPath -Path $rawFile
        replayPath = Get-LogicalPath -Path $replayFile
    })
}

$index = [ordered]@{
    schemaVersion = 'enr-001-evidence-index-v1'
    corpusId = 'ENR-001'
    expectedCallCount = $ExpectedCallCount
    generatedFrom = 'validated-recovery-corpus-with-finalization-classifications'
    entries = @($entries)
}

$outputDirectory = Split-Path -Parent $OutputPath
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$temporary = Join-Path $outputDirectory ('.{0}.{1}.tmp' -f (Split-Path -Leaf $OutputPath), [Guid]::NewGuid().ToString('N'))
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try {
    [System.IO.File]::WriteAllText($temporary, ($index | ConvertTo-Json -Depth 8), $utf8NoBom)
    if (Test-Path -LiteralPath $OutputPath) {
        Remove-Item -LiteralPath $OutputPath -Force
    }
    [System.IO.File]::Move($temporary, $OutputPath)
}
finally {
    if (Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary -Force
    }
}

[pscustomobject]@{
    status = 'PASS'
    corpusId = 'ENR-001'
    evidenceCount = $entries.Count
    outputPath = (Resolve-Path -LiteralPath $OutputPath).Path
    networkCalls = 0
} | ConvertTo-Json
