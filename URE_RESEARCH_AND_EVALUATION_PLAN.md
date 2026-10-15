# URE Research and Evaluation Plan

**Course context:** two-credit University Research Experience. Official deadline, report format, demo rubric, required research method and ethics rules are not known from the provided material; these remain open.

## Hypothesis
Combining (1) explainable constraint-aware schedule recovery, (2) explicit hard versus supportive task/event relationships, and (3) deliberately limited task cards may help users retain a realistic plan with lower perceived interruption burden than a static reminder list and manual repair. This is a hypothesis, not a proven result or novelty claim.

## Technical evaluation questions
1. Does the scheduler preserve fixed commitments and hard dependency order under realistic disruptions?
2. Does each eligible task get placed or receive a reasoned unplaced status?
3. Can users understand why tasks moved and correct parser mistakes?
4. Does reminder policy respect quiet hours, caps, batching and dismissal suppression?
5. Are the main surfaces accessible and responsive at demonstrator scale?
6. Which product hypotheses fail under testing and should be revised?

## Stage A: no-participant, synthetic testing (mandatory)
Use fixed clock/time zone and immutable fixtures. Compare Chrona with a simple baseline such as a static task/reminder list plus manual ordering on the same scenarios. Don't load real personal calendars or speech transcripts into fixtures. Record versions, inputs, outputs and limitations.

### Scenarios
- Late start: start planned for 08:00 but user begins at 10:00; a fixed class/deadline remains protected.
- Commitment overrun: class ends late; replan the remaining flexible work.
- Overloaded day: estimated tasks exceed capacity; show all unplaced items.
- Dependencies: task B requires A; a separate supportive paper does not block work.
- Parser correction: date/duration/priority was misread; user can correct before or after saving.
- New urgent task: recovery moves suitable work and explains trade-off.
- Reminder noise: quiet hours, two-card initial cap, related batching, repeated dismissal, permission denial.
- Platform capability: on-device voice unsupported, notification denied, overlay disabled, process killed/reopened.

## Metrics
### Schedule
- Hard-constraint violation count (fixed interval overlaps + hard dependency violations): target zero on all acceptance fixtures.
- Task-accounting rate = (placed active tasks + explicitly unplaced active tasks) / eligible active tasks: target 100%.
- Deadline reporting coverage: share of impossible deadlines explicitly flagged: target 100% in tests.
- Explanation coverage: changed/unplaced tasks with a reason: target 100% in tests.
- Revision atomicity: every injected failure/stale proposal preserves the last valid state: target 100% of fault tests.
- Number of moved tasks per recovery as descriptive metric; low movement is not automatically good if deadlines/priority are harmed.

### Input
- Field-level precision/recall on an authored synthetic phrase set for title/date/duration/priority/flexibility; report sample count and confusion cases.
- Correction completion/time and number of corrections in a developer walkthrough.
- Voice recognition availability/success on moto g54 with documented OS/provider/language/network conditions; distinguish speech-to-text errors from parser errors.

### Reminders
- Interruptions per simulated user-day, separated into urgent/non-urgent.
- Repeated-reminder rate per task over a simulated week.
- Useful-action rate (Start/Done/Later/meaningful details) by surface.
- Dismiss-without-action rate as a diagnostic metric, not a judgment of the user.
- Quiet-hours violations for nonurgent cards: target zero in policy tests.
- Permission opt-in/disable metrics only if participant research has proper authorization; opt-out is a legitimate preference.

### Usability/performance
- First launch to first saved task (~1 minute is a design hypothesis).
- Task correction/recovery completion time for scripted scenarios.
- Neutral ratings of perceived realism, explanation clarity and interruption burden if approved.
- Local action p50/p95, scheduler runtime, board responsiveness with 100 tasks/200 relations, all with disclosed device/build/fixture.

## Optional human-participant pilot — not authorized until institutional rules are confirmed
Before recruitment or any participant-data collection:
1. Ask supervisor/department whether ethics committee or equivalent review, consent, parental/minor safeguards or other approval applies.
2. Confirm this class URE is permitted to collect participant data. If unclear, do not recruit until written guidance.
3. Prepare appropriate information sheet, voluntary consent, withdrawal method, data minimization/retention, anonymized IDs, access/storage plan and contact information per institutional requirements.
4. Prefer synthetic tasks; avoid personal schedules, sensitive information or external AI transfer.
5. Predefine baseline, metrics, missing-data handling and limitations. Small uncontrolled samples support descriptive findings, not causal or significance claims.
6. Keep raw participant data outside source control and OpenCode context; use only approved, deidentified aggregates in the report.

If approval is not confirmed, the evaluation remains developer-only dogfooding plus synthetic scenarios. This is the default.

## Reporting discipline
Report sample size, aggregate participant profile if approved, duration, scenarios, consent/approval status, missing data, metrics and limitations. Do not claim the app “improves productivity” from a small uncontrolled demo. Voice, overlays, colored notes or rescheduling alone do not establish novelty; future publication/IP consideration needs evidence-backed review and qualified institutional/legal advice.
