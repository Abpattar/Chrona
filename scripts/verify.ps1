# Chrona verification entry point (Windows PowerShell).
# Runs the same task list as scripts/verify.sh and propagates a non-zero exit code.
#
# Prereqs: JDK 17 (see CROSS_OS_SETUP_AND_TOOLCHAIN.md), Android SDK Platform 36,
# and an untracked local.properties pointing at it.
#
# Usage: .\scripts\verify.ps1 [-WithInstrumentation]

[CmdletBinding()]
param(
    [switch]$WithInstrumentation
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $RepoRoot
try {
    Write-Host '==> java -version'
    java -version

    Write-Host '==> .\gradlew.bat --version'
    .\gradlew.bat --version
    if ($LASTEXITCODE -ne 0) { throw "gradlew.bat --version failed with $LASTEXITCODE" }

    Write-Host '==> clean assembleDebug testDebugUnitTest lintDebug'
    .\gradlew.bat --no-daemon clean assembleDebug testDebugUnitTest lintDebug
    if ($LASTEXITCODE -ne 0) { throw "build/test/lint failed with $LASTEXITCODE" }

    if ($WithInstrumentation) {
        Write-Host '==> connectedDebugAndroidTest (requires a running emulator or connected device)'
        .\gradlew.bat --no-daemon connectedDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw "connectedDebugAndroidTest failed with $LASTEXITCODE" }
    }
    else {
        Write-Host '==> SKIPPED connectedDebugAndroidTest (pass -WithInstrumentation to run it)'
    }

    Write-Host '==> assembleDebugAndroidTest (compiles the instrumentation suite without a device)'
    .\gradlew.bat --no-daemon assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "assembleDebugAndroidTest failed with $LASTEXITCODE" }

    Write-Host 'OK: Chrona verification passed'
}
finally {
    Pop-Location
}