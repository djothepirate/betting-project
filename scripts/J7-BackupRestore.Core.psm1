Set-StrictMode -Version 3.0

$script:J7ExpectedMigration = 'V008'
$script:J7ManifestPurpose = 'INT-001_J7_FULL_DATABASE_BACKUP'
$script:J7ManifestFormatVersion = 1
$script:J7TableNames = @(
    'j7_import_receipt',
    'j7_import_payload',
    'j7_import_audit',
    'outbox_message',
    'j7_import_payload_tombstone'
)
$script:J7IsolationAcknowledgement = 'RESTORE INT-001 J7 TO ISOLATED TARGET'

if (-not ('J7BackupRestoreCancellationScope' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Threading;

public sealed class J7BackupRestoreCancellationScope : IDisposable
{
    private readonly CancellationTokenSource source = new CancellationTokenSource();
    private readonly ConsoleCancelEventHandler handler;
    private bool disposed;

    public J7BackupRestoreCancellationScope()
    {
        handler = OnCancelKeyPress;
        Console.CancelKeyPress += handler;
    }

    public CancellationToken Token => source.Token;

    private void OnCancelKeyPress(object sender, ConsoleCancelEventArgs args)
    {
        args.Cancel = true;
        source.Cancel();
    }

    public void Dispose()
    {
        if (disposed)
        {
            return;
        }
        Console.CancelKeyPress -= handler;
        source.Dispose();
        disposed = true;
    }
}
'@
}

function Get-J7ExpectedTableNames {
    return @($script:J7TableNames)
}

function Get-J7IsolationAcknowledgement {
    return $script:J7IsolationAcknowledgement
}

function Assert-J7PowerShellRuntime {
    if ($PSVersionTable.PSEdition -cne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7) {
        throw 'J7_RUNTIME_UNSUPPORTED'
    }
    $killTreeMethod = [Diagnostics.Process].GetMethod(
        'Kill',
        [Reflection.BindingFlags]::Instance -bor [Reflection.BindingFlags]::Public,
        $null,
        [Type[]]@([bool]),
        $null)
    if ($null -eq $killTreeMethod) {
        throw 'J7_PROCESS_TREE_CLEANUP_UNAVAILABLE'
    }
}

function Get-J7PathComparison {
    if ($IsWindows) {
        return [StringComparison]::OrdinalIgnoreCase
    }
    return [StringComparison]::Ordinal
}

function Assert-J7NotUncPath {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    if ($LiteralPath.StartsWith('\\', [StringComparison]::Ordinal) -or
        $LiteralPath.StartsWith('//', [StringComparison]::Ordinal)) {
        throw $FailureCode
    }
}

function Get-J7CanonicalExistingDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    Assert-J7NotUncPath -LiteralPath $LiteralPath -FailureCode $FailureCode
    if (-not [IO.Path]::IsPathFullyQualified($LiteralPath)) {
        throw $FailureCode
    }
    if (-not (Test-Path -LiteralPath $LiteralPath -PathType Container)) {
        throw $FailureCode
    }

    $item = Get-Item -LiteralPath $LiteralPath -Force
    $cursor = $item
    while ($null -ne $cursor) {
        if (($cursor.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'J7_REPARSE_POINT_REJECTED'
        }
        $cursor = $cursor.Parent
    }
    return [IO.Path]::GetFullPath($item.FullName).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar)
}

function Test-J7PathWithinRepository {
    param(
        [Parameter(Mandatory = $true)][string]$CandidatePath,
        [Parameter(Mandatory = $true)][string]$RepositoryPath
    )

    $comparison = Get-J7PathComparison
    if ($CandidatePath.Equals($RepositoryPath, $comparison)) {
        return $true
    }
    $prefix = $RepositoryPath + [IO.Path]::DirectorySeparatorChar
    return $CandidatePath.StartsWith($prefix, $comparison)
}

function Resolve-J7NewArchiveDestination {
    param(
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot
    )

    Assert-J7NotUncPath `
        -LiteralPath $Destination `
        -FailureCode 'J7_DESTINATION_UNC_PATH_REJECTED'
    if (-not [IO.Path]::IsPathFullyQualified($Destination)) {
        throw 'J7_DESTINATION_MUST_BE_ABSOLUTE'
    }
    $archivePath = [IO.Path]::GetFullPath($Destination)
    if (-not $archivePath.EndsWith('.age', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'J7_DESTINATION_EXTENSION_INVALID'
    }
    $destinationDirectory = [IO.Path]::GetDirectoryName($archivePath)
    $destinationDirectory = Get-J7CanonicalExistingDirectory `
        -LiteralPath $destinationDirectory `
        -FailureCode 'J7_DESTINATION_DIRECTORY_MUST_PREEXIST'
    $repositoryPath = Get-J7CanonicalExistingDirectory `
        -LiteralPath $RepositoryRoot `
        -FailureCode 'J7_REPOSITORY_ROOT_INVALID'
    if (Test-J7PathWithinRepository `
            -CandidatePath $destinationDirectory `
            -RepositoryPath $repositoryPath) {
        throw 'J7_DESTINATION_MUST_BE_OUTSIDE_REPOSITORY'
    }

    $archivePath = Join-Path $destinationDirectory ([IO.Path]::GetFileName($archivePath))
    $manifestPath = $archivePath + '.manifest.json'
    if ((Test-Path -LiteralPath $archivePath) -or
        (Test-Path -LiteralPath $manifestPath)) {
        throw 'J7_DESTINATION_ALREADY_EXISTS'
    }

    return [pscustomobject]@{
        ArchivePath = $archivePath
        ManifestPath = $manifestPath
        DirectoryPath = $destinationDirectory
    }
}

function Resolve-J7ExistingArchive {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot
    )

    Assert-J7NotUncPath `
        -LiteralPath $ArchivePath `
        -FailureCode 'J7_ARCHIVE_UNC_PATH_REJECTED'
    if (-not [IO.Path]::IsPathFullyQualified($ArchivePath)) {
        throw 'J7_ARCHIVE_PATH_MUST_BE_ABSOLUTE'
    }
    $canonicalArchive = [IO.Path]::GetFullPath($ArchivePath)
    if (-not $canonicalArchive.EndsWith('.age', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'J7_ARCHIVE_EXTENSION_INVALID'
    }
    $directory = Get-J7CanonicalExistingDirectory `
        -LiteralPath ([IO.Path]::GetDirectoryName($canonicalArchive)) `
        -FailureCode 'J7_ARCHIVE_DIRECTORY_INVALID'
    $repositoryPath = Get-J7CanonicalExistingDirectory `
        -LiteralPath $RepositoryRoot `
        -FailureCode 'J7_REPOSITORY_ROOT_INVALID'
    if (Test-J7PathWithinRepository `
            -CandidatePath $directory `
            -RepositoryPath $repositoryPath) {
        throw 'J7_ARCHIVE_MUST_BE_OUTSIDE_REPOSITORY'
    }

    $canonicalArchive = Join-Path $directory ([IO.Path]::GetFileName($canonicalArchive))
    $manifestPath = $canonicalArchive + '.manifest.json'
    foreach ($path in @($canonicalArchive, $manifestPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw 'J7_ARCHIVE_OR_MANIFEST_MISSING'
        }
        $item = Get-Item -LiteralPath $path -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'J7_REPARSE_POINT_REJECTED'
        }
    }

    return [pscustomobject]@{
        ArchivePath = $canonicalArchive
        ManifestPath = $manifestPath
        DirectoryPath = $directory
    }
}

