[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ArchivePath,

    [string]$PrimaryHost = '127.0.0.1',

    [ValidateRange(1, 65535)]
    [int]$PrimaryPort = 5433,

    [Parameter(Mandatory = $true)]
    [string]$PrimaryDatabase,

    [string]$TargetHost = '127.0.0.1',

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$TargetPort,

    [Parameter(Mandatory = $true)]
    [string]$TargetDatabase,

    [Parameter(Mandatory = $true)]
    [string]$TargetUsername,

    [Parameter(Mandatory = $true)]
    [string]$IsolationAcknowledgement,

    [string]$PgRestorePath = 'pg_restore',

    [string]$PsqlPath = 'psql',

    [string]$AgePath = 'age',

    [ValidateRange(1, 3600)]
    [int]$TimeoutSeconds = 300,

    [ValidateRange(100, 30000)]
    [int]$CleanupTimeoutMilliseconds = 5000
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

Import-Module (Join-Path $PSScriptRoot 'J7-BackupRestore.Core.psm1') -Force

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$result = Invoke-J7Restore `
    -ArchivePath $ArchivePath `
    -RepositoryRoot $repositoryRoot `
    -PrimaryHost $PrimaryHost `
    -PrimaryPort $PrimaryPort `
    -PrimaryDatabase $PrimaryDatabase `
    -TargetHost $TargetHost `
    -TargetPort $TargetPort `
    -TargetDatabase $TargetDatabase `
    -TargetUsername $TargetUsername `
    -IsolationAcknowledgement $IsolationAcknowledgement `
    -PgRestorePath $PgRestorePath `
    -PsqlPath $PsqlPath `
    -AgePath $AgePath `
    -TimeoutSeconds $TimeoutSeconds `
    -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

Write-Host "INT001_J7_RESTORE_RESULT=$($result.Result)"
Write-Host "INT001_J7_RESTORE_CIPHER_SHA256=$($result.CipherSha256)"
Write-Host "INT001_J7_RESTORE_REQUIRED_MIGRATION=$($result.RequiredMigration)"
Write-Host "INT001_J7_RESTORE_POSTGRES_MAJOR=$($result.PostgresMajorVersion)"
Write-Host "INT001_J7_RESTORE_AGE_VERSION=$($result.AgeVersion)"
Write-Host "INT001_J7_RESTORE_TABLE_COUNT=$($result.TableCount)"
Write-Host "INT001_J7_RESTORE_PRIMARY_MUTATION=$($result.PrimaryMutationPerformed)"
