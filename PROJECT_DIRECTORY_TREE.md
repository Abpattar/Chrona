# Repository Directory Tree

This is the shape of the repository. P01 created the marked parts; later phases fill in the
rest. Structural changes must be reflected consistently in the tree, file map, contracts,
registry and prompts.

Legend: **[P01]** created and building · *(planned)* created by the phase that owns it

```text
chrona/
├── README.md
├── README_FIRST.md
├── MASTER_REQUIREMENTS.md
├── MASTER_ARCHITECTURE.md
├── PROJECT_DIRECTORY_TREE.md
├── FILE_RESPONSIBILITY_AND_PHASE_MAP.md
├── SHARED_CONTRACT.md
├── SHARED_SYMBOL_REGISTRY.md
├── MODULE_DEPENDENCY_MAP.md
├── DATA_MODEL_AND_INTERFACES.md
├── REQUIREMENT_TRACEABILITY.md
├── DECISIONS.md
├── CODING_CONVENTIONS.md
├── ERROR_HANDLING_AND_SECURITY.md
├── IMPLEMENTATION_PHASE_PLAN.md
├── CHANGE_CONTROL.md
├── GLOBAL_ACCEPTANCE_TESTS.md
├── TESTING_AND_VERIFICATION_GUIDE.md
├── CROSS_OS_SETUP_AND_TOOLCHAIN.md
├── APP_VISUAL_SPEC.md
├── URE_RESEARCH_AND_EVALUATION_PLAN.md
├── RISKS_AND_OPEN_ITEMS.md
├── TECHNICAL_REFERENCES.md
├── PROGRESS.md
├── CHANGELOG.md
├── pack-metadata.json
├── ARCHIVE_INVENTORY.md
├── FILE_PACK_MANIFEST.md
├── .editorconfig                                    [P01]
├── .gitattributes                                   [P01]
├── .gitignore                                       [P01]
├── settings.gradle.kts                              [P01]
├── build.gradle.kts                                 [P01]
├── gradle.properties                                [P01]
├── gradle/
│   ├── libs.versions.toml                           [P01] single version source
│   └── wrapper/
│       ├── gradle-wrapper.jar                       [P01]
│       └── gradle-wrapper.properties                [P01] pinned SHA-256
├── gradlew                                          [P01]
├── gradlew.bat                                      [P01]
├── app/
│   ├── build.gradle.kts                             [P01]
│   ├── proguard-rules.pro                           [P01]
│   ├── lint.xml                                     [P01] accessibility checks = error
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                  [P01] no runtime permissions yet
│       │   ├── res/                                 [P01]
│       │   └── kotlin/com/chrona/app/
│       │       ├── ChronaApplication.kt             [P01]
│       │       ├── MainActivity.kt                  [P01]
│       │       ├── navigation/                      [P01]
│       │       ├── di/                              (planned P02) manual composition root
│       │       ├── domain/
│       │       │   ├── model/                       (planned P02)
│       │       │   ├── task/                        (planned P02)
│       │       │   ├── relation/                    (planned P02)
│       │       │   ├── scheduling/                  (P04) deterministic engine + contract
│       │       │   ├── recovery/                    (P04) planner, use cases, revision contract
│       │       │   ├── reminder/                    (planned P06)
│       │       │   └── parse/                       (P03) deterministic parser + contract
│       │       ├── data/
│       │       │   ├── local/                       [P02] Room v1; [P04] v2 + migration + schedule tables
│       │       │   ├── migration/                    (planned) explicit migrations live in ChronaDatabase
│       │       │   ├── repository/                  [P02; P04 adds RoomScheduleRevisionRepository]
│       │       │   └── export/                      (planned P07)
│       │       ├── usecase/                         (planned P02)
│       │       ├── input/
│       │       │   ├── parser/                      (planned P03)
│       │       │   └── voice/                       (planned P03)
│       │       ├── platform/
│       │       │   ├── notification/                (planned P06)
│       │       │   ├── overlay/                     (planned P06)
│       │       │   ├── speech/                      (P03) on-device speech adapter
│       │       │   ├── permission/                  (planned P06/P07)
│       │       │   └── work/                        (planned P04/P06)
│       │       ├── privacy/                         (planned P07)
│       │       └── ui/
│       │           ├── design/                      [P01] theme, paper tokens, components
│       │           ├── today/                       [P01 shell; list in P02]
│       │           ├── schedule/                    [P01 shell; preview/apply/undo in P04]
│       │           ├── board/                       [P01 shell; canvas in P05]
│       │           ├── history/                     [P01 shell; content in P04/P07]
│       │           ├── taskeditor/                  [P02] typed task editor
│       │           ├── questcard/                   (planned P06)
│       │           ├── recovery/                     (reserved; recovery UI lives in ui/schedule for P04)
│       │           ├── settings/                    [P01 shell; preferences P06/P07]
│       │           ├── voice/                       [P03] tap-to-speak capture sheet + VM
│       │           └── privacy/                      (planned P07)
│       ├── test/kotlin/com/chrona/app/
│       │   ├── domain/                               (planned P02)
│       │       ├── parse/                               [P03] parser/tests
│       │       ├── scheduling/                          [P04] engine/scenario tests
│       │       ├── recovery/                            [P04] use-case tests
│       │   ├── data/                                    [P04] Room repo + migration tests
│       │   ├── navigation/                          [P01]
│       │       ├── platform/                             [P03] speech adapter tests
│       │       ├── ui/                                   [P03] voice VM tests; [P04] schedule VM tests
│       │       ├── usecase/                               [P03] intent-create tests
│       │       └── reminder/                            (planned P06)
│       └── androidTest/kotlin/com/chrona/app/
│           ├── ui/                                  [P01] navigation smoke
│           ├── data/                                (planned P02) Room integration
│           └── platform/                            (planned P06)
├── scripts/
│   ├── verify.sh                                    [P01]
│   └── verify.ps1                                   [P01]
├── opencode/
│   ├── OPENCODE_MASTER_EXECUTION_PROMPT.md
│   └── phase-prompts/
├── implementation-guides/
│   ├── EXECUTION_CHECKLIST.md
│   └── FINAL_VERIFICATION_TEMPLATE.md
└── source-references/
    ├── Chrona_Project_Requirements_Document.pdf
    └── Universal_Software_Development_Master_Prompt_V2.pdf
```

## Structural rules
- One Gradle app module is deliberate for the two-credit project; pure Kotlin package
  boundaries are enough initially. Split modules only with evidence and owner agreement if
  material.
- The version catalog is the single authoritative dependency source. Never keep a second
  pin location.
- Repository root holds canonical planning docs so the phase prompts can reference stable
  relative paths. PDFs stay in `source-references/`.
- Never commit `local.properties`, absolute SDK paths, user-specific IDE run configuration,
  signing keys or exported personal task files.
