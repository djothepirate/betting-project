$ErrorActionPreference = 'Stop'

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]
        $Expected,

        [Parameter(Mandatory = $true)]
        $Actual,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if ($Expected -ne $Actual) {
        throw "$Message Attendu : '$Expected'. Obtenu : '$Actual'."
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,

        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-ValidationProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PowerShellPath,

        [Parameter(Mandatory = $true)]
        [string]$ValidationScript,

        [Parameter(Mandatory = $true)]
        [string]$MavenPath,

        [Parameter(Mandatory = $true)]
        [string]$SecretScanPath
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Une commande native qui ecrit sur stderr ne doit pas interrompre le
        # processus de test avant que son code de sortie soit controle.
        $ErrorActionPreference = 'Continue'
        & $PowerShellPath -NoProfile -ExecutionPolicy Bypass -File $ValidationScript `
            -MavenCommand $MavenPath -SecretScanScript $SecretScanPath *> $null
        return $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

$verifyScript = Join-Path (Split-Path -Parent $PSScriptRoot) 'verify-windows.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("betting-project-verify-windows-{0}" -f [guid]::NewGuid().ToString('N'))
$windowsPowerShell = (Get-Command powershell.exe -CommandType Application -ErrorAction Stop).Source

New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    $fakeMaven = Join-Path $testRoot 'fake-maven.cmd'
    $fakeSecretScan = Join-Path $testRoot 'fake-secret-scan.ps1'
    $invocationLog = Join-Path $testRoot 'maven-invocations.log'

    $env:FAKE_MAVEN_LOG = $invocationLog
    'exit 0' | Set-Content -LiteralPath $fakeSecretScan -Encoding Ascii

    @(
        '@echo off'
        'echo %*>>"%FAKE_MAVEN_LOG%"'
        'if "%~1"=="-version" exit /b 23'
        'exit /b 0'
    ) | Set-Content -LiteralPath $fakeMaven -Encoding Ascii

    $wrapperFailureExitCode = Invoke-ValidationProcess -PowerShellPath $windowsPowerShell `
        -ValidationScript $verifyScript -MavenPath $fakeMaven -SecretScanPath $fakeSecretScan
    $wrapperFailureInvocations = @(Get-Content -LiteralPath $invocationLog)

    Assert-Equal -Expected 1 -Actual $wrapperFailureExitCode `
        -Message 'La validation doit echouer lorsque le controle du wrapper echoue.'
    Assert-Equal -Expected 1 -Actual $wrapperFailureInvocations.Count `
        -Message 'La validation doit stopper immediatement apres un echec du wrapper.'
    Assert-Equal -Expected '-version' -Actual $wrapperFailureInvocations[0] `
        -Message 'Seul le controle du wrapper doit avoir ete execute.'

    Remove-Item -LiteralPath $invocationLog -Force
    @(
        '@echo off'
        'echo %*>>"%FAKE_MAVEN_LOG%"'
        'if "%~1"=="verify" exit /b 37'
        'exit /b 0'
    ) | Set-Content -LiteralPath $fakeMaven -Encoding Ascii

    $standardFailureExitCode = Invoke-ValidationProcess -PowerShellPath $windowsPowerShell `
        -ValidationScript $verifyScript -MavenPath $fakeMaven -SecretScanPath $fakeSecretScan
    $standardFailureInvocations = @(Get-Content -LiteralPath $invocationLog)

    Assert-Equal -Expected 1 -Actual $standardFailureExitCode `
        -Message 'La validation doit echouer lorsque le build Maven standard echoue.'
    Assert-Equal -Expected 2 -Actual $standardFailureInvocations.Count `
        -Message 'La validation doit stopper immediatement apres un echec du build standard.'
    Assert-Equal -Expected '-version' -Actual $standardFailureInvocations[0] `
        -Message 'Le wrapper doit etre controle avant le build.'
    Assert-Equal -Expected 'verify' -Actual $standardFailureInvocations[1] `
        -Message 'Le build standard simule doit avoir ete execute.'

    Remove-Item -LiteralPath $invocationLog -Force
    @(
        '@echo off'
        'echo %*>>"%FAKE_MAVEN_LOG%"'
        'exit /b 0'
    ) | Set-Content -LiteralPath $fakeMaven -Encoding Ascii
    'exit 29' | Set-Content -LiteralPath $fakeSecretScan -Encoding Ascii

    $secretFailureExitCode = Invoke-ValidationProcess -PowerShellPath $windowsPowerShell `
        -ValidationScript $verifyScript -MavenPath $fakeMaven -SecretScanPath $fakeSecretScan
    $secretFailureInvocations = @(Get-Content -LiteralPath $invocationLog)

    Assert-Equal -Expected 1 -Actual $secretFailureExitCode `
        -Message 'La validation doit echouer lorsque le controle de secrets echoue.'
    Assert-Equal -Expected 2 -Actual $secretFailureInvocations.Count `
        -Message 'La validation doit stopper avant les tests integration apres un echec du scan.'
    Assert-Equal -Expected 'verify' -Actual $secretFailureInvocations[1] `
        -Message 'Le build standard doit preceder le controle de secrets.'
    Assert-True -Condition ($secretFailureInvocations -notcontains '-Pintegration verify') `
        -Message 'Les tests integration ne doivent pas demarrer apres un echec du scan.'

    Remove-Item -LiteralPath $invocationLog -Force
    'exit 0' | Set-Content -LiteralPath $fakeSecretScan -Encoding Ascii
    @(
        '@echo off'
        'echo %*>>"%FAKE_MAVEN_LOG%"'
        'if "%~1"=="-Pintegration" exit /b 41'
        'exit /b 0'
    ) | Set-Content -LiteralPath $fakeMaven -Encoding Ascii

    $integrationFailureExitCode = Invoke-ValidationProcess -PowerShellPath $windowsPowerShell `
        -ValidationScript $verifyScript -MavenPath $fakeMaven -SecretScanPath $fakeSecretScan
    $integrationFailureInvocations = @(Get-Content -LiteralPath $invocationLog)

    Assert-Equal -Expected 1 -Actual $integrationFailureExitCode `
        -Message 'La validation doit echouer lorsque Testcontainers/Maven echoue.'
    Assert-Equal -Expected 3 -Actual $integrationFailureInvocations.Count `
        -Message 'La validation complete doit toujours appeler le profil integration.'
    Assert-Equal -Expected '-Pintegration verify' -Actual $integrationFailureInvocations[2] `
        -Message 'Le profil integration doit etre execute sans precontrole de la CLI Docker.'
    Assert-True -Condition ($integrationFailureInvocations -notcontains 'docker') `
        -Message 'La validation ne doit pas appeler la CLI Docker pour decider des tests.'

    Write-Host 'Tests verify-windows : succes.'
}
finally {
    Remove-Item Env:FAKE_MAVEN_LOG -ErrorAction SilentlyContinue
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
    if ($resolvedTestRoot.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
