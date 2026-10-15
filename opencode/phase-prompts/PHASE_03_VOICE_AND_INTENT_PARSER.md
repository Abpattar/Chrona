# P03 — Voice and Structured Task Intent

## Objective
Add explicit tap-to-speak on top of the existing typed flow with provider-neutral parsing, deterministic validation and correction. No external AI provider is to be integrated.

## Prerequisites
P02 gate passed. Read `SHARED_CONTRACT.md` parser contract and `TECHNICAL_REFERENCES.md`.

## Scope
- `TaskIntentParser` interface and deterministic parser supporting a deliberately bounded grammar for common commands/dates/durations/priorities and fixed/flexible clues.
- Structured task intent DTO with confidence/ambiguity issues, validated by canonical domain rules.
- Typed fallback remains first-class.
- Android tap-to-speak adapter checks `SpeechRecognizer.isOnDeviceRecognitionAvailable`, uses on-device recognition only when available, destroys/releases recognizer correctly and visibly represents listening/cancel/retry.
- A transcript review/correction screen; user confirms ambiguous/non-obvious interpretation before persistence.
- No remote speech fallback and no remote LLM code, provider secrets, analytics or raw audio persistence.

## Parser behaviors to cover
Examples: “Study for GATE for two hours tomorrow”; “finish assignment before Friday”; “What should I do next?”; “I finished the assignment”; “move my workout later”; “connect this project to my showcase”. Unsupported or ambiguous requests should return a reason and ask one focused clarification or open the editor; do not fabricate date/time facts.

## Tests
- Table-driven parser fixtures including malformed dates, ambiguous relative dates, negation, Unicode, empty speech result, unsupported language/locale, confidence ambiguity.
- Contract validation for every parser output; property/fuzz cases around empty/large input and invalid enums.
- Adapter tests for unavailable on-device recognition, permission denial, cancel, timeout/error, repeat use and recognizer cleanup.
- On physical moto g54 Android 15: record device locale, on-device availability, observed network behavior/permission flows. If service behavior cannot be verified, describe it as unknown and keep voice optional.

## Acceptance
Typed flow always works; voice uses only explicitly selected local/on-device path or offers typing. No network/paid AI dependency was added. Update data disclosure docs. Next: P04.
