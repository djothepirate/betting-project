$modulePath = Join-Path $PSScriptRoot '..\J7-BackupRestore.Core.psm1'
Import-Module $modulePath -Force

function New-J7TestManifest {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$ArchiveName = 'j7-test.age'
    )

    $emptySha256 = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
    $tables = @()
    foreach ($tableName in Get-J7ExpectedTableNames) {
        $tables += [ordered]@{
            name = $tableName
            rowCount = 0
            contentSha256 = $emptySha256
        }
    }
    $manifest = [ordered]@{
        formatVersion = 1
        purpose = 'INT-001_J7_FULL_DATABASE_BACKUP'
        backupScope = 'full-database'
        postgresMajorVersion = 17
        createdAtUtc = '2026-09-02T12:34:56.1234567Z'
        encryptedArchiveFileName = $ArchiveName
        cipherSha256 = ('a' * 64)
        cipherBytes = 3
        requiredMigration = 'V008'
        tables = $tables
        j7EvidenceStableAcrossDump = $true
        encryption = [ordered]@{
            format = 'age-passphrase'
            tool = 'age'
            toolVersion = 'v1.2.1'
            secretMaterialPersisted = $false
        }
        restorePolicy = [ordered]@{
            isolatedLoopbackTargetRequired = $true
            primaryMutationAllowed = $false
        }
    }
    [IO.File]::WriteAllText(
        $Path,
        ($manifest | ConvertTo-Json -Depth 6),
        [Text.UTF8Encoding]::new($false))
    return $manifest
}

