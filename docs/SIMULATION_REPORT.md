# Chrona simulation report

- **Run id:** `life-2026-01-05`
- **Generated:** 2026-10-19T04:53:49.772929755Z
- **Processing mode:** Deterministic (no AI)
- **Environment:** JVM (Gradle testDebugUnitTest)
- **Result:** PASSED (40 passed, 0 failed, 40 total)
- **Total simulation runtime:** 403 ms

## Scenarios

### Full 14-day life (`full-life`)

College, flute, routine, family and deadlines, with disruptions throughout.

- Days simulated: 14
- Tasks in the pool: 142
- Distinct task titles: 58
- Runtime: 135 ms

| Day | Date | Active | Scheduled | Completed | Missed | Unplaced | Overdue | At risk | Moved |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2026-01-05 | 136 | 136 | 5 | 0 | 0 | 0 | 0 | 0 |
| 2 | 2026-01-06 | 131 | 131 | 6 | 0 | 0 | 0 | 0 | 2 |
| 3 | 2026-01-07 | 126 | 124 | 5 | 0 | 2 | 0 | 0 | 7 |
| 4 | 2026-01-08 | 122 | 118 | 5 | 1 | 4 | 0 | 0 | 12 |
| 5 | 2026-01-09 | 117 | 109 | 6 | 0 | 8 | 2 | 0 | 15 |
| 6 | 2026-01-10 | 112 | 103 | 5 | 0 | 9 | 3 | 0 | 16 |
| 7 | 2026-01-11 | 110 | 96 | 5 | 0 | 14 | 6 | 0 | 33 |
| 8 | 2026-01-12 | 105 | 88 | 4 | 0 | 17 | 8 | 1 | 38 |
| 9 | 2026-01-13 | 101 | 81 | 3 | 0 | 20 | 10 | 0 | 13 |
| 10 | 2026-01-14 | 98 | 66 | 3 | 0 | 32 | 12 | 0 | 15 |
| 11 | 2026-01-15 | 95 | 55 | 2 | 0 | 40 | 14 | 0 | 32 |
| 12 | 2026-01-16 | 93 | 34 | 3 | 0 | 59 | 16 | 0 | 10 |
| 13 | 2026-01-17 | 90 | 17 | 2 | 0 | 73 | 17 | 3 | 5 |
| 14 | 2026-01-18 | 88 | 17 | 1 | 0 | 71 | 20 | 0 | 2 |

Reasons work could not be placed:
- `DEADLINE_INFEASIBLE`: 10
- `FIXED_CONFLICT`: 12
- `NO_AVAILABLE_WINDOW`: 5

### Normal college day (`normal-college-day`)

A single ordinary day, to check the plan is sensible before anything else runs.

- Days simulated: 1
- Tasks in the pool: 136
- Distinct task titles: 52
- Runtime: 3 ms

| Day | Date | Active | Scheduled | Completed | Missed | Unplaced | Overdue | At risk | Moved |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2026-01-05 | 136 | 5 | 6 | 0 | 131 | 0 | 21 | 0 |

Reasons work could not be placed:
- `OUTSIDE_HORIZON`: 1

### Overloaded day (`overloaded-day`)

More work than the day can hold; the surplus must be reported, not deleted.

- Days simulated: 1
- Tasks in the pool: 136
- Distinct task titles: 52
- Runtime: 18 ms

| Day | Date | Active | Scheduled | Completed | Missed | Unplaced | Overdue | At risk | Moved |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2026-01-05 | 136 | 0 | 0 | 0 | 136 | 0 | 21 | 0 |

Reasons work could not be placed:
- `NO_AVAILABLE_WINDOW`: 1
- `OUTSIDE_HORIZON`: 1

### Provider outage (`provider-outage`)

No AI provider is reachable; planning must be identical and fully local.

- Days simulated: 14
- Tasks in the pool: 142
- Distinct task titles: 58
- Runtime: 247 ms

| Day | Date | Active | Scheduled | Completed | Missed | Unplaced | Overdue | At risk | Moved |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2026-01-05 | 136 | 136 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2 | 2026-01-06 | 136 | 136 | 0 | 0 | 0 | 0 | 0 | 6 |
| 3 | 2026-01-07 | 137 | 134 | 0 | 0 | 3 | 0 | 0 | 12 |
| 4 | 2026-01-08 | 138 | 133 | 0 | 0 | 5 | 0 | 0 | 14 |
| 5 | 2026-01-09 | 138 | 129 | 0 | 0 | 9 | 2 | 0 | 12 |
| 6 | 2026-01-10 | 139 | 126 | 0 | 0 | 13 | 4 | 1 | 16 |
| 7 | 2026-01-11 | 142 | 125 | 0 | 0 | 17 | 7 | 0 | 20 |
| 8 | 2026-01-12 | 142 | 122 | 0 | 0 | 20 | 9 | 1 | 22 |
| 9 | 2026-01-13 | 142 | 107 | 0 | 0 | 35 | 11 | 0 | 17 |
| 10 | 2026-01-14 | 142 | 93 | 0 | 0 | 49 | 14 | 0 | 36 |
| 11 | 2026-01-15 | 142 | 78 | 0 | 0 | 64 | 17 | 0 | 37 |
| 12 | 2026-01-16 | 142 | 56 | 0 | 0 | 86 | 19 | 0 | 5 |
| 13 | 2026-01-17 | 142 | 45 | 0 | 0 | 97 | 20 | 1 | 14 |
| 14 | 2026-01-18 | 142 | 21 | 0 | 0 | 121 | 23 | 0 | 4 |

