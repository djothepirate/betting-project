[CmdletBinding()]
param(
    [string]$MavenCommand,
    [string]$SecretScanScript
)

$ErrorActionPreference = 'Stop'

function Invoke-NativeCommand {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [string[]]$ArgumentList = @(),

        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    Write-Host "==> $Description"
    & $FilePath @ArgumentList
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "$Description a echoue avec le code de sortie $exitCode."
    }
}

$repository = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($MavenCommand)) {
    $MavenCommand = Join-Path $repository 'mvnw.cmd'
}
if ([string]::IsNullOrWhiteSpace($SecretScanScript)) {
    $SecretScanScript = Join-Path $PSScriptRoot 'check-no-secrets.ps1'
}

$windowsPowerShell = (Get-Command powershell.exe -CommandType Application -ErrorAction Stop).Source

Push-Location $repository
try {
    Invoke-NativeCommand -FilePath $MavenCommand -ArgumentList @('-version') -Description 'Controle du Maven Wrapper'
    Invoke-NativeCommand -FilePath $MavenCommand -ArgumentList @('verify') -Description 'Build et tests standards'
    Invoke-NativeCommand -FilePath $windowsPowerShell `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $SecretScanScript) `
        -Description 'Controle de secrets'
    Invoke-NativeCommand -FilePath $MavenCommand -ArgumentList @('-Pintegration', 'verify') -Description 'Tests PostgreSQL/Testcontainers'
}
finally {
    Pop-Location
}