Describe 'INT-001 J7 backup and restore contract' {
    It 'pins the five V006 and V007 physical tables in dependency-aware order' {
        $actual = @(Get-J7ExpectedTableNames)
        $actual.Count | Should Be 5
        ($actual -join ',') | Should Be (
            'j7_import_receipt,j7_import_payload,j7_import_audit,' +
            'outbox_message,j7_import_payload_tombstone')
    }

    It 'pins an explicit restore acknowledgement' {
        Get-J7IsolationAcknowledgement |
            Should Be 'RESTORE INT-001 J7 TO ISOLATED TARGET'
    }

    It 'accepts only the numeric IPv4 loopback endpoint' {
        {
            Assert-J7PostgresEndpoint `
                -DatabaseHost '127.0.0.1' `
                -Port 5433 `
                -Database 'local_lab' `
                -Username 'postgres'
        } | Should Not Throw

        foreach ($unsafeHost in @('localhost', '::1', '0.0.0.0', '192.168.1.10')) {
            {
                Assert-J7PostgresEndpoint `
                    -DatabaseHost $unsafeHost `
                    -Port 5433 `
                    -Database 'local_lab' `
                    -Username 'postgres'
            } | Should Throw 'J7_POSTGRES_HOST_MUST_BE_LOOPBACK'
        }
    }

    It 'requires a different port, a restore-only database name, and the exact acknowledgement' {
        {
            Assert-J7IsolatedRestoreTarget `
                -PrimaryHost '127.0.0.1' `
                -PrimaryPort 5433 `
                -PrimaryDatabase 'local_lab' `
                -TargetHost '127.0.0.1' `
                -TargetPort 55432 `
                -TargetDatabase 'int001_j7_restore_test' `
                -IsolationAcknowledgement (Get-J7IsolationAcknowledgement)
        } | Should Not Throw

        {
            Assert-J7IsolatedRestoreTarget `
                -PrimaryHost '127.0.0.1' `
                -PrimaryPort 5433 `
                -PrimaryDatabase 'local_lab' `
                -TargetHost '127.0.0.1' `
                -TargetPort 5433 `
                -TargetDatabase 'int001_j7_restore_test' `
                -IsolationAcknowledgement (Get-J7IsolationAcknowledgement)
        } | Should Throw 'J7_RESTORE_TARGET_NOT_ISOLATED'

        {
            Assert-J7IsolatedRestoreTarget `
                -PrimaryHost '127.0.0.1' `
                -PrimaryPort 5433 `
                -PrimaryDatabase 'local_lab' `
                -TargetHost '127.0.0.1' `
                -TargetPort 55432 `
                -TargetDatabase 'local_lab_restore' `
                -IsolationAcknowledgement (Get-J7IsolationAcknowledgement)
        } | Should Throw 'J7_RESTORE_DATABASE_IDENTITY_INVALID'

        {
            Assert-J7IsolatedRestoreTarget `
                -PrimaryHost '127.0.0.1' `
                -PrimaryPort 5433 `
                -PrimaryDatabase 'local_lab' `
                -TargetHost '127.0.0.1' `
                -TargetPort 55432 `
                -TargetDatabase 'int001_j7_restore_test' `
                -IsolationAcknowledgement 'yes'
        } | Should Throw 'J7_RESTORE_ACKNOWLEDGEMENT_INVALID'
    }

    It 'rejects an explicit pgpass file inside the repository' {
        $repository = Join-Path $TestDrive 'pgpass-repository'
        $outside = Join-Path $TestDrive 'pgpass-outside'
        New-Item -ItemType Directory -Path $repository -Force | Out-Null
        New-Item -ItemType Directory -Path $outside -Force | Out-Null
        $insideFile = Join-Path $repository 'pgpass.conf'
        $outsideFile = Join-Path $outside 'pgpass.conf'
        [IO.File]::WriteAllText($insideFile, 'synthetic-placeholder')
        [IO.File]::WriteAllText($outsideFile, 'synthetic-placeholder')
        $previous = [Environment]::GetEnvironmentVariable(
            'PGPASSFILE',
            [EnvironmentVariableTarget]::Process)
        try {
            [Environment]::SetEnvironmentVariable(
                'PGPASSFILE',
                $insideFile,
                [EnvironmentVariableTarget]::Process)
            { Assert-J7PgpassBoundary -RepositoryRoot $repository } |
                Should Throw 'J7_PGPASSFILE_MUST_BE_OUTSIDE_REPOSITORY'

            [Environment]::SetEnvironmentVariable(
                'PGPASSFILE',
                $outsideFile,
                [EnvironmentVariableTarget]::Process)
            { Assert-J7PgpassBoundary -RepositoryRoot $repository } |
                Should Not Throw
        }
        finally {
            [Environment]::SetEnvironmentVariable(
                'PGPASSFILE',
                $previous,
                [EnvironmentVariableTarget]::Process)
        }
    }

    It 'rejects an explicit pgpass file on a UNC share before filesystem access' {
        $repository = Join-Path $TestDrive 'pgpass-unc-repository'
        New-Item -ItemType Directory -Path $repository -Force | Out-Null
        $previous = [Environment]::GetEnvironmentVariable(
            'PGPASSFILE',
            [EnvironmentVariableTarget]::Process)
        try {
            [Environment]::SetEnvironmentVariable(
                'PGPASSFILE',
                '\\synthetic-host\share\pgpass.conf',
                [EnvironmentVariableTarget]::Process)
            { Assert-J7PgpassBoundary -RepositoryRoot $repository } |
                Should Throw 'J7_PGPASSFILE_UNC_PATH_REJECTED'
        }
        finally {
            [Environment]::SetEnvironmentVariable(
                'PGPASSFILE',
                $previous,
                [EnvironmentVariableTarget]::Process)
        }
    }

    It 'scrubs inherited connection overrides and retains only a validated pgpass file' {
        $repository = Join-Path $TestDrive 'environment-repository'
        $outside = Join-Path $TestDrive 'environment-outside'
        New-Item -ItemType Directory -Path $repository -Force | Out-Null
        New-Item -ItemType Directory -Path $outside -Force | Out-Null
        $pgpassFile = Join-Path $outside 'pgpass.conf'
        [IO.File]::WriteAllText($pgpassFile, 'synthetic-placeholder')

        $scrubbedNames = @(
            'AGE_PASSPHRASE',
            'PGPASSWORD',
            'PGHOSTADDR',
            'PGSERVICE',
            'PGSERVICEFILE',
            'PGHOST',
            'PGPORT',
            'PGDATABASE',
            'PGUSER',
            'PGTZ',
            'PGDATESTYLE'
        )
        $previousValues = @{}
        foreach ($name in @($scrubbedNames + 'PGPASSFILE')) {
            $previousValues[$name] = [Environment]::GetEnvironmentVariable(
                $name,
                [EnvironmentVariableTarget]::Process)
        }

        $childScript = Join-Path $TestDrive 'inspect-environment.ps1'
        [IO.File]::WriteAllText(
            $childScript,
            @'
$scrubbedNames = @(
    'AGE_PASSPHRASE',
    'PGPASSWORD',
    'PGHOSTADDR',
    'PGSERVICE',
    'PGSERVICEFILE',
    'PGHOST',
    'PGPORT',
    'PGDATABASE',
    'PGUSER',
    'PGTZ',
    'PGDATESTYLE'
)
foreach ($name in $scrubbedNames) {
    $value = [Environment]::GetEnvironmentVariable(
        $name,
        [EnvironmentVariableTarget]::Process)
    if ([string]::IsNullOrEmpty($value)) {
        Write-Output "$name=UNSET"
    }
    else {
        Write-Output "$name=SET"
    }
}
$pgpassFile = [Environment]::GetEnvironmentVariable(
    'PGPASSFILE',
    [EnvironmentVariableTarget]::Process)
Write-Output "PGPASSFILE=$pgpassFile"
'@,
            [Text.UTF8Encoding]::new($false))

        try {
            foreach ($name in $scrubbedNames) {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    "ambient-$name",
                    [EnvironmentVariableTarget]::Process)
            }
            [Environment]::SetEnvironmentVariable(
                'PGPASSFILE',
                $pgpassFile,
                [EnvironmentVariableTarget]::Process)
            { Assert-J7PgpassBoundary -RepositoryRoot $repository } |
                Should Not Throw

            $explicitEnvironment = @{}
            foreach ($name in $scrubbedNames) {
                $explicitEnvironment[$name] = "explicit-$name"
            }
            $pwsh = (Get-Command pwsh -CommandType Application |
                Select-Object -First 1).Source
            $actual = Invoke-J7NativeCapture `
                -FilePath $pwsh `
                -ArgumentList @(
                    '-NoProfile', '-NonInteractive', '-File', $childScript) `
                -Environment $explicitEnvironment `
                -TimeoutSeconds 10 `
                -CleanupTimeoutMilliseconds 2000

            $actualLines = @($actual -split '\r?\n')
            foreach ($name in $scrubbedNames) {
                ($actualLines -ccontains "$name=UNSET") | Should Be $true
                ($actualLines -ccontains "$name=SET") | Should Be $false
            }
            ($actualLines -ccontains "PGPASSFILE=$pgpassFile") | Should Be $true
        }
        finally {
            foreach ($name in $previousValues.Keys) {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    $previousValues[$name],
                    [EnvironmentVariableTarget]::Process)
            }
        }
    }

    It 'pins bytea and timestamp evidence despite opposite ambient PostgreSQL settings' {
        $ambientNames = @('PGOPTIONS', 'PGTZ', 'PGDATESTYLE')
        $previousValues = @{}
        foreach ($name in $ambientNames) {
            $previousValues[$name] = [Environment]::GetEnvironmentVariable(
                $name,
                [EnvironmentVariableTarget]::Process)
        }
        $module = Get-Module 'J7-BackupRestore.Core'
        try {
            [Environment]::SetEnvironmentVariable(
                'PGOPTIONS',
                '-c bytea_output=escape',
                [EnvironmentVariableTarget]::Process)
            [Environment]::SetEnvironmentVariable(
                'PGTZ',
                'Pacific/Honolulu',
                [EnvironmentVariableTarget]::Process)
            [Environment]::SetEnvironmentVariable(
                'PGDATESTYLE',
                'SQL,DMY',
                [EnvironmentVariableTarget]::Process)
            $sourceEnvironment = & $module {
                New-J7PostgresEnvironment `
                    -ApplicationName 'int001_source_evidence' `
                    -TimeoutSeconds 7
            }

            [Environment]::SetEnvironmentVariable(
                'PGOPTIONS',
                '-c bytea_output=hex -c statement_timeout=1',
                [EnvironmentVariableTarget]::Process)
            [Environment]::SetEnvironmentVariable(
                'PGTZ',
                'Europe/Paris',
                [EnvironmentVariableTarget]::Process)
            [Environment]::SetEnvironmentVariable(
                'PGDATESTYLE',
                'German,DMY',
                [EnvironmentVariableTarget]::Process)
            $targetEnvironment = & $module {
                New-J7PostgresEnvironment `
                    -ApplicationName 'int001_target_evidence' `
                    -TimeoutSeconds 7
            }

            $expected = '-c statement_timeout=7000 -c bytea_output=hex' +
                ' -c TimeZone=UTC -c DateStyle=ISO,YMD'
            $sourceEnvironment.PGOPTIONS | Should Be $expected
            $targetEnvironment.PGOPTIONS | Should Be $expected
            $sourceEnvironment.ContainsKey('PGTZ') | Should Be $false
            $sourceEnvironment.ContainsKey('PGDATESTYLE') | Should Be $false
            $targetEnvironment.ContainsKey('PGTZ') | Should Be $false
            $targetEnvironment.ContainsKey('PGDATESTYLE') | Should Be $false
        }
        finally {
            foreach ($name in $ambientNames) {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    $previousValues[$name],
                    [EnvironmentVariableTarget]::Process)
            }
        }
    }
}

Describe 'INT-001 J7 archive path boundary' {
    BeforeEach {
        $repository = Join-Path $TestDrive 'repository'
        $outside = Join-Path $TestDrive 'outside'
        New-Item -ItemType Directory -Path $repository -Force | Out-Null
        New-Item -ItemType Directory -Path $outside -Force | Out-Null
    }

    It 'requires an absolute destination in a pre-existing directory outside the repository' {
        $destination = Join-Path $outside 'j7.age'
        $resolved = Resolve-J7NewArchiveDestination `
            -Destination $destination `
            -RepositoryRoot $repository
        $resolved.ArchivePath | Should Be ([IO.Path]::GetFullPath($destination))

        {
            Resolve-J7NewArchiveDestination `
                -Destination 'relative.age' `
                -RepositoryRoot $repository
        } | Should Throw 'J7_DESTINATION_MUST_BE_ABSOLUTE'

        {
            Resolve-J7NewArchiveDestination `
                -Destination (Join-Path $TestDrive 'missing\j7.age') `
                -RepositoryRoot $repository
        } | Should Throw 'J7_DESTINATION_DIRECTORY_MUST_PREEXIST'
    }

    It 'rejects a destination in the repository, a wrong extension, and overwrite' {
        {
            Resolve-J7NewArchiveDestination `
                -Destination (Join-Path $repository 'j7.age') `
                -RepositoryRoot $repository
        } | Should Throw 'J7_DESTINATION_MUST_BE_OUTSIDE_REPOSITORY'

        {
            Resolve-J7NewArchiveDestination `
                -Destination (Join-Path $outside 'j7.dump') `
                -RepositoryRoot $repository
        } | Should Throw 'J7_DESTINATION_EXTENSION_INVALID'

        $existing = Join-Path $outside 'existing.age'
        [IO.File]::WriteAllBytes($existing, [byte[]](1))
        {
            Resolve-J7NewArchiveDestination `
                -Destination $existing `
                -RepositoryRoot $repository
        } | Should Throw 'J7_DESTINATION_ALREADY_EXISTS'
    }

    It 'requires both an archive and its adjacent manifest for restore' {
        $archive = Join-Path $outside 'j7.age'
        [IO.File]::WriteAllBytes($archive, [byte[]](1, 2, 3))
        {
            Resolve-J7ExistingArchive `
                -ArchivePath $archive `
                -RepositoryRoot $repository
        } | Should Throw 'J7_ARCHIVE_OR_MANIFEST_MISSING'

        New-J7TestManifest -Path ($archive + '.manifest.json') | Out-Null
        {
            Resolve-J7ExistingArchive `
                -ArchivePath $archive `
                -RepositoryRoot $repository
        } | Should Not Throw
    }

    It 'rejects UNC destinations and restore archives before filesystem access' {
        {
            Resolve-J7NewArchiveDestination `
                -Destination '\\synthetic-host\share\j7.age' `
                -RepositoryRoot $repository
        } | Should Throw 'J7_DESTINATION_UNC_PATH_REJECTED'

        {
            Resolve-J7ExistingArchive `
                -ArchivePath '\\synthetic-host\share\j7.age' `
                -RepositoryRoot $repository
        } | Should Throw 'J7_ARCHIVE_UNC_PATH_REJECTED'
    }
}

Describe 'INT-001 J7 executable locality boundary' {
    It 'rejects direct and command-resolved UNC executable paths' {
        {
            Resolve-J7Executable `
                -Candidate '\\synthetic-host\share\pg_dump.exe' `
                -FailureCode 'J7_PG_DUMP_NOT_FOUND'
        } | Should Throw 'J7_EXECUTABLE_UNC_PATH_REJECTED'

        Mock Get-Command -ModuleName 'J7-BackupRestore.Core' {
            [pscustomobject]@{ Source = '\\synthetic-host\share\pg_dump.exe' }
        }
        {
            Resolve-J7Executable `
                -Candidate 'synthetic-pg-dump' `
                -FailureCode 'J7_PG_DUMP_NOT_FOUND'
        } | Should Throw 'J7_EXECUTABLE_UNC_PATH_REJECTED'
    }
}

Describe 'INT-001 J7 redacted manifest parser' {
    BeforeEach {
        $manifestPath = Join-Path $TestDrive 'j7.age.manifest.json'
        $manifest = New-J7TestManifest -Path $manifestPath
    }

    It 'accepts the exact contract and preserves table counts and hashes' {
        $parsed = Read-J7Manifest -ManifestPath $manifestPath
        $parsed.requiredMigration | Should Be 'V008'
        $parsed.backupScope | Should Be 'full-database'
        $parsed.postgresMajorVersion | Should Be 17
        $parsed.ageVersion | Should Be 'v1.2.1'
        $parsed.tables.Count | Should Be 5
        $parsed.tables[0].name | Should Be 'j7_import_receipt'
        $parsed.tables[4].name | Should Be 'j7_import_payload_tombstone'
    }

    It 'rejects a legacy manifest requiring only V006' {
        $manifest.requiredMigration = 'V006'
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_CONTRACT_INVALID'
    }

    It 'rejects duplicate JSON properties before object conversion' {
        $json = [IO.File]::ReadAllText($manifestPath)
        $json = $json -replace (
            '"purpose"\s*:\s*"INT-001_J7_FULL_DATABASE_BACKUP"'), (
            '"purpose":"INT-001_J7_FULL_DATABASE_BACKUP",' +
            '"purpose":"INT-001_J7_FULL_DATABASE_BACKUP"')
        [IO.File]::WriteAllText(
            $manifestPath,
            $json,
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_DUPLICATE_PROPERTY'
    }

    It 'rejects unknown properties and trailing JSON tokens' {
        $manifest['unknown'] = 'rejected'
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_PROPERTIES_INVALID'

        New-J7TestManifest -Path $manifestPath | Out-Null
        [IO.File]::AppendAllText(
            $manifestPath,
            '{}',
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_JSON_INVALID'
    }

    It 'rejects invalid UTF-8, uppercase hashes, and table reordering' {
        [IO.File]::WriteAllBytes($manifestPath, [byte[]](0xC3, 0x28))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_JSON_INVALID'

        $manifest = New-J7TestManifest -Path $manifestPath
        $manifest.cipherSha256 = ('A' * 64)
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_CONTRACT_INVALID'

        $manifest = New-J7TestManifest -Path $manifestPath
        $first = $manifest.tables[0]
        $manifest.tables[0] = $manifest.tables[1]
        $manifest.tables[1] = $first
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_TABLES_INVALID'
    }

    It 'rejects any manifest claiming persisted secret material or primary mutation' {
        $manifest.encryption.secretMaterialPersisted = $true
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_ENCRYPTION_INVALID'

        $manifest = New-J7TestManifest -Path $manifestPath
        $manifest.restorePolicy.primaryMutationAllowed = $true
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_RESTORE_POLICY_INVALID'
    }

    It 'rejects a table-only scope, another PostgreSQL major, or an invalid age version' {
        $manifest.backupScope = 'table-subset'
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_CONTRACT_INVALID'

        $manifest = New-J7TestManifest -Path $manifestPath
        $manifest.postgresMajorVersion = 18
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_CONTRACT_INVALID'

        $manifest = New-J7TestManifest -Path $manifestPath
        $manifest.encryption.toolVersion = 'latest'
        [IO.File]::WriteAllText(
            $manifestPath,
            ($manifest | ConvertTo-Json -Depth 6),
            [Text.UTF8Encoding]::new($false))
        { Read-J7Manifest -ManifestPath $manifestPath } |
            Should Throw 'J7_MANIFEST_ENCRYPTION_INVALID'
    }
}

Describe 'INT-001 J7 evidence parser' {
    It 'accepts only canonical non-negative counts and lowercase SHA-256' {
        $evidence = ConvertFrom-J7EvidenceScalar `
            -TableName 'j7_import_receipt' `
            -Scalar ('12|' + ('a' * 64))
        $evidence.rowCount | Should Be 12
        $evidence.contentSha256 | Should Be ('a' * 64)

        foreach ($invalid in @(
                '01|' + ('a' * 64),
                '-1|' + ('a' * 64),
                '1|' + ('A' * 64),
                '1|abcd')) {
            {
                ConvertFrom-J7EvidenceScalar `
                    -TableName 'j7_import_receipt' `
                    -Scalar $invalid
            } | Should Throw 'J7_TABLE_EVIDENCE_INVALID'
        }
    }

    It 'rejects a non-contract table even with a valid scalar' {
        {
            ConvertFrom-J7EvidenceScalar `
                -TableName 'users' `
                -Scalar ('0|' + ('a' * 64))
        } | Should Throw 'J7_TABLE_EVIDENCE_INVALID'
    }

}

Describe 'INT-001 bounded native binary pipeline' {
    BeforeEach {
        $pwsh = (Get-Command pwsh -CommandType Application |
            Select-Object -First 1).Source
        $producerScript = Join-Path $TestDrive 'producer.ps1'
        $consumerScript = Join-Path $TestDrive 'consumer.ps1'
        [IO.File]::WriteAllText(
            $producerScript,
            @'
$stream = [Console]::OpenStandardOutput()
$bytes = [byte[]](0..255)
$stream.Write($bytes, 0, $bytes.Length)
$stream.Flush()
'@,
            [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText(
            $consumerScript,
            @'
param([string]$OutputPath)
$inputStream = [Console]::OpenStandardInput()
$outputStream = [IO.File]::Create($OutputPath)
try {
    $inputStream.CopyTo($outputStream)
}
finally {
    $outputStream.Dispose()
}
'@,
            [Text.UTF8Encoding]::new($false))
    }

    It 'copies all binary byte values without a PowerShell text pipeline' {
        $outputPath = Join-Path $TestDrive 'binary.out'
        Invoke-J7NativeBinaryPipeline `
            -ProducerFilePath $pwsh `
            -ProducerArgumentList @(
                '-NoProfile', '-NonInteractive', '-File', $producerScript) `
            -ConsumerFilePath $pwsh `
            -ConsumerArgumentList @(
                '-NoProfile', '-NonInteractive', '-File', $consumerScript, $outputPath) `
            -ProducerEnvironment @{} `
            -ConsumerEnvironment @{} `
            -TimeoutSeconds 10 `
            -CleanupTimeoutMilliseconds 2000
        $bytes = [IO.File]::ReadAllBytes($outputPath)
        $bytes.Length | Should Be 256
        for ($index = 0; $index -lt 256; $index++) {
            $bytes[$index] | Should Be $index
        }
    }

    It 'times out and cleans the native process trees' {
        $childPidPath = Join-Path $TestDrive 'child.pid'
        [IO.File]::WriteAllText(
            $producerScript,
            @'
param([string]$ChildPidPath)
$childExecutable = Join-Path $PSHOME 'pwsh.exe'
$child = Start-Process `
    -FilePath $childExecutable `
    -ArgumentList @(
        '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30') `
    -PassThru `
    -WindowStyle Hidden
[IO.File]::WriteAllText($ChildPidPath, $child.Id.ToString())
Start-Sleep -Seconds 30
'@,
            [Text.UTF8Encoding]::new($false))
        $outputPath = Join-Path $TestDrive 'timeout.out'
        {
            Invoke-J7NativeBinaryPipeline `
                -ProducerFilePath $pwsh `
                -ProducerArgumentList @(
                    '-NoProfile', '-NonInteractive', '-File', $producerScript,
                    $childPidPath) `
                -ConsumerFilePath $pwsh `
                -ConsumerArgumentList @(
                    '-NoProfile', '-NonInteractive', '-File', $consumerScript, $outputPath) `
                -ProducerEnvironment @{} `
                -ConsumerEnvironment @{} `
                -TimeoutSeconds 1 `
                -CleanupTimeoutMilliseconds 2000
        } | Should Throw 'J7_PIPELINE_TIMEOUT'
        (Test-Path -LiteralPath $childPidPath) | Should Be $true
        $childPid = [int][IO.File]::ReadAllText($childPidPath)
        Get-Process -Id $childPid -ErrorAction SilentlyContinue |
            Should BeNullOrEmpty
    }

    It 'does not expose captured stderr when a producer fails' {
        [IO.File]::WriteAllText(
            $producerScript,
            "[Console]::Error.WriteLine('synthetic-sensitive-marker'); exit 7",
            [Text.UTF8Encoding]::new($false))
        $outputPath = Join-Path $TestDrive 'failure.out'
        $message = $null
        try {
            Invoke-J7NativeBinaryPipeline `
                -ProducerFilePath $pwsh `
                -ProducerArgumentList @(
                    '-NoProfile', '-NonInteractive', '-File', $producerScript) `
                -ConsumerFilePath $pwsh `
                -ConsumerArgumentList @(
                    '-NoProfile', '-NonInteractive', '-File', $consumerScript, $outputPath) `
                -ProducerEnvironment @{} `
                -ConsumerEnvironment @{} `
                -TimeoutSeconds 10 `
                -CleanupTimeoutMilliseconds 2000
        }
        catch {
            $message = $_.Exception.Message
        }
        $message | Should Be 'J7_NATIVE_PIPELINE_FAILED'
        $message | Should Not Match 'synthetic-sensitive-marker'
    }
}

Describe 'INT-001 script static safety gates' {
    It 'parses every new PowerShell source without syntax errors' {
        $paths = @(
            $modulePath,
            (Join-Path $PSScriptRoot '..\Backup-J7Imports.ps1'),
            (Join-Path $PSScriptRoot '..\Restore-J7Imports.ps1'),
            $PSCommandPath
        )
        foreach ($path in $paths) {
            $tokens = $null
            $errors = $null
            [Management.Automation.Language.Parser]::ParseFile(
                (Resolve-Path $path),
                [ref]$tokens,
                [ref]$errors) | Out-Null
            $errors.Count | Should Be 0
        }
    }

    It 'contains no database destruction, Docker, provider, receiver, VPS, or network URL' {
        $sourcePaths = @(
            $modulePath,
            (Join-Path $PSScriptRoot '..\Backup-J7Imports.ps1'),
            (Join-Path $PSScriptRoot '..\Restore-J7Imports.ps1')
        )
        $source = ($sourcePaths | ForEach-Object {
                [IO.File]::ReadAllText((Resolve-Path $_))
            }) -join "`n"
        $source | Should Not Match '(?i)\bdelete\s+from\b'
        $source | Should Not Match '(?i)\btruncate\b'
        $source | Should Not Match '(?i)\bdrop\s+(database|table)\b'
        $source | Should Not Match '(?i)\bcreate\s+database\b'
        $source | Should Not Match '(?i)--clean|--create'
        $source | Should Not Match '(?i)\bdocker\b|\bprovider\b|\breceiver\b|\bvps\b'
        $source | Should Not Match '(?i)https?://'
    }

    It 'scrubs ambient password variables and uses no env file or command-line secret' {
        $source = [IO.File]::ReadAllText((Resolve-Path $modulePath))
        $source | Should Match '\.Environment\.Remove\(\$variableName\)'
        foreach ($name in @(
                'AGE_PASSPHRASE', 'PGPASSWORD', 'PGHOSTADDR', 'PGSERVICE',
                'PGSERVICEFILE', 'PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER',
                'PGTZ', 'PGDATESTYLE')) {
            $source | Should Match ([regex]::Escape("'$name'"))
        }
        $source | Should Not Match '(?i)["'']\.env["'']|--env-file'
        $source | Should Match "@\('--passphrase', '--output'"
        $source | Should Match '@\(''--decrypt'', \$archiveInfo\.ArchivePath\)'
        $source | Should Match "'--no-password'"
    }

    It 'uses binary copy, process-tree kill, a full custom dump, and transactional restore' {
        $source = [IO.File]::ReadAllText((Resolve-Path $modulePath))
        $source | Should Match '\.CopyToAsync\('
        $source | Should Match '\.Kill\(\$true\)'
        $source | Should Match "'--format=custom'"
        $source | Should Match "'--compress=9'"
        $source | Should Not Match "'--data-only'|--table="
        $source | Should Match "'--single-transaction'"
        $source | Should Match 'J7_RESTORE_TARGET_DATABASE_NOT_FRESH'
        $source | Should Match 'J7_POSTGRES_17_REQUIRED'
        $source | Should Match 'J7_POSTGRES_CLIENT_17_REQUIRED'
        $source | Should Match 'J7_AGE_VERSION_INVALID'
        $source | Should Match 'J7_PAYLOAD_HASH_INTEGRITY_FAILED'
        $source | Should Match "sha256\(payload\.payload\)"
        $source | Should Match "bytea_output=hex"
        $source | Should Match 'from pg_largeobject_metadata'
        foreach ($catalog in @(
                'pg_publication', 'pg_subscription', 'pg_event_trigger',
                'pg_foreign_data_wrapper', 'pg_foreign_server', 'pg_user_mappings',
                'pg_default_acl', 'pg_seclabel', 'pg_cast', 'pg_conversion',
                'pg_operator', 'pg_opclass', 'pg_opfamily', 'pg_am', 'pg_transform',
                'pg_ts_config', 'pg_ts_dict', 'pg_ts_parser', 'pg_ts_template')) {
            $source | Should Match ([regex]::Escape($catalog))
        }
        $source | Should Match "lanname not in \('internal', 'c', 'sql', 'plpgsql'\)"
    }

    It 'does not use PostgreSQL catalog names as potentially reserved SQL aliases' {
        $source = [IO.File]::ReadAllText((Resolve-Path $modulePath))
        foreach ($forbiddenAlias in @(
                'from pg_collation collation',
                'from pg_subscription subscription',
                'from pg_database database',
                'from pg_language language',
                'from pg_extension extension')) {
            $source | Should Not Match ([regex]::Escape($forbiddenAlias))
        }
        foreach ($qualifiedAlias in @(
                'from pg_collation catalog_collation',
                'from pg_subscription catalog_subscription',
                'from pg_database catalog_database',
                'from pg_language catalog_language',
                'from pg_extension catalog_extension')) {
            $source | Should Match ([regex]::Escape($qualifiedAlias))
        }
    }

    It 'requires successful V006 V007 and V008 Flyway history and advertises V008' {
        $source = [IO.File]::ReadAllText((Resolve-Path $modulePath))
        $source | Should Match ([regex]::Escape(
                "script = 'V006__j7_import_inbox.sql'"))
        $source | Should Match ([regex]::Escape(
                "script = 'V007__j7_import_purge_integrity.sql'"))
        $source | Should Match ([regex]::Escape(
                "script = 'V008__j7_import_upgrade_evidence_time_integrity.sql'"))
        $source | Should Match ([regex]::Escape(
                "J7ExpectedMigration = 'V008'"))
        $source | Should Match 'J7_EXPECTED_MIGRATIONS_V006_V007_V008_MISSING'
    }

    It 'pins the local primary default to the Compose baseline port 5433' {
        $backupSource = [IO.File]::ReadAllText((Resolve-Path (
                    Join-Path $PSScriptRoot '..\Backup-J7Imports.ps1')))
        $restoreSource = [IO.File]::ReadAllText((Resolve-Path (
                    Join-Path $PSScriptRoot '..\Restore-J7Imports.ps1')))
        $backupSource | Should Match '\[int\]\$Port = 5433'
        $restoreSource | Should Match '\[int\]\$PrimaryPort = 5433'
    }
}
