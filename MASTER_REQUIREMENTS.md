# Chrona — Master Requirements

**Inputs:** `source-references/Chrona_Project_Requirements_Document.pdf`, owner decisions confirmed 15 October 2026, and process rules in `source-references/Universal_Software_Development_Master_Prompt_V2.pdf`.

## 1. Product definition and problem
Chrona is a voice-first Android planner that converts everyday task requests into structured tasks, creates a feasible day plan, repairs it when the day changes, visualizes task/event relationships as a paper-and-red-thread board, and presents brief respectful task guidance rather than repeated reminder noise. The central problem is the gap between a timetable and a changing day: late starts, classes running over, underestimated work, unexpected events, and overloaded plans. The hypothesis that Chrona improves recovery or reduces interruption must be evaluated, not assumed.

## 2. Confirmed MVP — MUST
- Native Android application; one repository usable on Ubuntu and Windows.
- Low-friction onboarding, task capture by typed input or explicit tap-to-speak, and correction of misunderstandings. No mandatory account.
- No always-listening microphone. No unnecessary raw-audio persistence.
- Use on-device speech recognition when supported and reported available. Never silently fall back to an off-device provider; typing must remain a complete fallback. A potentially remote system recognizer may only be offered with plain disclosure and an explicit user choice.
- Structured parser output with schema validation, conservative defaults, confidence/ambiguity handling and short clarification only when consequential.
- Deterministic scheduling/feasibility engine independent of parser/LLM, Android UI, database I/O and network.
- Fixed commitments that automatic recovery never moves.
- Tasks support title/details, priority, estimated duration, deadline, earliest start/preferred window, fixed/flexible behavior, splitting/minimum session, status, origin, project/category and relations.
- Schedule creation and repair after late start, overrun, task delay/addition, cancelled task or break. Show changed tasks, reason per change, conflicting constraints and unplaced work. Never silently drop tasks.
- Preview/accept/edit/reject schedule changes; revision history and guarded undo.
- Today, Schedule, Board, History and Settings/Privacy Centre. Same task identity across all views.
- Paper-like note cards, pins/subtle shadows, thin red threads; yellow = low/side task, blue = important, green = critical/very important. Priority/status must never depend on color alone.
- Typed relationships: `DEPENDS_ON` is a hard directional prerequisite; `SUPPORTS`, `PART_OF_PROJECT`, `SAME_EVENT`, `RELATED_REFERENCE` are not blockers on their own. Consequential suggested dependencies need user approval.
- Optional permission-gated Quest Card overlay; notifications and in-app display remain functional without it. Compact panel actions include Start, Done, Later, Dismiss. Dismiss only closes the surface and is never completion/deletion.
- Quiet reminders; batching related tasks; quiet hours; configurable cap; suppress repeated nagging. Two non-urgent cards/day is an initial hypothesis.
- Local persistent database (Room/SQLite proposed), explicit schema migrations, export and deletion path, least permissions and privacy transparency.
- Clear error/empty/offline/permission-denied states; accessibility: semantic labels, scalable text, reduced motion, list alternative to visual board.
- Continuous tests, an early end-to-end working slice, cross-host verification, honest phase gates and progress.
- URE evaluation plan with measurable success metrics, a baseline and limitations. Confirm institutional ethics/consent/data rules before human-participant research.

## 3. Priority and MVP defaults — SHOULD
- Quiet reminder mode at first launch.
- Configurable initial nonurgent cap of 2 per local day; treat it as a test hypothesis, not a universal truth or an unchangeable hard-coded number.
- Manual fixed event entry; external calendar import/sync deferred.
- Local-only storage and no backend. Interfaces may make later sync possible, but no unused server or cloud abstraction is implemented now.
- A simple deterministic local parser for common date/day, duration, priority, flexibility and explicit relation cues. Keep it bounded and testable; show assumptions and let the user correct fields.
- Defer learning/personalization until the core loop is stable and objectively evaluated.
- Notifications are primary dependable reminder surface; overlay is optional best-effort. No exact-second or universal OEM behavior promises.
- Proposed `minSdk 26`, `compileSdk 36`, `targetSdk 36`, pending P01 verification.

