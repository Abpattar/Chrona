#!/usr/bin/env bash
#
# Chrona verification entry point (Ubuntu/Linux + WSL/macOS).
# Runs the same task list as scripts/verify.ps1 and propagates a non-zero exit code.
#
# Prereqs: JDK 17 (see CROSS_OS_SETUP_AND_TOOLCHAIN.md), Android SDK Platform 36,
# and an untracked local.properties pointing at it.
#
# Usage: ./scripts/verify.sh [--with-instrumentation]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

WITH_INSTRUMENTATION=false
if [[ "${1:-}" == "--with-instrumentation" ]]; then
  WITH_INSTRUMENTATION=true
fi

echo "==> java -version"
java -version

echo "==> ./gradlew --version"
./gradlew --version

echo "==> clean assembleDebug testDebugUnitTest lintDebug"
./gradlew --no-daemon clean assembleDebug testDebugUnitTest lintDebug

if [[ "$WITH_INSTRUMENTATION" == "true" ]]; then
  echo "==> connectedDebugAndroidTest (requires a running emulator or connected device)"
  ./gradlew --no-daemon connectedDebugAndroidTest
else
  echo "==> SKIPPED connectedDebugAndroidTest (pass --with-instrumentation to run it)"
fi

echo "==> assembleDebugAndroidTest (compiles the instrumentation suite without a device)"
./gradlew --no-daemon assembleDebugAndroidTest

echo "OK: Chrona verification passed"