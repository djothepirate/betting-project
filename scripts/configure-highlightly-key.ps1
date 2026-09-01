param(
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'

if ($Remove) {
    [Environment]::SetEnvironmentVariable('HIGHLIGHTLY_API_KEY', $null, 'User')
    Remove-Item Env:HIGHLIGHTLY_API_KEY -ErrorAction SilentlyContinue
    Write-Output 'Configuration locale Highlightly supprimée.'
    exit 0
}

$taskSecureKey = Read-Host 'Clé Highlightly' -AsSecureString
$taskPointer = [IntPtr]::Zero
$taskPlainKey = $null
try {
    $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecureKey)
    $taskPlainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
    if ([string]::IsNullOrWhiteSpace($taskPlainKey)) {
        throw 'La clé saisie est vide.'
    }
    [Environment]::SetEnvironmentVariable('HIGHLIGHTLY_API_KEY', $taskPlainKey, 'User')
    Write-Output 'Configuration locale Highlightly enregistrée sans affichage de la valeur.'
}
finally {
    if ($taskPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer)
    }
    $taskPlainKey = $null
    if ($null -ne $taskSecureKey) {
        $taskSecureKey.Dispose()
    }
}
