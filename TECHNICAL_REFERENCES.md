# Chrona — Technical References and Version Verification

**Research checked:** 15 October 2026
**P01 verification:** 15 October 2026 — Ubuntu clean build, unit tests and lint passed with
the matrix below. Windows and emulator checks remain BLOCKED (`PROGRESS.md`).

## Decision summary

Use a native Android application built with Kotlin and Jetpack Compose. Keep the scheduler and core data rules in ordinary Kotlin so that they can be tested without Android services. Local persistence is Room over SQLite. Do not add a backend, cloud account, external AI, analytics SDK, crash-reporting service, calendar provider or dependency-injection framework to the MVP unless an explicit requirement later justifies it.

### Android SDK target

- Verified `compileSdk = 36` and `targetSdk = 36`; `minSdk = 26`.
- Google Play's current target-level page says that from 31 August 2026, new app submissions and updates must target Android 16 / API 36 or higher, subject to the stated policy scope and exceptions. Recheck immediately before a public release because the policy can change. Source: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en
- Android's Android 16 setup page documents `compileSdk = 36`, `targetSdk = 36`, and an Android Studio version compatible with Android 16 SDK. Source: https://developer.android.com/about/versions/16/setup-sdk
- Do not assume `targetSdk` controls the oldest installable OS; that is `minSdk`. The minimum API 26 is a product/default decision to validate, not a requirement from the uploaded brief.
- The user's moto g54 runs Android 15 / API 35, which is suitable to test behavior below target API 36 and to catch target-SDK behavior changes. Emulator coverage should include API 36.

## Verified toolchain matrix

P01 checked each pin against published metadata and ran clean builds. One correction was
required; see [P01 findings](#p01-findings).

| Component | Verified pin | Official source |
|---|---|---|
| JDK | 17 (Temurin 17.0.20.1+1) | https://developer.android.com/build/releases/about-agp |
| Android Gradle Plugin | 8.13.2 | https://developer.android.com/build/releases/about-agp |
| Gradle wrapper | 8.13 (SHA-256 pinned) | https://docs.gradle.org/current/userguide/gradle_wrapper.html |
| Kotlin | 2.2.21 | https://kotlinlang.org/docs/releases.html |
| KSP | 2.2.21-2.0.5 | https://github.com/google/ksp/releases |
| Compose BOM | 2026.06.00 (ui 1.11.3, material3 1.4.0) | https://developer.android.com/jetpack/androidx/releases/compose-bom |
| Activity Compose | 1.13.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Lifecycle | **2.10.0** | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Navigation Compose | **2.9.8** | https://developer.android.com/jetpack/androidx/releases/navigation |
| Room | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |
| DataStore | 1.2.1 | https://developer.android.com/jetpack/androidx/releases/datastore |
| WorkManager | 2.12.0 | https://developer.android.com/jetpack/androidx/releases/work |
| Test stack | JUnit 4.13.2, Robolectric 4.16, AndroidX test 1.7.0, Compose UI test from the BOM, Turbine 1.2.1 | https://developer.android.com/develop/ui/compose/testing |

The single authoritative pin location is `gradle/libs.versions.toml`. Do not copy version
literals elsewhere, and do not silently bump versions during implementation.

## P01 findings

The AndroidX compatibility signal that matters is the `minCompileSdk` / `minAgpVersion` pair
published in each AAR at
`META-INF/com/android/build/gradle/aar-metadata.properties`. Read it directly before assuming
a library supports the project's platform level:

```bash
curl -sSL -o nav.aar \
  https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose-android/<v>/navigation-compose-android-<v>.aar
unzip -p nav.aar META-INF/com/android/build/gradle/aar-metadata.properties | grep -E 'minCompileSdk|minAgpVersion'
```

Measured on 15 October 2026:

| Artifact | Version | `minCompileSdk` |
|---|---|---|
| `navigation-compose-android` | 2.9.8 | 35 |
| `navigation-compose-android` | 2.10.0 – 2.10.2 | 37 |
| `lifecycle-viewmodel-compose-android` | 2.10.0 | 35 |
| `lifecycle-viewmodel-compose-android` | 2.11.0 | 37 |
| `lifecycle-runtime-compose-android` | 2.11.0 | 37 |

Navigation Compose 2.10.x and Lifecycle 2.11.0 therefore require `compileSdk 37` and AGP
9.1.0+, which AGP also reports as exceeding its maximum recommended compile SDK of 36 for
8.13.2. Chrona keeps the stable API-36 line and pins the newest compatible releases instead.
Full decision record: `DECISIONS.md` ADR-018a.

## Speech recognition and privacy

- Android provides `SpeechRecognizer.createOnDeviceSpeechRecognizer` and `isOnDeviceRecognitionAvailable` from API 31. The on-device recognizer can throw `UnsupportedOperationException` if availability is false. Source: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Check on-device capability before choosing that path. In the MVP, never silently switch to a recognizer that may send audio to a remote service. When on-device recognition is unavailable, clearly offer typing and explain the limitation.
- Voice capture remains tap-to-speak. Explicitly release/destroy the recognizer and clear transcript/recording state after use. Do not retain raw audio as task history.
- Transcribed words remain user data. They are processed locally by the parser unless a future separately approved provider integration is designed.

## Notifications, background work and overlay

- Android 13+ runtime notification permission documentation: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Notification channels documentation: https://developer.android.com/develop/ui/compose/notifications/channels
- WorkManager is intended for deferrable asynchronous work that must be scheduled subject to constraints, not exact-time notification delivery: https://developer.android.com/jetpack/androidx/releases/work
- Background foreground-service starts are restricted for apps targeting API 31+, and services needing while-in-use permissions have additional constraints: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android 15 changes the `SYSTEM_ALERT_WINDOW` exemption: the app must currently have a visible overlay window to use this exemption to launch a foreground service from the background, unless another exemption applies. Source: https://developer.android.com/develop/background-work/services/fgs/changes
- Therefore: do not promise the overlay will appear at every time/context, and do not design a continuously running service simply to sustain it. Keep the overlay optional; use normal notifications and the in-app compact panel as fallbacks. Verify behavior on the moto g54 / Android 15, with permission granted/denied, after reboot, and under battery/background restrictions.

## Accessibility and automated testing

- Compose UI testing documentation: https://developer.android.com/develop/ui/compose/testing
- Compose accessibility documentation: https://developer.android.com/guide/topics/ui/accessibility/composables
- Automated tests are necessary but not a replacement for TalkBack, large font, reduced-motion and physical-device checks.
- Use fake clocks, deterministic fixture data, and test doubles for pure scheduling/parser/reminder policy. Tests must not send network requests or modify a real user's production data.

## Release fact-check gate

Before first public release, recheck and record date/URL for:
1. Play target API policy.
2. Android SDK / AGP / Gradle / JDK compatibility matrix.
3. stable release status for every dependency (no accidental alpha/preview artifact).
4. overlay and foreground execution rules for target API and tested devices.
5. speech recognition's on-device availability and privacy behavior on the actual phone.
6. distribution, privacy declaration, consent and institutional URE requirements.

A source URL in this file establishes where to verify a fact; it does not mean the project itself has been tested.
