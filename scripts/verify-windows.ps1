$ErrorActionPreference = 'Stop'

$repository = Split-Path -Parent $PSScriptRoot
Push-Location $repository
try {
    & .\mvnw.cmd -version
    & .\mvnw.cmd verify
    & .\scripts\check-no-secrets.ps1

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -ne $docker) {
        & .\mvnw.cmd -Pintegration verify
    }
    else {
        Write-Warning 'Docker indisponible : tests Testcontainers non exécutés.'
    }
}
finally {
    Pop-Location
}
