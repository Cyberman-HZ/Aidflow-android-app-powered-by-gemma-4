# Convenience wrapper around the portable toolchain under .tools/.
# Usage:
#   .\build.ps1                # assembleDebug
#   .\build.ps1 test           # testDebugUnitTest
#   .\build.ps1 <gradleTask>   # any gradle task

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Args = @('assembleDebug')
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

$env:JAVA_HOME = Join-Path $root '.tools\jdk'
$env:ANDROID_HOME = Join-Path $root '.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$($env:JAVA_HOME)\bin;$($env:ANDROID_HOME)\platform-tools;" + $env:Path

$gradle = Join-Path $root '.tools\gradle\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw "Portable Gradle not found at $gradle. Run the toolchain installer first." }

# Map shortcut to real task names
$normalised = $Args | ForEach-Object {
    switch ($_) {
        'test'  { 'testDebugUnitTest' }
        'apk'   { 'assembleDebug' }
        'clean' { 'clean' }
        default { $_ }
    }
}

Write-Host "Running gradle $($normalised -join ' ')" -ForegroundColor Cyan
& $gradle @normalised
exit $LASTEXITCODE
