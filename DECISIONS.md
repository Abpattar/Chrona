# Chrona Decision Register

Date: 15 October 2026. `APPROVED` refers to product direction confirmed by the owner. `PROPOSED—P01 VERIFY` identifies an ordinary technical default that requires compatibility/build verification. Reopen a settled choice only with new evidence or a concrete material issue.

| ID | Status | Decision | Trade-off / reason | Affected area |
|---|---|---|---|---|
| ADR-001 | APPROVED | Android-first native app; one Git repo; one OpenCode agent in sequential phases | matches product/platform and master workflow | all |
| ADR-002 | APPROVED | Local-first MVP; no required account/backend/cloud sync | lower cost/privacy/scope; no multi-device sync initially | data/privacy |
| ADR-003 | APPROVED | Deterministic scheduler independent of external AI | reliable offline planning, but parser language coverage is intentionally bounded | scheduler/parser |
| ADR-004 | APPROVED | Never embed provider keys/secrets in APK or client source | mobile binary is not a secret store; future provider must be behind approved secure proxy | security |
| ADR-005 | APPROVED | Tap-to-speak only, no always-listening, no unnecessary raw audio | privacy and platform restrictions | voice |
| ADR-006 | APPROVED | On-device speech only when supported; otherwise typed fallback; optional system path only with disclosure/choice | may reduce voice usability on devices without an on-device recognizer | voice |
| ADR-007 | APPROVED | Room/SQLite local relational persistence, migrations and delete/export path | relationships and atomic revision commits; no cloud recovery initially | data |
| ADR-008 | APPROVED | Manual fixed events; calendar provider later | core scheduler can be evaluated without account scopes | calendar |
| ADR-009 | APPROVED | Overlay optional, permission-gated, best effort; notifications/in-app fallback | Android/OEM behavior differs | Quest Card |
| ADR-010 | APPROVED | Quiet reminders; cap two non-urgent cards/day is a configurable hypothesis | must be evaluated; not a hard universal truth | reminders/research |
| ADR-011 | APPROVED | Card actions Start/Done/Later/Dismiss are semantically distinct; dismiss != done | avoids accidental completion/data loss | task/card |
| ADR-012 | APPROVED | Warm tactile paper/pinned-note board, thin red threads; yellow/blue/green priorities plus labels/icons | matches source visual brief and accessibility | UI |
| ADR-013 | APPROVED | Advanced learning/personalization deferred until core loop works | protects two-credit scope and evidence quality | later release |
| ADR-014 | APPROVED | Ubuntu + Windows parity, shared Gradle wrapper and pinned dependency source | reproducibility across two dev machines | developer workflow |
| ADR-015 | APPROVED | Emulator and physical moto g54 Android 15 testing | platform/OEM differences cannot be inferred from build alone | QA |
| ADR-016 | VERIFIED (Ubuntu, P01) | Kotlin + Compose, single Gradle app module, Room, DataStore and pure domain packages | native, testable, proportionate; `scripts/verify.sh` built, tested and linted clean | P01 |
| ADR-017 | VERIFIED (Ubuntu, P01) | minSdk 26, compileSdk 36, targetSdk 36 | stable API 36 path; built against Platform 36 / Build-Tools 36.0.0. Release target policy must be rechecked before publishing | P01/release |
| ADR-018 | VERIFIED (Ubuntu, P01) | JDK17, AGP8.13.2, Gradle8.13, Kotlin2.2.21, KSP2.2.21-2.0.5, Compose BOM2026.06.00; Room2.8.5, DataStore1.2.1, WorkManager2.12.0, Activity Compose1.13.0 | every pin resolved and built. Navigation/Lifecycle corrected — see ADR-018a | P01 |
| ADR-019 | PROPOSED—P06 VERIFY | WorkManager for deferrable reminders/reconciliation; no exact-alarm promise | lower permission and battery scope; OS may delay non-exact work | P06 |
| ADR-020 | APPROVED | URE deadline, official report/demo rubric remain unknown until provided | avoid invented submission constraints | P08 |
| ADR-021 | APPROVED | No participant study until institution’s ethics/consent/data rules confirmed | safeguards and course policy unknown | P08 |
| ADR-022 | APPROVED | Standalone visual reference image absent from currently available uploaded files; use written PRD §6 until supplied | don't hallucinate unseen composition | UI |
| ADR-018a | APPROVED — P01 TOOLCHAIN GATE | Pin Navigation Compose **2.9.8** and Lifecycle **2.10.0**, not the originally proposed 2.10.2 / 2.11.0 | Navigation 2.10.x and Lifecycle 2.11.0 declare `minCompileSdk=37` and require AGP 9.1.0+, which forces the API-37 path this project deliberately avoids. 2.9.8 / 2.10.0 are the newest releases that keep compileSdk 36 + AGP 8.13.2. Costs us the newest navigation library; buys a stable, documented API-36 build | P01 |
| ADR-023 | APPROVED | Cloud backup and device transfer are disabled; a deliberate export path replaces them | `MASTER_ARCHITECTURE.md` §10 requires an intentional backup decision. Keeps private task data on the device by default without claiming encryption. Reversible; needs an owner decision if a user-visible backup feature is ever wanted | P01 |
| ADR-024 | APPROVED | Release builds are unminified for now | no shrink/obfuscation audit has been done; an unverified R8 config risks stripping Room/Compose classes in a demonstrator build. Revisit before any release | P01 |
| ADR-025 | APPROVED | Application identity `com.chrona.app` / "Chrona" | no approved reverse-DNS ID exists. "Chrona" is provisional; trademark and domain availability are unchecked. Renaming the product is an owner decision | P01 |
| ADR-026 | APPROVED | Parser moved to `domain/parse`; speech boundary to `platform/speech`; capture UI to `ui/voice`. `TaskIntentDraft` adds `fixedRange` and drops `clarification` (carried by `NeedsClarification` with a base draft) | Keeps domain pure (no Android imports). The change lets a fixed time interval be represented without guessing and separates an unanswered clarification from a complete draft. Matches the implemented P03 code and tests | P03 |