function Resolve-J7Executable {
    param(
        [Parameter(Mandatory = $true)][string]$Candidate,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    Assert-J7NotUncPath `
        -LiteralPath $Candidate `
        -FailureCode 'J7_EXECUTABLE_UNC_PATH_REJECTED'
    if ([IO.Path]::IsPathFullyQualified($Candidate)) {
        $path = [IO.Path]::GetFullPath($Candidate)
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw $FailureCode
        }
        return $path
    }

    $command = Get-Command -Name $Candidate -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $command -or [string]::IsNullOrWhiteSpace($command.Source)) {
        throw $FailureCode
    }
    Assert-J7NotUncPath `
        -LiteralPath $command.Source `
        -FailureCode 'J7_EXECUTABLE_UNC_PATH_REJECTED'
    return [IO.Path]::GetFullPath($command.Source)
}

function Assert-J7PostgresEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username
    )

    if ($DatabaseHost -cne '127.0.0.1') {
        throw 'J7_POSTGRES_HOST_MUST_BE_LOOPBACK'
    }
    if ($Port -lt 1 -or $Port -gt 65535) {
        throw 'J7_POSTGRES_PORT_INVALID'
    }
    if ($Database -cnotmatch '^[A-Za-z_][A-Za-z0-9_$]{0,62}$') {
        throw 'J7_POSTGRES_DATABASE_INVALID'
    }
    if ($Username -cnotmatch '^[A-Za-z_][A-Za-z0-9_$.-]{0,62}$') {
        throw 'J7_POSTGRES_USERNAME_INVALID'
    }
}

function Assert-J7PgpassBoundary {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $pgpassFile = [Environment]::GetEnvironmentVariable(
        'PGPASSFILE',
        [EnvironmentVariableTarget]::Process)
    if ([string]::IsNullOrWhiteSpace($pgpassFile)) {
        # libpq may use its standard per-user pgpass location. --no-password
        # still prevents an unbounded or accidentally logged interactive prompt.
        return
    }
    Assert-J7NotUncPath `
        -LiteralPath $pgpassFile `
        -FailureCode 'J7_PGPASSFILE_UNC_PATH_REJECTED'
    if (-not [IO.Path]::IsPathFullyQualified($pgpassFile) -or
        -not (Test-Path -LiteralPath $pgpassFile -PathType Leaf)) {
        throw 'J7_PGPASSFILE_BOUNDARY_INVALID'
    }
    $item = Get-Item -LiteralPath $pgpassFile -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'J7_PGPASSFILE_BOUNDARY_INVALID'
    }
    $parent = Get-J7CanonicalExistingDirectory `
        -LiteralPath $item.DirectoryName `
        -FailureCode 'J7_PGPASSFILE_BOUNDARY_INVALID'
    $repository = Get-J7CanonicalExistingDirectory `
        -LiteralPath $RepositoryRoot `
        -FailureCode 'J7_REPOSITORY_ROOT_INVALID'
    if (Test-J7PathWithinRepository `
            -CandidatePath $parent `
            -RepositoryPath $repository) {
        throw 'J7_PGPASSFILE_MUST_BE_OUTSIDE_REPOSITORY'
    }
}

function Assert-J7IsolatedRestoreTarget {
    param(
        [Parameter(Mandatory = $true)][string]$PrimaryHost,
        [Parameter(Mandatory = $true)][int]$PrimaryPort,
        [Parameter(Mandatory = $true)][string]$PrimaryDatabase,
        [Parameter(Mandatory = $true)][string]$TargetHost,
        [Parameter(Mandatory = $true)][int]$TargetPort,
        [Parameter(Mandatory = $true)][string]$TargetDatabase,
        [Parameter(Mandatory = $true)][string]$IsolationAcknowledgement
    )

    if ($PrimaryHost -cne '127.0.0.1' -or $TargetHost -cne '127.0.0.1') {
        throw 'J7_RESTORE_ENDPOINTS_MUST_BE_LOOPBACK'
    }
    if ($PrimaryPort -lt 1 -or $PrimaryPort -gt 65535 -or
        $TargetPort -lt 1 -or $TargetPort -gt 65535) {
        throw 'J7_RESTORE_PORT_INVALID'
    }
    if ($PrimaryDatabase -cnotmatch '^[A-Za-z_][A-Za-z0-9_$]{0,62}$' -or
        $TargetDatabase -cnotmatch '^int001_j7_restore_[a-z0-9_]{1,40}$') {
        throw 'J7_RESTORE_DATABASE_IDENTITY_INVALID'
    }
    if ($PrimaryPort -eq $TargetPort -or $PrimaryDatabase -ceq $TargetDatabase) {
        throw 'J7_RESTORE_TARGET_NOT_ISOLATED'
    }
    if ($IsolationAcknowledgement -cne $script:J7IsolationAcknowledgement) {
        throw 'J7_RESTORE_ACKNOWLEDGEMENT_INVALID'
    }
}

function New-J7ProcessStartInfo {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][bool]$RedirectInput,
        [Parameter(Mandatory = $true)][bool]$RedirectOutput,
        [Parameter(Mandatory = $true)][bool]$RedirectError,
        [Parameter(Mandatory = $true)][hashtable]$Environment
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $false
    $startInfo.RedirectStandardInput = $RedirectInput
    $startInfo.RedirectStandardOutput = $RedirectOutput
    $startInfo.RedirectStandardError = $RedirectError
    if ($RedirectOutput) {
        $startInfo.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    }
    if ($RedirectError) {
        $startInfo.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    }
    foreach ($argument in $ArgumentList) {
        [void]$startInfo.ArgumentList.Add([string]$argument)
    }
    foreach ($entry in $Environment.GetEnumerator()) {
        $startInfo.Environment[[string]$entry.Key] = [string]$entry.Value
    }
    # Never allow ambient or caller-provided connection selectors and passwords
    # to override the explicit CLI endpoint or the native age prompt. PGPASSFILE
    # is deliberately retained only after Assert-J7PgpassBoundary has validated
    # its location; libpq may otherwise use its standard per-user password file.
    foreach ($variableName in @(
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
            'PGDATESTYLE')) {
        [void]$startInfo.Environment.Remove($variableName)
    }
    return $startInfo
}

