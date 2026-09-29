#requires -Version 7.2
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $projectRoot 'tools/release.ps1'
$hostExe = (Get-Process -Id $PID).Path
$tokens = $null
$syntaxErrors = $null
[void][Management.Automation.Language.Parser]::ParseFile($scriptPath,[ref]$tokens,[ref]$syntaxErrors)
if ($syntaxErrors.Count) { throw 'Release helper has syntax errors.' }
$script:passed = 1
function Expect-Failure([string[]]$Arguments, [string]$Message) {
    $info = [Diagnostics.ProcessStartInfo]::new($hostExe)
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardInput = $true
    foreach ($arg in (@('-NoProfile','-File',$scriptPath) + $Arguments)) { $info.ArgumentList.Add($arg) }
    $process = [Diagnostics.Process]::Start($info)
    try {
        $process.StandardInput.Close()
        $out = $process.StandardOutput.ReadToEndAsync()
        $err = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(15000)) { $process.Kill($true); throw 'Unexpected prompt or timeout in guard test.' }
        $text = $out.GetAwaiter().GetResult() + $err.GetAwaiter().GetResult()
        if ($process.ExitCode -eq 0 -or -not $text.Contains($Message)) { throw "Expected guard failed: $Message" }
        $script:passed++
    } finally { $process.Dispose() }
}
$existing = Join-Path $projectRoot 'README.md'
$hash = (Get-FileHash -LiteralPath $existing).Hash
Expect-Failure @('-Action','CreateKey','-KeystorePath',$existing) 'Keystore already exists'
if ((Get-FileHash -LiteralPath $existing).Hash -ne $hash) { throw 'Existing file changed.' }
$script:passed++
Expect-Failure @('-Action','Build','-KeystorePath','relative-key.jks') 'absolute keystore path'
$missing = Join-Path $projectRoot ('.agent/archive/missing-test-key-' + [guid]::NewGuid().ToString('N') + '.jks')
Expect-Failure @('-Action','Build','-KeystorePath',$missing) 'Release key is missing'
Write-Output "PASS: $script:passed release helper safety checks. Keys created=0; passwords requested=0; uploads=0."