Reasons work could not be placed:
- `DEADLINE_INFEASIBLE`: 10
- `FIXED_CONFLICT`: 12
- `NO_AVAILABLE_WINDOW`: 6

## Checks

| Status | Scenario | Check | Detail |
|---|---|---|---|
| PASS | full-life | one report per simulated day | expected 14 day reports, got 14 |
| PASS | full-life | every active task is placed or explained | no day reported fewer accounted-for tasks than exist in the pool (tightest day 14) |
| PASS | full-life | no task is silently dropped over the whole run | the run ended with an empty task pool, so nothing was actually exercised |
| PASS | full-life | the final day accounts for every task still open | 17 scheduled + 71 unplaced vs 88 still open |
| PASS | full-life | no overlapping placements in the final plan | none |
| PASS | full-life | every placement sits inside a day the clock offered | a placement fell outside the simulated horizon |
| PASS | full-life | a completed task never returns to the plan | tasks finished before the last day were placed again: [] |
| PASS | full-life | scripted completions actually happened | the scenario scripted 71 completions but 55 were recorded |
| PASS | full-life | the same inputs reproduce the same plan | replaying the scenario produced a different set of placements |
| PASS | full-life | reset removes the run and keeps user tasks | after reset the store held 1 task(s): [user-task] |
| PASS | normal-college-day | one report per simulated day | expected 1 day reports, got 1 |
| PASS | normal-college-day | every active task is placed or explained | no day reported fewer accounted-for tasks than exist in the pool (tightest day 1) |
| PASS | normal-college-day | no task is silently dropped over the whole run | the run ended with an empty task pool, so nothing was actually exercised |
| PASS | normal-college-day | the final day accounts for every task still open | 5 scheduled + 131 unplaced vs 136 still open |
| PASS | normal-college-day | no overlapping placements in the final plan | none |
| PASS | normal-college-day | every placement sits inside a day the clock offered | a placement fell outside the simulated horizon |
| PASS | normal-college-day | a completed task never returns to the plan | tasks finished before the last day were placed again: [] |
| PASS | normal-college-day | scripted completions actually happened | the scenario scripted 6 completions but 6 were recorded |
| PASS | normal-college-day | the same inputs reproduce the same plan | replaying the scenario produced a different set of placements |
| PASS | normal-college-day | reset removes the run and keeps user tasks | after reset the store held 1 task(s): [user-task] |
| PASS | overloaded-day | one report per simulated day | expected 1 day reports, got 1 |
| PASS | overloaded-day | every active task is placed or explained | no day reported fewer accounted-for tasks than exist in the pool (tightest day 1) |
| PASS | overloaded-day | no task is silently dropped over the whole run | the run ended with an empty task pool, so nothing was actually exercised |
| PASS | overloaded-day | the final day accounts for every task still open | 0 scheduled + 136 unplaced vs 136 still open |
| PASS | overloaded-day | no overlapping placements in the final plan | none |
| PASS | overloaded-day | every placement sits inside a day the clock offered | a placement fell outside the simulated horizon |
| PASS | overloaded-day | a completed task never returns to the plan | tasks finished before the last day were placed again: [] |
| PASS | overloaded-day | scripted completions actually happened | the scenario scripted 0 completions but 0 were recorded |
| PASS | overloaded-day | the same inputs reproduce the same plan | replaying the scenario produced a different set of placements |
| PASS | overloaded-day | reset removes the run and keeps user tasks | after reset the store held 1 task(s): [user-task] |
| PASS | provider-outage | one report per simulated day | expected 14 day reports, got 14 |
| PASS | provider-outage | every active task is placed or explained | no day reported fewer accounted-for tasks than exist in the pool (tightest day 1) |
| PASS | provider-outage | no task is silently dropped over the whole run | the run ended with an empty task pool, so nothing was actually exercised |
| PASS | provider-outage | the final day accounts for every task still open | 21 scheduled + 121 unplaced vs 142 still open |
| PASS | provider-outage | no overlapping placements in the final plan | none |
| PASS | provider-outage | every placement sits inside a day the clock offered | a placement fell outside the simulated horizon |
| PASS | provider-outage | a completed task never returns to the plan | tasks finished before the last day were placed again: [] |
| PASS | provider-outage | scripted completions actually happened | the scenario scripted 0 completions but 0 were recorded |
| PASS | provider-outage | the same inputs reproduce the same plan | replaying the scenario produced a different set of placements |
| PASS | provider-outage | reset removes the run and keeps user tasks | after reset the store held 1 task(s): [user-task] |