## 4. Explicit non-goals / DEFERRED
- Paid external AI for core scheduling; API keys/secrets embedded in APK/source; direct provider credentials in client app.
- Mandatory accounts, backend, cloud sync or multi-device behavior.
- Required Google/Microsoft/other calendars; reading email, notes, files or drives by default.
- Always-on microphone, hidden recording or passive behavioral surveillance.
- Email client, note-taking suite, social network, full project-management platform, microservices or multi-agent implementation workflow.
- Advanced ML personalization before core logic is measured.
- Shame/guilt/streak loss/punitive scoring.
- Guaranteed schedule completion, exact timing, universal overlay, or feature novelty claims.
- Guaranteed URE publication or patentability.

## 5. Critical invariants
1. Task IDs do not change when tasks move, appear on another view, or are connected on the board.
2. Fixed commitments are hard constraints and cannot be moved by automatic recovery.
3. Source task `DEPENDS_ON` target task means source is dependent and target is its prerequisite. Other relationship types do not impose order by themselves.
4. Schedule output has placements plus explicit conflicts/unplaced reasons. Every eligible active task is accounted for.
5. Recovery is explained and previewed; applying assignments and revision metadata is atomic. Stale proposals cannot overwrite new edits.
6. Undo is guarded against overwriting later constraints; when unsafe, ask for a fresh recovery instead.
7. Parser/recognizer does not decide final schedule feasibility and does not activate an unconfirmed hard dependency.
8. Completion, postponement, skip, archive, Later/snooze and card dismissal are distinct actions.
9. Core task CRUD, planning, recovery and history work offline.
10. No remote speech/AI transfer occurs without clear disclosure and user choice. Chrona doesn't persist raw audio unnecessarily.
11. No API/provider secret may appear in source, resources, prompts, build logs or distributable APK.

## 6. Quality attributes
- Reliability: safe persistence, no duplicate action on retry, DB migrations without destructive fallback.
- Security/privacy: app-private storage, minimal permissions, export/delete, safe logs, known backup behavior, no misleading independent-encryption claim.
- Explainability: reason for movements, visible fixed events, overload and unplaced work.
- Accessibility: color not sole signal, scalable text, semantics, reduced motion, list alternative to board.
- Performance: measure everyday interactions and a board fixture of 100 tasks/200 relationships.
- Portability: same repo/toolchain builds on Ubuntu and Windows.
- Battery/platform: no always-on polling or constant redraw; test actual device behavior.

## 7. Platforms
Development hosts: Ubuntu Linux and Windows PC. Routine verification uses emulator/automated tests. Physical target reported by owner: Motorola moto g54, Android 15/API 35. Proposed build SDK target is API 36; exact version matrix is a P01 gate, and store policy must be rechecked before publishing.

## 8. Open institutional items
`OPEN — CONFIRM`: URE deadline; official report template/format; demo duration/rubric; required study method; whether participant recruitment is allowed; ethics/IRB/department approval and consent/data rules; IP/publication policies; institution-mandated stack/minimum SDK. Until these are confirmed, build relative milestones and synthetic scenario tests; do not collect participant data or invent official dates.

## 9. Classification
- **Confirmed:** explicit owner decisions and requirements above.
- **Proposed default:** ordinary architecture/toolchain choices in `DECISIONS.md` and `CROSS_OS_SETUP_AND_TOOLCHAIN.md`, subject to P01.
- **Open:** institutional requirements and hardware/toolchain facts requiring confirmation/test; see `RISKS_AND_OPEN_ITEMS.md`.
- **Optional future:** external AI via secure proxy, cloud sync, calendar providers, personalization, expanded gamification.
