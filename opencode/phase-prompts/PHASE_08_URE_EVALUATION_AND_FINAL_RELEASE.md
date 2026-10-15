# P08 — URE Evaluation, Demo and Final Acceptance

## Objective
Produce a reliable, demonstrable academic prototype with evidence, reproducible setup and an honest limitations record.

## Prerequisites
P07 gate passed. Confirm official URE deadline/report format/demo constraints with the university before claiming submission readiness.

## Scope
- Run `GLOBAL_ACCEPTANCE_TESTS.md` end-to-end and close all critical failures.
- Repeat clean-clone setup/build/tests on Ubuntu and Windows; record machine/tool details and exact outputs.
- Test on emulator/API36 and physical moto g54/Android15.
- Run the proposed internal evaluation plan in `URE_RESEARCH_AND_EVALUATION_PLAN.md`; define measures before testing.
- Before recruiting participants or collecting identifiable/user behavioral data, confirm university ethics/consent/data handling approvals. If approval is absent, limit evaluation to developer-run scenarios and synthetic/dummy data.
- Package debug/demo APK as institution allows; release/signing guidance must not include signing keys in repository. Add demo seed data only through a deliberate development path.
- Produce demo script, known limitations, build instructions, screenshots captured from the actual app, research-method/result report template, traceability audit and final changelog.

## Required evidence
- Actual build/test commands and outcomes; failed/skipped checks explicitly named.
- Requirement-to-test traceability for all Must requirements.
- Short demo journey: create task by typed entry or on-device speech where available; add fixed class; schedule; disrupt day; preview/apply recovery; inspect why changes occurred; board link; receive notification; test overlay fallback; export/delete.
- Basic evaluation data: task correctness, recovery constraint violations, tasks silently lost (target zero), unnecessary notification counts, correction rate, completion of scenario tasks, usability feedback collected only with appropriate approval.
- Distinguish measured results from design targets, hypotheses, and assumptions.

## Acceptance
All critical functional/privacy gates pass or are explicitly reported as release blockers. No novelty or patentability claim without evidence and qualified legal review. Finish README, architecture/decisions/change/progress files and final limitations report.
