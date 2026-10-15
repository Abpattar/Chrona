# Cross-OS Setup and Toolchain Baseline

**Status: VERIFIED on Ubuntu in P01.** An integrated clean build, unit-test run and lint run
succeeded on Ubuntu 24.04.5 with the matrix below. The Windows half of the gate and the
emulator launch are still open — see [Open checks](#open-checks).

## Verified version matrix

Every pin is declared once, in `gradle/libs.versions.toml`. Do not duplicate versions
anywhere else.

| Component | Pinned value | Verified how |
|---|---|---|
| JDK | 17 (Temurin 17.0.20.1+1) | `./gradlew --version` reports Launcher JVM 17.0.20.1 |
| Android Gradle Plugin | 8.13.2 | builds with Gradle 8.13 |
| Gradle wrapper | 8.13 | `./gradlew --version`; distribution SHA-256 pinned |
| Kotlin Gradle plugin | 2.2.21 | compiles main + test + androidTest sources |
| Compose compiler plugin | 2.2.21 (`org.jetbrains.kotlin.plugin.compose`) | applied via the version catalog; no separate pinned compiler extension |
| KSP | 2.2.21-2.0.5 | applied; Room annotation processing runs |
| Compose BOM | 2026.06.00 | `assembleDebug` resolves ui 1.11.3 / material3 1.4.0 |
| Activity Compose | 1.13.0 | resolves |
| Lifecycle | 2.10.0 | resolves; newest release with `minCompileSdk=35` |
| Navigation Compose | 2.9.8 | resolves; newest release with `minCompileSdk=35` (see ADR-018a) |
| Room runtime/ktx/compiler | 2.8.5 | KSP task runs; schema export enabled |
| DataStore Preferences | 1.2.1 | resolves |
| WorkManager KTX | 2.12.0 | resolves |
| minSdk | 26 | builds |
| compileSdk | 36 | builds against Platform 36 / Build-Tools 36.0.0 |
| targetSdk | 36 | builds; recheck Google Play policy before submission |

### Why Navigation Compose is 2.9.8 and not 2.10.2

The originally proposed 2.10.2 does not build against this stack. Its published AAR metadata
requires `compileSdk 37` and AGP 9.1.0+, and it drags Lifecycle 2.11.0 which has the same
requirement. Chrona stays on the stable API-36 line, so it pins the newest compatible
releases instead. Full evidence and alternatives are recorded under ADR-018a in
`DECISIONS.md` and in `PROGRESS.md`.

### Version strategy
Android 16 SDK documentation uses API 36. Newer Compose material in the current official docs
is associated with API 37. Chrona prefers a stable, reproducible API-36-compatible stack for
the first demonstrator and reassesses before release. Never adopt a preview SDK purely to be
newest; adopting one is an owner-approved decision, not a dependency bump.

## Ubuntu laptop setup
1. Install a supported stable Android Studio and, via SDK Manager, Platform 36, matching
   build-tools, platform-tools, emulator and a system image.
2. Use JDK 17 (P01 verified). Check `java -version` and `./gradlew --version`.
3. Clone the same Git repository; never copy build outputs or local settings from another host.
4. Configure the Android SDK through Android Studio or environment variables. Keep
   `local.properties` untracked; never commit personal paths.
5. Ensure the `gradlew` executable bit is committed; call `./gradlew`, never a global Gradle.
6. Run `scripts/verify.sh` and record the full result.

```bash
java -version
./gradlew --version
./scripts/verify.sh                          # clean + assemble + unit tests + lint
./scripts/verify.sh --with-instrumentation   # adds connectedDebugAndroidTest
```

## Windows PC setup
1. Install the same supported stable Android Studio line and SDK Platform 36 / build-tools /
   platform-tools / emulator system image.
2. Select the same JDK 17. Make sure Android Studio's Gradle JDK and the command-line
   `JAVA_HOME` do not disagree.
3. Clone the same repository into a normal user-writable directory. Keep the SDK path in an
   untracked `local.properties`.
4. Use the committed `gradlew.bat`; do not rely on a globally installed Gradle.
5. Run `scripts/verify.ps1` and record the full result.

```powershell
java -version
.\gradlew.bat --version
.\scripts\verify.ps1
.\scripts\verify.ps1 -WithInstrumentation
```

## Shared reproducibility rules
- One Git repository and one source of pinned dependency versions
  (`gradle/libs.versions.toml`).
- Commit the Gradle wrapper scripts, JAR and properties. The wrapper JAR checksum matches
  the value published by `services.gradle.org`; `distributionSha256Sum` is pinned in
  `gradle-wrapper.properties`.
- Never commit `local.properties`, absolute SDK paths, signing keystores, exports or
  IDE user-local configuration.
- UTF-8, LF, `.editorconfig`, `.gitattributes`; no paths differing only by case.
- Pin versions; prohibit dynamic `+` versions and unbounded ranges.
- `scripts/verify.sh` and `scripts/verify.ps1` run equivalent tasks and propagate failing
  exit codes.
- A successful desktop build is not proof the app behaves correctly on Android. Emulator,
  microphone, permission, overlay and background checks are separate.
- Never silently skip a host or a real-device check; mark it BLOCKED with the reason.

## Open checks

| Check | Status | Blocker | Needed action |
|---|---|---|---|
| Windows clean-clone `clean assembleDebug testDebugUnitTest lintDebug` | **BLOCKED** | No Windows machine is available in this environment. `scripts/verify.ps1` is written and reviewed but has never been executed | Run it on the Windows PC from a fresh clone and paste the result into `PROGRESS.md` (satisfies AT-033) |
| API 36 emulator launch and instrumentation run | **BLOCKED** | `/dev/kvm` is `root:kvm` mode 0660 and the build user is not in the `kvm` group, so `cat /dev/kvm` fails. Hardware-accelerated emulation is unavailable and software emulation of an x86_64 API 36 image is not viable | Add the build user to the `kvm` group, or run `connectedDebugAndroidTest` on the moto g54 or a Studio-managed emulator |
| moto g54 / Android 15 physical device | NOT RUN | No device attached | Required from P03 onward (PLAT-001, AT-034) |

## Troubleshooting
- **JDK mismatch:** inspect `JAVA_HOME`, `PATH`, the Studio Gradle JDK and `./gradlew --version`.
- **Missing SDK:** install the exact platform/build-tools via SDK Manager. Never hard-code
  personal paths.
- **AAR metadata error mentioning compileSdk 37 / AGP 9:** a transitive dependency has moved to
  a newer platform line. Check the artifact's published AAR metadata before upgrading anything
  else; see ADR-018a for the worked example.
- **Ubuntu permission:** check the `gradlew` executable bit and filesystem permissions.
- **Windows file lock:** stop daemons and close tools holding build outputs. Never delete source
  to hide a problem.
- **Divergent dependency resolution:** compare wrapper, pin source and
  `./gradlew :app:dependencies`. Never patch only one host.
- **`lint.xml` parse failure:** `lint.xml` accepts only `lint`, `issue`, `ignore` and `option`
  as children. Anything else (for example `<format>`) makes lint crash rather than warn.
- **Emulator/device unavailable:** mark the device test BLOCKED. An emulator pass is never
  physical-device verification.