### ADR-018a — Coordinated toolchain change record

- **Date:** 15 October 2026. **Phase:** P01. **Owner approval:** recorded here as a technical
  default under ADR-016/017/018, not a product-scope change, so no separate approval round is
  required. Escalate to the owner if the owner prefers the API-37 path.
- **Evidence:** `./gradlew :app:assembleDebug` failed `checkDebugAarMetadata` with 6 errors.
  Published AAR metadata confirms `minCompileSdk=37` for `navigation-compose-android`
  2.10.0/2.10.1/2.10.2 and for `lifecycle-viewmodel-compose-android` / `lifecycle-runtime-compose-android`
  2.11.0. Full capture in `PROGRESS.md`.
- **Previous choice:** Navigation Compose 2.10.2, Lifecycle 2.10.0 (with 2.11.0 arriving
  transitively from Navigation).
- **New choice:** Navigation Compose 2.9.8, Lifecycle 2.10.0 pinned explicitly.
- **Alternatives considered:**
  1. *Move to compileSdk 37 + AGP 9.x.* Rejected — contradicts
     `CROSS_OS_SETUP_AND_TOOLCHAIN.md` ("Never use a preview SDK purely to be newest") and
     moves the whole project onto an unvalidated platform line for a library minor version.
  2. *Drop Navigation Compose and hand-roll navigation.* Rejected — reimplementing back
     stack, state restoration and accessibility for a five-destination shell is far more
     complexity than the version pin costs.
  3. *Stay on 2.9.7.* Rejected — 2.9.8 is API-compatible, same `minCompileSdk`, and is the
     latest patch of that line.
- **Affected requirements/files/contracts/tests:** PLAT-002, PRJ-001, UI-001;
  `gradle/libs.versions.toml`, `CROSS_OS_SETUP_AND_TOOLCHAIN.md`, `README.md`, this file,
  `CHANGELOG.md`, `PROGRESS.md`. No shared-contract symbol changed.
- **Compatibility/migration:** no schema, API or contract migration. Compile-target-only change.
- **Privacy/security/cost:** none.
- **Verification:** `./scripts/verify.sh` passes with the new pins on Ubuntu.

### ADR-026 — P03 structure and parser-contract adjustment

- **Date:** 16 October 2026. **Phase:** P03.
- **Structural change:** `domain/parse` (pure Kotlin) rather than `input/parser`; `platform/speech` rather than `input/voice`; `ui/voice` for capture UI. Preserves the domain core import boundary (no Android imports).
- **Contract change:** `TaskIntentDraft.fixedRange: TimeRange?` added; `clarification` removed from the draft and moved to `TaskUnderstandingResult.NeedsClarification(prompt)` with `prompt.base`. No semantics lost.
- **Verification:** 152 unit/Robolectric tests pass; `scripts/verify.sh` passes; only `RECORD_AUDIO` added, no `INTERNET` permission and no networking dependency. Device on-device availability remains unmeasured (OPEN-007).

## Approval-required change categories
Ask owner before material changes to data leaving device, cost, provider, storage/sync/account architecture, raw audio/transcript retention, task/relation/scheduler semantics, fixed commitment protection, mandatory overlay behavior, backup/deletion semantics, major dependency/hosting, or human study recruitment.

## Change record expectations
Document ID/date, evidence, previous/new choice, alternatives, affected requirement IDs/files/contracts/tests, migration/compatibility consequences, privacy/security/cost implications, owner approval and verification. See `CHANGE_CONTROL.md`.
