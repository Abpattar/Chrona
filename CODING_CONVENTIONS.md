# Coding Conventions

- Kotlin / Kotlin DSL, UTF-8, LF and `.editorconfig`; avoid filenames differing only by case so Linux and Windows agree.
- Types use UpperCamelCase, functions/variables lowerCamelCase, constants UPPER_SNAKE_CASE. Names in `SHARED_SYMBOL_REGISTRY.md` are canonical.
- Prefer immutable data classes, sealed result/state types and explicit return values. Expected user failures are modeled rather than raised as generic exceptions.
- Inject `Clock`, ID generator, parser, repositories, and platform presenters. Pure scheduling must not use global `now()` or random tie-breaking.
- Compose screens collect state and dispatch events. Do not put DB access, scheduler constraints or intricate permission flows in Composable bodies.
- Keep each interface tied to a real domain boundary, platform variation or test seam. Avoid speculative abstractions and unnecessary DI frameworks.
- No API keys, personal task text, transcripts or private provider errors in logs. Ignore local config, signing keys, exports and machine-specific files.
- Use one dependency version source. Avoid dynamic `+` versions and unbounded ranges. Keep Gradle wrapper committed.
- Unit tests use fixed clock/timezone and synthetic data. Do not contact external AI or live speech/network services in tests.
- Every fixed defect gets a regression test. Shared public behavior changes require updating contracts, registry, consumers, tests and documentation together.
- Keep `PROGRESS.md` factual: commands actually run, actual pass/fail, skipped checks, device/host versions and blockers.