function Stop-J7ProcessTree {
    param(
        [Diagnostics.Process]$Process,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    if ($null -eq $Process) {
        return
    }
    try {
        if (-not $Process.HasExited) {
            $Process.Kill($true)
        }
    }
    catch [InvalidOperationException] {
        # The process exited between the identity check and the kill request.
    }
    catch {
        throw 'J7_PROCESS_TREE_CLEANUP_UNCONFIRMED'
    }
    try {
        if (-not $Process.WaitForExit($CleanupTimeoutMilliseconds)) {
            throw 'J7_PROCESS_TREE_CLEANUP_UNCONFIRMED'
        }
    }
    catch [InvalidOperationException] {
        # A process that never started has no tree to clean.
    }
}

function Invoke-J7NativeCapture {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList,
        [Parameter(Mandatory = $true)][hashtable]$Environment,
        [ValidateRange(1, 3600)][int]$TimeoutSeconds = 300,
        [ValidateRange(100, 30000)][int]$CleanupTimeoutMilliseconds = 5000
    )

    Assert-J7PowerShellRuntime
    $scope = [J7BackupRestoreCancellationScope]::new()
    $process = $null
    $stdoutTask = $null
    $stderrTask = $null
    $failure = $null
    $stdout = $null
    try {
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = New-J7ProcessStartInfo `
            -FilePath $FilePath `
            -ArgumentList $ArgumentList `
            -RedirectInput $false `
            -RedirectOutput $true `
            -RedirectError $true `
            -Environment $Environment
        if (-not $process.Start()) {
            throw 'J7_NATIVE_START_FAILED'
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while (-not $process.HasExited) {
            if ($scope.Token.IsCancellationRequested) {
                throw 'J7_NATIVE_CANCELLED'
            }
            if ([DateTime]::UtcNow -ge $deadline) {
                throw 'J7_NATIVE_TIMEOUT'
            }
            [void]$process.WaitForExit(50)
        }
        if ($process.ExitCode -ne 0) {
            throw 'J7_NATIVE_COMMAND_FAILED'
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        [void]$stderrTask.GetAwaiter().GetResult()
        if ($stdout.Length -gt 1048576) {
            throw 'J7_NATIVE_OUTPUT_LIMIT_EXCEEDED'
        }
    }
    catch {
        $failure = $_.Exception
    }
    finally {
        try {
            Stop-J7ProcessTree `
                -Process $process `
                -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
        }
        catch {
            $failure = $_.Exception
        }
        if ($null -ne $process) {
            $process.Dispose()
        }
        $scope.Dispose()
    }
    if ($null -ne $failure) {
        throw $failure
    }
    return $stdout.Trim()
}

function Assert-J7PostgresClient17 {
    param(
        [Parameter(Mandatory = $true)][string]$ExecutablePath,
        [Parameter(Mandatory = $true)]
        [ValidateSet('pg_dump', 'pg_restore', 'psql')]
        [string]$ToolName,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $version = Invoke-J7NativeCapture `
        -FilePath $ExecutablePath `
        -ArgumentList @('--version') `
        -Environment @{} `
        -TimeoutSeconds 10 `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    if ($version -cnotmatch (
            '^' + [regex]::Escape($ToolName) +
            ' \(PostgreSQL\) 17(?:\.[0-9]+)+(?:\s.*)?$')) {
        throw 'J7_POSTGRES_CLIENT_17_REQUIRED'
    }
}

function Get-J7AgeVersion {
    param(
        [Parameter(Mandatory = $true)][string]$ExecutablePath,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $version = Invoke-J7NativeCapture `
        -FilePath $ExecutablePath `
        -ArgumentList @('--version') `
        -Environment @{} `
        -TimeoutSeconds 10 `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    if ($version -cnotmatch '^v?[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$' -or
        $version.Length -gt 64) {
        throw 'J7_AGE_VERSION_INVALID'
    }
    return $version
}

function Invoke-J7NativeBinaryPipeline {
    param(
        [Parameter(Mandatory = $true)][string]$ProducerFilePath,
        [Parameter(Mandatory = $true)][string[]]$ProducerArgumentList,
        [Parameter(Mandatory = $true)][string]$ConsumerFilePath,
        [Parameter(Mandatory = $true)][string[]]$ConsumerArgumentList,
        [Parameter(Mandatory = $true)][hashtable]$ProducerEnvironment,
        [Parameter(Mandatory = $true)][hashtable]$ConsumerEnvironment,
        [bool]$ProducerErrorToConsole = $false,
        [bool]$ConsumerErrorToConsole = $false,
        [ValidateRange(1, 3600)][int]$TimeoutSeconds = 300,
        [ValidateRange(100, 30000)][int]$CleanupTimeoutMilliseconds = 5000
    )

    Assert-J7PowerShellRuntime
    $scope = [J7BackupRestoreCancellationScope]::new()
    $copyCancellation = [Threading.CancellationTokenSource]::CreateLinkedTokenSource(
        $scope.Token)
    $producer = $null
    $consumer = $null
    $producerErrorTask = $null
    $consumerOutputTask = $null
    $consumerErrorTask = $null
    $copyTask = $null
    $consumerInputClosed = $false
    $failure = $null
    try {
        $consumer = [Diagnostics.Process]::new()
        $consumer.StartInfo = New-J7ProcessStartInfo `
            -FilePath $ConsumerFilePath `
            -ArgumentList $ConsumerArgumentList `
            -RedirectInput $true `
            -RedirectOutput $true `
            -RedirectError (-not $ConsumerErrorToConsole) `
            -Environment $ConsumerEnvironment
        if (-not $consumer.Start()) {
            throw 'J7_PIPELINE_CONSUMER_START_FAILED'
        }
        $consumerOutputTask = $consumer.StandardOutput.ReadToEndAsync()
        if (-not $ConsumerErrorToConsole) {
            $consumerErrorTask = $consumer.StandardError.ReadToEndAsync()
        }

        $producer = [Diagnostics.Process]::new()
        $producer.StartInfo = New-J7ProcessStartInfo `
            -FilePath $ProducerFilePath `
            -ArgumentList $ProducerArgumentList `
            -RedirectInput $false `
            -RedirectOutput $true `
            -RedirectError (-not $ProducerErrorToConsole) `
            -Environment $ProducerEnvironment
        if (-not $producer.Start()) {
            throw 'J7_PIPELINE_PRODUCER_START_FAILED'
        }
        if (-not $ProducerErrorToConsole) {
            $producerErrorTask = $producer.StandardError.ReadToEndAsync()
        }
        $copyTask = $producer.StandardOutput.BaseStream.CopyToAsync(
            $consumer.StandardInput.BaseStream,
            81920,
            $copyCancellation.Token)

        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ($true) {
            if ($scope.Token.IsCancellationRequested) {
                throw 'J7_PIPELINE_CANCELLED'
            }
            if ([DateTime]::UtcNow -ge $deadline) {
                throw 'J7_PIPELINE_TIMEOUT'
            }
            if ($copyTask.IsFaulted) {
                [void]$copyTask.GetAwaiter().GetResult()
            }
            if ($producer.HasExited -and $copyTask.IsCompleted -and
                -not $consumerInputClosed) {
                $consumer.StandardInput.Close()
                $consumerInputClosed = $true
            }
            if ($consumer.HasExited -and -not $producer.HasExited) {
                throw 'J7_PIPELINE_CONSUMER_FAILED'
            }
            if ($producer.HasExited -and $consumer.HasExited -and
                $copyTask.IsCompleted) {
                break
            }
            Start-Sleep -Milliseconds 25
        }

        [void]$copyTask.GetAwaiter().GetResult()
        if (-not $consumerInputClosed) {
            $consumer.StandardInput.Close()
            $consumerInputClosed = $true
        }
        if ($null -ne $producerErrorTask) {
            [void]$producerErrorTask.GetAwaiter().GetResult()
        }
        [void]$consumerOutputTask.GetAwaiter().GetResult()
        if ($null -ne $consumerErrorTask) {
            [void]$consumerErrorTask.GetAwaiter().GetResult()
        }
        if ($producer.ExitCode -ne 0 -or $consumer.ExitCode -ne 0) {
            throw 'J7_NATIVE_PIPELINE_FAILED'
        }
    }
    catch {
        $failure = $_.Exception
    }
    finally {
        $copyCancellation.Cancel()
        if ($null -ne $consumer -and -not $consumerInputClosed) {
            try {
                $consumer.StandardInput.Close()
            }
            catch {
                # Process-tree cleanup below is authoritative.
            }
        }
        foreach ($process in @($producer, $consumer)) {
            try {
                Stop-J7ProcessTree `
                    -Process $process `
                    -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
            }
            catch {
                $failure = $_.Exception
            }
        }
        foreach ($process in @($producer, $consumer)) {
            if ($null -ne $process) {
                $process.Dispose()
            }
        }
        $copyCancellation.Dispose()
        $scope.Dispose()
    }
    if ($null -ne $failure) {
        throw $failure
    }
}

function New-J7PostgresEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$ApplicationName,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    return @{
        PGAPPNAME = $ApplicationName
        PGCLIENTENCODING = 'UTF8'
        PGCONNECT_TIMEOUT = ([Math]::Min($TimeoutSeconds, 30)).ToString(
            [Globalization.CultureInfo]::InvariantCulture)
        PGOPTIONS = '-c statement_timeout=' +
            ($TimeoutSeconds * 1000).ToString([Globalization.CultureInfo]::InvariantCulture) +
            ' -c bytea_output=hex' +
            ' -c TimeZone=UTC' +
            ' -c DateStyle=ISO,YMD'
    }
}

function Invoke-J7PsqlScalar {
    param(
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Sql,
        [ValidateRange(1, 3600)][int]$TimeoutSeconds,
        [ValidateRange(100, 30000)][int]$CleanupTimeoutMilliseconds
    )

    $applicationName = 'int001_j7_evidence_' + [Guid]::NewGuid().ToString('N')
    $arguments = @(
        '--no-password',
        '--no-psqlrc',
        '--quiet',
        '--tuples-only',
        '--no-align',
        '--set=ON_ERROR_STOP=1',
        "--host=$DatabaseHost",
        "--port=$Port",
        "--username=$Username",
        "--dbname=$Database",
        "--command=$Sql"
    )
    $environment = New-J7PostgresEnvironment `
        -ApplicationName $applicationName `
        -TimeoutSeconds $TimeoutSeconds
    return Invoke-J7NativeCapture `
        -FilePath $PsqlPath `
        -ArgumentList $arguments `
        -Environment $environment `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
}

function Assert-J7ExpectedSchema {
    param(
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $sql = @"
select case when
    exists (
        select 1
        from public.flyway_schema_history
        where success
          and version in ('006', '6')
          and script = 'V006__j7_import_inbox.sql'
    )
    and exists (
        select 1
        from public.flyway_schema_history
        where success
          and version in ('007', '7')
          and script = 'V007__j7_import_purge_integrity.sql'
    )
    and exists (
        select 1
        from public.flyway_schema_history
        where success
          and version in ('008', '8')
          and script = 'V008__j7_import_upgrade_evidence_time_integrity.sql'
    )
    and to_regclass('public.j7_import_receipt') is not null
    and to_regclass('public.j7_import_payload') is not null
    and to_regclass('public.j7_import_audit') is not null
    and to_regclass('public.outbox_message') is not null
    and to_regclass('public.j7_import_payload_tombstone') is not null
then '$($script:J7ExpectedMigration)' else 'INVALID' end
"@
    $result = Invoke-J7PsqlScalar `
        -PsqlPath $PsqlPath `
        -DatabaseHost $DatabaseHost `
        -Port $Port `
        -Database $Database `
        -Username $Username `
        -Sql $sql `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    if ($result -cne $script:J7ExpectedMigration) {
        throw 'J7_EXPECTED_MIGRATIONS_V006_V007_V008_MISSING'
    }
}

function Assert-J7PayloadHashIntegrity {
    param(
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $sql = @'
select count(*)
from public.j7_import_payload payload
join public.j7_import_receipt receipt on receipt.id = payload.import_id
where payload.file_sha256 <> receipt.file_sha256
   or payload.payload_size_bytes <> receipt.payload_size_bytes
   or octet_length(payload.payload) <> payload.payload_size_bytes
   or encode(sha256(payload.payload), 'hex') <> payload.file_sha256
'@
    $result = Invoke-J7PsqlScalar `
        -PsqlPath $PsqlPath `
        -DatabaseHost $DatabaseHost `
        -Port $Port `
        -Database $Database `
        -Username $Username `
        -Sql $sql `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    if ($result -cne '0') {
        throw 'J7_PAYLOAD_HASH_INTEGRITY_FAILED'
    }
}

function Assert-J7Postgres17 {
    param(
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $scalar = Invoke-J7PsqlScalar `
        -PsqlPath $PsqlPath `
        -DatabaseHost $DatabaseHost `
        -Port $Port `
        -Database $Database `
        -Username $Username `
        -Sql "select current_setting('server_version_num')" `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    [int]$versionNumber = 0
    if (-not [int]::TryParse(
            $scalar,
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$versionNumber) -or
        $versionNumber -lt 170000 -or $versionNumber -ge 180000) {
        throw 'J7_POSTGRES_17_REQUIRED'
    }
}

function Assert-J7FreshRestoreDatabase {
    param(
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $sql = @'
select case when
    not exists (
        select 1
        from pg_namespace namespace
        where namespace.nspname not in ('pg_catalog', 'information_schema', 'public')
          and namespace.nspname not like 'pg_toast%'
          and namespace.nspname not like 'pg_temp_%'
    )
    and not exists (
        select 1
        from pg_class relation
        join pg_namespace namespace on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
    )
    and not exists (
        select 1
        from pg_proc routine
        join pg_namespace namespace on namespace.oid = routine.pronamespace
        where namespace.nspname = 'public'
    )
    and not exists (
        select 1
        from pg_type data_type
        join pg_namespace namespace on namespace.oid = data_type.typnamespace
        where namespace.nspname = 'public'
    )
    and not exists (
        select 1
        from pg_collation catalog_collation
        join pg_namespace namespace
          on namespace.oid = catalog_collation.collnamespace
        where namespace.nspname = 'public'
    )
    and not exists (
        select 1
        from pg_largeobject_metadata
    )
    and not exists (
        select 1
        from pg_publication
    )
    and not exists (
        select 1
        from pg_subscription catalog_subscription
        where catalog_subscription.subdbid = (
            select catalog_database.oid
            from pg_database catalog_database
            where catalog_database.datname = current_database()
        )
    )
    and not exists (
        select 1
        from pg_event_trigger
    )
    and not exists (
        select 1
        from pg_foreign_data_wrapper
    )
    and not exists (
        select 1
        from pg_foreign_server
    )
    and not exists (
        select 1
        from pg_user_mappings
    )
    and not exists (
        select 1
        from pg_default_acl
    )
    and not exists (
        select 1
        from pg_seclabel
    )
    and not exists (
        select 1
        from pg_language catalog_language
        where catalog_language.lanname not in ('internal', 'c', 'sql', 'plpgsql')
    )
    and not exists (select 1 from pg_cast where oid >= 16384)
    and not exists (select 1 from pg_conversion where oid >= 16384)
    and not exists (select 1 from pg_operator where oid >= 16384)
    and not exists (select 1 from pg_opclass where oid >= 16384)
    and not exists (select 1 from pg_opfamily where oid >= 16384)
    and not exists (select 1 from pg_am where oid >= 16384)
    and not exists (select 1 from pg_transform where oid >= 16384)
    and not exists (select 1 from pg_ts_config where oid >= 16384)
    and not exists (select 1 from pg_ts_dict where oid >= 16384)
    and not exists (select 1 from pg_ts_parser where oid >= 16384)
    and not exists (select 1 from pg_ts_template where oid >= 16384)
    and not exists (
        select 1
        from pg_extension catalog_extension
        where catalog_extension.extname <> 'plpgsql'
    )
then 'EMPTY' else 'NOT_EMPTY' end
'@
    $result = Invoke-J7PsqlScalar `
        -PsqlPath $PsqlPath `
        -DatabaseHost $DatabaseHost `
        -Port $Port `
        -Database $Database `
        -Username $Username `
        -Sql $sql `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    if ($result -cne 'EMPTY') {
        throw 'J7_RESTORE_TARGET_DATABASE_NOT_FRESH'
    }
}

function ConvertFrom-J7EvidenceScalar {
    param(
        [Parameter(Mandatory = $true)][string]$TableName,
        [Parameter(Mandatory = $true)][string]$Scalar
    )

    if ($TableName -cnotin $script:J7TableNames -or
        $Scalar -cnotmatch '^(0|[1-9][0-9]*)\|([0-9a-f]{64})$') {
        throw 'J7_TABLE_EVIDENCE_INVALID'
    }
    [long]$rowCount = 0
    if (-not [long]::TryParse(
            $Matches[1],
            [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$rowCount)) {
        throw 'J7_TABLE_EVIDENCE_INVALID'
    }
    return [pscustomobject][ordered]@{
        name = $TableName
        rowCount = $rowCount
        contentSha256 = $Matches[2]
    }
}

function Get-J7TableEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    $evidence = [Collections.Generic.List[object]]::new()
    foreach ($tableName in $script:J7TableNames) {
        $sql = @"
with row_digests as (
    select encode(
        sha256(convert_to(to_jsonb(source_row)::text, 'UTF8')),
        'hex') as row_hash
    from public.$tableName source_row
)
select count(*)::text || '|' || encode(
    sha256(convert_to(
        coalesce(string_agg(row_hash, E'\n' order by row_hash), ''),
        'UTF8')),
    'hex')
from row_digests
"@
        $scalar = Invoke-J7PsqlScalar `
            -PsqlPath $PsqlPath `
            -DatabaseHost $DatabaseHost `
            -Port $Port `
            -Database $Database `
            -Username $Username `
            -Sql $sql `
            -TimeoutSeconds $TimeoutSeconds `
            -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
        $evidence.Add((ConvertFrom-J7EvidenceScalar `
                    -TableName $tableName `
                    -Scalar $scalar))
    }
    return @($evidence)
}

function Assert-J7EvidenceEqual {
    param(
        [Parameter(Mandatory = $true)][object[]]$Expected,
        [Parameter(Mandatory = $true)][object[]]$Actual,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    if ($Expected.Count -ne $script:J7TableNames.Count -or
        $Actual.Count -ne $script:J7TableNames.Count) {
        throw $FailureCode
    }
    for ($index = 0; $index -lt $script:J7TableNames.Count; $index++) {
        if ($Expected[$index].name -cne $script:J7TableNames[$index] -or
            $Actual[$index].name -cne $script:J7TableNames[$index] -or
            [long]$Expected[$index].rowCount -ne [long]$Actual[$index].rowCount -or
            $Expected[$index].contentSha256 -cne $Actual[$index].contentSha256) {
            throw $FailureCode
        }
    }
}

function Assert-J7NoDuplicateJsonProperties {
    param([Parameter(Mandatory = $true)][Text.Json.JsonElement]$Element)

    if ($Element.ValueKind -eq [Text.Json.JsonValueKind]::Object) {
        $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) {
                throw 'J7_MANIFEST_DUPLICATE_PROPERTY'
            }
            Assert-J7NoDuplicateJsonProperties -Element $property.Value
        }
    }
    elseif ($Element.ValueKind -eq [Text.Json.JsonValueKind]::Array) {
        foreach ($item in $Element.EnumerateArray()) {
            Assert-J7NoDuplicateJsonProperties -Element $item
        }
    }
}

function Assert-J7JsonPropertiesExact {
    param(
        [Parameter(Mandatory = $true)][Text.Json.JsonElement]$Element,
        [Parameter(Mandatory = $true)][string[]]$ExpectedNames
    )

    if ($Element.ValueKind -ne [Text.Json.JsonValueKind]::Object) {
        throw 'J7_MANIFEST_SHAPE_INVALID'
    }
    $actual = [Collections.Generic.List[string]]::new()
    foreach ($property in $Element.EnumerateObject()) {
        $actual.Add($property.Name)
    }
    if ($actual.Count -ne $ExpectedNames.Count) {
        throw 'J7_MANIFEST_PROPERTIES_INVALID'
    }
    foreach ($name in $ExpectedNames) {
        if ($name -cnotin $actual) {
            throw 'J7_MANIFEST_PROPERTIES_INVALID'
        }
    }
}

function Read-J7Manifest {
    param([Parameter(Mandatory = $true)][string]$ManifestPath)

    $item = Get-Item -LiteralPath $ManifestPath -Force
    if ($item.Length -lt 1 -or $item.Length -gt 131072) {
        throw 'J7_MANIFEST_SIZE_INVALID'
    }
    $encoding = [Text.UTF8Encoding]::new($false, $true)
    try {
        $json = $encoding.GetString([IO.File]::ReadAllBytes($item.FullName))
        $document = [Text.Json.JsonDocument]::Parse($json)
    }
    catch {
        throw 'J7_MANIFEST_JSON_INVALID'
    }

    try {
        $root = $document.RootElement
        Assert-J7NoDuplicateJsonProperties -Element $root
        Assert-J7JsonPropertiesExact -Element $root -ExpectedNames @(
            'formatVersion', 'purpose', 'backupScope', 'postgresMajorVersion',
            'createdAtUtc',
            'encryptedArchiveFileName', 'cipherSha256', 'cipherBytes',
            'requiredMigration', 'tables', 'j7EvidenceStableAcrossDump',
            'encryption', 'restorePolicy')

        [int]$formatVersion = 0
        [int]$postgresMajorVersion = 0
        [long]$cipherBytes = 0
        if (-not $root.GetProperty('formatVersion').TryGetInt32([ref]$formatVersion) -or
            $formatVersion -ne $script:J7ManifestFormatVersion -or
            $root.GetProperty('purpose').GetString() -cne $script:J7ManifestPurpose -or
            $root.GetProperty('backupScope').GetString() -cne 'full-database' -or
            -not $root.GetProperty('postgresMajorVersion').TryGetInt32(
                [ref]$postgresMajorVersion) -or
            $postgresMajorVersion -ne 17 -or
            $root.GetProperty('requiredMigration').GetString() -cne $script:J7ExpectedMigration -or
            -not $root.GetProperty('cipherBytes').TryGetInt64([ref]$cipherBytes) -or
            $cipherBytes -lt 1) {
            throw 'J7_MANIFEST_CONTRACT_INVALID'
        }

        $createdAtUtc = $root.GetProperty('createdAtUtc').GetString()
        $archiveName = $root.GetProperty('encryptedArchiveFileName').GetString()
        $cipherSha256 = $root.GetProperty('cipherSha256').GetString()
        if ($createdAtUtc -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{7}Z$' -or
            [string]::IsNullOrWhiteSpace($archiveName) -or
            [IO.Path]::GetFileName($archiveName) -cne $archiveName -or
            -not $archiveName.EndsWith('.age', [StringComparison]::OrdinalIgnoreCase) -or
            $cipherSha256 -cnotmatch '^[0-9a-f]{64}$') {
            throw 'J7_MANIFEST_CONTRACT_INVALID'
        }

        $stableElement = $root.GetProperty('j7EvidenceStableAcrossDump')
        if ($stableElement.ValueKind -ne [Text.Json.JsonValueKind]::True) {
            throw 'J7_MANIFEST_CONTRACT_INVALID'
        }

        $encryption = $root.GetProperty('encryption')
        Assert-J7JsonPropertiesExact -Element $encryption -ExpectedNames @(
            'format', 'tool', 'toolVersion', 'secretMaterialPersisted')
        $ageVersion = $encryption.GetProperty('toolVersion').GetString()
        if ($encryption.GetProperty('format').GetString() -cne 'age-passphrase' -or
            $encryption.GetProperty('tool').GetString() -cne 'age' -or
            $ageVersion -cnotmatch '^v?[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$' -or
            $ageVersion.Length -gt 64 -or
            $encryption.GetProperty('secretMaterialPersisted').ValueKind -ne
                [Text.Json.JsonValueKind]::False) {
            throw 'J7_MANIFEST_ENCRYPTION_INVALID'
        }

        $restorePolicy = $root.GetProperty('restorePolicy')
        Assert-J7JsonPropertiesExact -Element $restorePolicy -ExpectedNames @(
            'isolatedLoopbackTargetRequired', 'primaryMutationAllowed')
        if ($restorePolicy.GetProperty('isolatedLoopbackTargetRequired').ValueKind -ne
                [Text.Json.JsonValueKind]::True -or
            $restorePolicy.GetProperty('primaryMutationAllowed').ValueKind -ne
                [Text.Json.JsonValueKind]::False) {
            throw 'J7_MANIFEST_RESTORE_POLICY_INVALID'
        }

        $tablesElement = $root.GetProperty('tables')
        if ($tablesElement.ValueKind -ne [Text.Json.JsonValueKind]::Array -or
            $tablesElement.GetArrayLength() -ne $script:J7TableNames.Count) {
            throw 'J7_MANIFEST_TABLES_INVALID'
        }
        $tables = [Collections.Generic.List[object]]::new()
        $tableIndex = 0
        foreach ($tableElement in $tablesElement.EnumerateArray()) {
            Assert-J7JsonPropertiesExact -Element $tableElement -ExpectedNames @(
                'name', 'rowCount', 'contentSha256')
            [long]$rowCount = 0
            $name = $tableElement.GetProperty('name').GetString()
            $contentSha256 = $tableElement.GetProperty('contentSha256').GetString()
            if ($name -cne $script:J7TableNames[$tableIndex] -or
                -not $tableElement.GetProperty('rowCount').TryGetInt64([ref]$rowCount) -or
                $rowCount -lt 0 -or
                $contentSha256 -cnotmatch '^[0-9a-f]{64}$') {
                throw 'J7_MANIFEST_TABLES_INVALID'
            }
            $tables.Add([pscustomobject][ordered]@{
                    name = $name
                    rowCount = $rowCount
                    contentSha256 = $contentSha256
                })
            $tableIndex++
        }

        return [pscustomobject][ordered]@{
            formatVersion = $formatVersion
            purpose = $script:J7ManifestPurpose
            backupScope = 'full-database'
            postgresMajorVersion = $postgresMajorVersion
            createdAtUtc = $createdAtUtc
            encryptedArchiveFileName = $archiveName
            cipherSha256 = $cipherSha256
            cipherBytes = $cipherBytes
            requiredMigration = $script:J7ExpectedMigration
            ageVersion = $ageVersion
            tables = @($tables)
            j7EvidenceStableAcrossDump = $true
        }
    }
    finally {
        $document.Dispose()
    }
}

function Invoke-J7Backup {
    param(
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$DatabaseHost,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$PgDumpPath,
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$AgePath,
        [Parameter(Mandatory = $true)][ValidateSet('Interactive', 'AgeGenerated')]
        [string]$PassphraseMode,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    Assert-J7PowerShellRuntime
    Assert-J7PostgresEndpoint `
        -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username
    Assert-J7PgpassBoundary -RepositoryRoot $RepositoryRoot
    $destinationInfo = Resolve-J7NewArchiveDestination `
        -Destination $Destination `
        -RepositoryRoot $RepositoryRoot
    $pgDumpExecutable = Resolve-J7Executable `
        -Candidate $PgDumpPath -FailureCode 'J7_PG_DUMP_NOT_FOUND'
    $psqlExecutable = Resolve-J7Executable `
        -Candidate $PsqlPath -FailureCode 'J7_PSQL_NOT_FOUND'
    $ageExecutable = Resolve-J7Executable `
        -Candidate $AgePath -FailureCode 'J7_AGE_NOT_FOUND'
    Assert-J7PostgresClient17 `
        -ExecutablePath $pgDumpExecutable `
        -ToolName 'pg_dump' `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    Assert-J7PostgresClient17 `
        -ExecutablePath $psqlExecutable `
        -ToolName 'psql' `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    $ageVersion = Get-J7AgeVersion `
        -ExecutablePath $ageExecutable `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

    Assert-J7Postgres17 `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    Assert-J7ExpectedSchema `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    Assert-J7PayloadHashIntegrity `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    $beforeEvidence = @(Get-J7TableEvidence `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds)

    $partialArchive = Join-Path $destinationInfo.DirectoryPath `
        ([IO.Path]::GetFileName($destinationInfo.ArchivePath) +
            '.partial-' + [Guid]::NewGuid().ToString('N'))
    $partialManifest = $destinationInfo.ManifestPath +
        '.partial-' + [Guid]::NewGuid().ToString('N')
    $publishedArchive = $false
    $publishedManifest = $false
    try {
        $dumpArguments = @(
            "--host=$DatabaseHost",
            "--port=$Port",
            "--username=$Username",
            "--dbname=$Database",
            '--no-password',
            '--format=custom',
            '--compress=9',
            '--no-owner',
            '--no-privileges',
            '--serializable-deferrable'
        )
        $postgresEnvironment = New-J7PostgresEnvironment `
            -ApplicationName ('int001_j7_backup_' + [Guid]::NewGuid().ToString('N')) `
            -TimeoutSeconds $TimeoutSeconds
        $ageArguments = @('--passphrase', '--output', $partialArchive)
        Write-Host "INT001_J7_PASSPHRASE_MODE=$($PassphraseMode.ToUpperInvariant())"
        Write-Host 'INT001_J7_PASSPHRASE_HANDLING=NATIVE_AGE_PROMPT_ONLY'
        if ($PassphraseMode -ceq 'AgeGenerated') {
            Write-Host 'INT001_J7_AGE_PROMPT_ACTION=LEAVE_EMPTY_FOR_NATIVE_GENERATION'
        }
        Invoke-J7NativeBinaryPipeline `
            -ProducerFilePath $pgDumpExecutable `
            -ProducerArgumentList $dumpArguments `
            -ConsumerFilePath $ageExecutable `
            -ConsumerArgumentList $ageArguments `
            -ProducerEnvironment $postgresEnvironment `
            -ConsumerEnvironment @{} `
            -ConsumerErrorToConsole $true `
            -TimeoutSeconds $TimeoutSeconds `
            -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

        if (-not (Test-Path -LiteralPath $partialArchive -PathType Leaf) -or
            (Get-Item -LiteralPath $partialArchive).Length -lt 1) {
            throw 'J7_ENCRYPTED_ARCHIVE_NOT_CREATED'
        }

        Assert-J7ExpectedSchema `
            -PsqlPath $psqlExecutable `
            -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
            -TimeoutSeconds $TimeoutSeconds `
            -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
        Assert-J7PayloadHashIntegrity `
            -PsqlPath $psqlExecutable `
            -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
            -TimeoutSeconds $TimeoutSeconds `
            -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
        $afterEvidence = @(Get-J7TableEvidence `
            -PsqlPath $psqlExecutable `
            -DatabaseHost $DatabaseHost -Port $Port -Database $Database -Username $Username `
            -TimeoutSeconds $TimeoutSeconds `
            -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds)
        Assert-J7EvidenceEqual `
            -Expected $beforeEvidence `
            -Actual $afterEvidence `
            -FailureCode 'J7_SOURCE_CHANGED_DURING_BACKUP'

        $cipherItem = Get-Item -LiteralPath $partialArchive
        $cipherSha256 = (Get-FileHash `
                -LiteralPath $partialArchive `
                -Algorithm SHA256).Hash.ToLowerInvariant()
        $manifest = [ordered]@{
            formatVersion = $script:J7ManifestFormatVersion
            purpose = $script:J7ManifestPurpose
            backupScope = 'full-database'
            postgresMajorVersion = 17
            createdAtUtc = [DateTime]::UtcNow.ToString(
                'yyyy-MM-ddTHH:mm:ss.fffffffZ',
                [Globalization.CultureInfo]::InvariantCulture)
            encryptedArchiveFileName = [IO.Path]::GetFileName(
                $destinationInfo.ArchivePath)
            cipherSha256 = $cipherSha256
            cipherBytes = [long]$cipherItem.Length
            requiredMigration = $script:J7ExpectedMigration
            tables = $beforeEvidence
            j7EvidenceStableAcrossDump = $true
            encryption = [ordered]@{
                format = 'age-passphrase'
                tool = 'age'
                toolVersion = $ageVersion
                secretMaterialPersisted = $false
            }
            restorePolicy = [ordered]@{
                isolatedLoopbackTargetRequired = $true
                primaryMutationAllowed = $false
            }
        }
        $manifestJson = $manifest | ConvertTo-Json -Depth 6
        [IO.File]::WriteAllText(
            $partialManifest,
            $manifestJson,
            [Text.UTF8Encoding]::new($false))

        if ((Test-Path -LiteralPath $destinationInfo.ArchivePath) -or
            (Test-Path -LiteralPath $destinationInfo.ManifestPath)) {
            throw 'J7_DESTINATION_RACE_DETECTED'
        }
        [IO.File]::Move($partialArchive, $destinationInfo.ArchivePath)
        $publishedArchive = $true
        [IO.File]::Move($partialManifest, $destinationInfo.ManifestPath)
        $publishedManifest = $true

        $manifestSha256 = (Get-FileHash `
                -LiteralPath $destinationInfo.ManifestPath `
                -Algorithm SHA256).Hash.ToLowerInvariant()
        return [pscustomobject][ordered]@{
            Result = 'CREATED'
            CipherSha256 = $cipherSha256
            ManifestSha256 = $manifestSha256
            RequiredMigration = $script:J7ExpectedMigration
            PostgresMajorVersion = 17
            AgeVersion = $ageVersion
            TableCount = $script:J7TableNames.Count
        }
    }
    finally {
        foreach ($ownedStagingPath in @($partialArchive, $partialManifest)) {
            if (Test-Path -LiteralPath $ownedStagingPath) {
                Remove-Item -LiteralPath $ownedStagingPath -Force
            }
        }
        if ($publishedArchive -and -not $publishedManifest -and
            (Test-Path -LiteralPath $destinationInfo.ArchivePath)) {
            Remove-Item -LiteralPath $destinationInfo.ArchivePath -Force
        }
    }
}

function Invoke-J7Restore {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$PrimaryHost,
        [Parameter(Mandatory = $true)][int]$PrimaryPort,
        [Parameter(Mandatory = $true)][string]$PrimaryDatabase,
        [Parameter(Mandatory = $true)][string]$TargetHost,
        [Parameter(Mandatory = $true)][int]$TargetPort,
        [Parameter(Mandatory = $true)][string]$TargetDatabase,
        [Parameter(Mandatory = $true)][string]$TargetUsername,
        [Parameter(Mandatory = $true)][string]$IsolationAcknowledgement,
        [Parameter(Mandatory = $true)][string]$PgRestorePath,
        [Parameter(Mandatory = $true)][string]$PsqlPath,
        [Parameter(Mandatory = $true)][string]$AgePath,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$CleanupTimeoutMilliseconds
    )

    Assert-J7PowerShellRuntime
    Assert-J7IsolatedRestoreTarget `
        -PrimaryHost $PrimaryHost -PrimaryPort $PrimaryPort `
        -PrimaryDatabase $PrimaryDatabase `
        -TargetHost $TargetHost -TargetPort $TargetPort `
        -TargetDatabase $TargetDatabase `
        -IsolationAcknowledgement $IsolationAcknowledgement
    Assert-J7PostgresEndpoint `
        -DatabaseHost $TargetHost -Port $TargetPort `
        -Database $TargetDatabase -Username $TargetUsername
    Assert-J7PgpassBoundary -RepositoryRoot $RepositoryRoot

    $archiveInfo = Resolve-J7ExistingArchive `
        -ArchivePath $ArchivePath `
        -RepositoryRoot $RepositoryRoot
    $manifest = Read-J7Manifest -ManifestPath $archiveInfo.ManifestPath
    if ($manifest.encryptedArchiveFileName -cne
        [IO.Path]::GetFileName($archiveInfo.ArchivePath)) {
        throw 'J7_ARCHIVE_NAME_MISMATCH'
    }
    $cipherItem = Get-Item -LiteralPath $archiveInfo.ArchivePath
    if ([long]$cipherItem.Length -ne [long]$manifest.cipherBytes) {
        throw 'J7_ARCHIVE_SIZE_MISMATCH'
    }
    $actualCipherSha256 = (Get-FileHash `
            -LiteralPath $archiveInfo.ArchivePath `
            -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualCipherSha256 -cne $manifest.cipherSha256) {
        throw 'J7_ARCHIVE_HASH_MISMATCH'
    }

    $pgRestoreExecutable = Resolve-J7Executable `
        -Candidate $PgRestorePath -FailureCode 'J7_PG_RESTORE_NOT_FOUND'
    $psqlExecutable = Resolve-J7Executable `
        -Candidate $PsqlPath -FailureCode 'J7_PSQL_NOT_FOUND'
    $ageExecutable = Resolve-J7Executable `
        -Candidate $AgePath -FailureCode 'J7_AGE_NOT_FOUND'
    Assert-J7PostgresClient17 `
        -ExecutablePath $pgRestoreExecutable `
        -ToolName 'pg_restore' `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    Assert-J7PostgresClient17 `
        -ExecutablePath $psqlExecutable `
        -ToolName 'psql' `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    $runtimeAgeVersion = Get-J7AgeVersion `
        -ExecutablePath $ageExecutable `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

    Assert-J7Postgres17 `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $TargetHost -Port $TargetPort `
        -Database $TargetDatabase -Username $TargetUsername `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    Assert-J7FreshRestoreDatabase `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $TargetHost -Port $TargetPort `
        -Database $TargetDatabase -Username $TargetUsername `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

    $restoreArguments = @(
        "--host=$TargetHost",
        "--port=$TargetPort",
        "--username=$TargetUsername",
        "--dbname=$TargetDatabase",
        '--no-password',
        '--exit-on-error',
        '--single-transaction',
        '--no-owner',
        '--no-privileges'
    )
    $postgresEnvironment = New-J7PostgresEnvironment `
        -ApplicationName ('int001_j7_restore_' + [Guid]::NewGuid().ToString('N')) `
        -TimeoutSeconds $TimeoutSeconds
    Write-Host 'INT001_J7_RESTORE_PASSPHRASE=NATIVE_AGE_PROMPT_ONLY'
    Invoke-J7NativeBinaryPipeline `
        -ProducerFilePath $ageExecutable `
        -ProducerArgumentList @('--decrypt', $archiveInfo.ArchivePath) `
        -ConsumerFilePath $pgRestoreExecutable `
        -ConsumerArgumentList $restoreArguments `
        -ProducerEnvironment @{} `
        -ConsumerEnvironment $postgresEnvironment `
        -ProducerErrorToConsole $true `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds

    Assert-J7ExpectedSchema `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $TargetHost -Port $TargetPort `
        -Database $TargetDatabase -Username $TargetUsername `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    Assert-J7PayloadHashIntegrity `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $TargetHost -Port $TargetPort `
        -Database $TargetDatabase -Username $TargetUsername `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds
    $restoredEvidence = @(Get-J7TableEvidence `
        -PsqlPath $psqlExecutable `
        -DatabaseHost $TargetHost -Port $TargetPort `
        -Database $TargetDatabase -Username $TargetUsername `
        -TimeoutSeconds $TimeoutSeconds `
        -CleanupTimeoutMilliseconds $CleanupTimeoutMilliseconds)
    Assert-J7EvidenceEqual `
        -Expected $manifest.tables `
        -Actual $restoredEvidence `
        -FailureCode 'J7_RESTORE_EXACTNESS_MISMATCH'

    return [pscustomobject][ordered]@{
        Result = 'RESTORED_AND_VERIFIED'
        CipherSha256 = $actualCipherSha256
        RequiredMigration = $script:J7ExpectedMigration
        PostgresMajorVersion = 17
        AgeVersion = $runtimeAgeVersion
        TableCount = $script:J7TableNames.Count
        PrimaryMutationPerformed = $false
    }
}

Export-ModuleMember -Function @(
    'Get-J7ExpectedTableNames',
    'Get-J7IsolationAcknowledgement',
    'Assert-J7PowerShellRuntime',
    'Resolve-J7NewArchiveDestination',
    'Resolve-J7ExistingArchive',
    'Resolve-J7Executable',
    'Assert-J7PostgresEndpoint',
    'Assert-J7PgpassBoundary',
    'Assert-J7Postgres17',
    'Assert-J7FreshRestoreDatabase',
    'Assert-J7PayloadHashIntegrity',
    'Assert-J7IsolatedRestoreTarget',
    'Invoke-J7NativeCapture',
    'Invoke-J7NativeBinaryPipeline',
    'Assert-J7PostgresClient17',
    'Get-J7AgeVersion',
    'ConvertFrom-J7EvidenceScalar',
    'Assert-J7EvidenceEqual',
    'Read-J7Manifest',
    'Invoke-J7Backup',
    'Invoke-J7Restore'
)
