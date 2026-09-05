[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$DatabaseHost = '127.0.0.1',

    [ValidateRange(1, 65535)]
    [int]$Port = 5433,

    [Parameter(Mandatory = $true)]
    [string]$Database,

    [Parameter(Mandatory = $true)]
    [string]$Username,

    [string]$PgDumpPath = 'pg_dump',

    [string]$PsqlPath = 'psql',

    [string]$AgePath = 'age',

    [ValidateSet('Interactive', 'AgeGenerated')]
    [string]$PassphraseMode = 'Interactive',

    [ValidateRange(1, 3600)]
    [int]$TimeoutSeconds = 300,

    [ValidateRange(100, 30000)]
    [int]$CleanupTimeoutMilliseconds = 5000
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

Import-Module (Join-Path $PSScriptRoot 'J7-BackupRestore.Core.psm1') -Force

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$result = Invoke-J7Backup `
    -Destination $Destination `
    -RepositoryRoot $repositoryRoot `
    -DatabaseHost $DatabaseHost `
    -Port $Port `
    -Database $Database `
    -Username $Username `
    -PgDumpPath $PgDumpPath `
    -PsqlPath $PsqlPath `
    -AgePath $AgePath `
    -PassphraseMode $PassphraseMode `
    -TimeoutSeconds $TimeoutSeconds `
    -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

Write-Host "INT001_J7_BACKUP_RESULT=$($result.Result)"
Write-Host "INT001_J7_BACKUP_CIPHER_SHA256=$($result.CipherSha256)"
Write-Host "INT001_J7_BACKUP_MANIFEST_SHA256=$($result.ManifestSha256)"
Write-Host "INT001_J7_BACKUP_REQUIRED_MIGRATION=$($result.RequiredMigration)"
Write-Host "INT001_J7_BACKUP_POSTGRES_MAJOR=$($result.PostgresMajorVersion)"
Write-Host "INT001_J7_BACKUP_AGE_VERSION=$($result.AgeVersion)"
Write-Host "INT001_J7_BACKUP_TABLE_COUNT=$($result.TableCount)"
