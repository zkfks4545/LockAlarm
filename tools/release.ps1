#requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('CreateKey','Build')][string]$Action,
    [string]$KeystorePath = (Join-Path $env:LOCALAPPDATA 'LockAlarm/signing/lockalarm-release.jks'),
    [string]$KeyAlias = 'lockalarm-release',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android/Sdk')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) { throw 'JDK keytool not found. Supply -JavaHome.' }
if (-not [IO.Path]::IsPathFullyQualified($KeystorePath)) { throw 'Use an absolute keystore path.' }
$KeystorePath = [IO.Path]::GetFullPath($KeystorePath)
if ([string]::IsNullOrWhiteSpace($KeyAlias)) { throw 'KeyAlias is required.' }

if ($Action -eq 'CreateKey') {
    if (Test-Path -LiteralPath $KeystorePath) { throw 'Keystore already exists. It will not be replaced.' }
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $KeystorePath))
    Write-Host 'Enter the new password directly in keytool. Do not paste it into chat.'
    Write-Host 'When asked for the key password, Enter reuses the keystore password.'
    & $keytool -genkeypair -keystore $KeystorePath -storetype JKS -alias $KeyAlias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LockAlarm'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) { throw 'Key creation did not complete. Any partial file was preserved; inspect it before retrying.' }
    Write-Host "Key created: $KeystorePath"
    Write-Host 'Back up the keystore and alias offline, and keep the password in your password manager. No build or upload was performed.'
    return
}

if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) { throw 'Release key is missing. Run -Action CreateKey first.' }
if ([IO.Path]::GetFileName($KeystorePath) -ieq 'debug.keystore') { throw 'The development debug key is not a release key.' }
$buildTools = Join-Path $SdkRoot 'build-tools/36.0.0'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$aapt = Join-Path $buildTools 'aapt.exe'
foreach ($tool in @($apksigner,$aapt)) { if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw 'Android build-tools 36.0.0 not found. Supply -SdkRoot.' } }

$names = @('JAVA_HOME','ANDROID_HOME','LOCKALARM_STORE_FILE','LOCKALARM_STORE_PASSWORD','LOCKALARM_KEY_ALIAS','LOCKALARM_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$storeSecret = $null
$keySecret = $null
function Read-SecretText([Security.SecureString]$Secret) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
Push-Location $projectRoot
try {
    $storeSecret = Read-Host 'Keystore password (hidden)' -AsSecureString
    if ($storeSecret.Length -eq 0) { throw 'Empty password rejected.' }
    $keySecret = Read-Host 'Key password (hidden; Enter = same as keystore)' -AsSecureString
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $SdkRoot
    $env:LOCKALARM_STORE_FILE = $KeystorePath
    $env:LOCKALARM_KEY_ALIAS = $KeyAlias
    $env:LOCKALARM_STORE_PASSWORD = Read-SecretText $storeSecret
    $env:LOCKALARM_KEY_PASSWORD = if ($keySecret.Length) { Read-SecretText $keySecret } else { $env:LOCKALARM_STORE_PASSWORD }
    & .\gradlew.bat --no-daemon --no-configuration-cache --no-build-cache :app:verifyReleaseSigning :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release verification/build failed. No artifact was published.' }
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Expected signed release APK was not generated.' }
    $certificate = & $apksigner verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    if (($certificate -join "`n") -match 'CN=Android Debug') { throw 'Debug certificate detected; do not distribute this APK.' }
    $badging = & $aapt dump badging $apk
    if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -match 'application-debuggable') { throw 'APK metadata verification failed or debug flag is set.' }
    Write-Output $certificate
    Write-Output ($badging | Select-String '^package:')
    Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List
    Write-Host "Verified local APK: $apk"
    Write-Host 'Device testing and explicit GitHub publishing approval are still required. Nothing was uploaded.'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    if ($storeSecret) { $storeSecret.Dispose() }
    if ($keySecret) { $keySecret.Dispose() }
    Pop-Location
}
