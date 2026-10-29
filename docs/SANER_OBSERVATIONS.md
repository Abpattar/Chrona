# Chrona — Product Vision & Saner.AI Reference

**Document purpose:** Define what Chrona should feel like and do as an intelligent personal scheduling assistant. This describes the product vision, not a technical implementation plan.

**Product principle:** Chrona should help a person decide what matters, turn intentions into a realistic plan, and keep that plan useful as life changes. It should feel like a capable personal assistant—not a calendar that merely stores events or a to-do list that leaves all planning to the user.

## 1. The vision

Chrona is an AI-first personal life planner. A user should be able to speak or type naturally, unload everything on their mind, and trust Chrona to understand it, organize it, and help turn it into action.

The experience should be close in spirit to Saner.AI: a personal assistant that understands tasks and context, helps plan the day, brings important things back to the user's attention, and reduces the mental effort of keeping life organized.

Chrona should aim to become the place a person turns to for questions such as:

- "Plan my day around college and my other commitments."
- "I have an assignment due Friday, a flute class tomorrow, and three other things I need to finish. Work out when I should do them."
- "I couldn't finish that task today. Rework the next few days without messing up my fixed commitments."
- "What should I focus on next, and why?"
- "I have too much to do. Show me what is realistic and what needs to move."

The goal is not to fill every empty minute. It is to create a practical, adaptable plan that helps the user make progress without unnecessary stress.

## 2. The core experience

### A. Natural-language capture

Users can explain tasks and plans in ordinary language, by voice or text. They should not have to complete a long form for every task.

Chrona should understand, where stated or reasonably inferable:

- What needs to be done.
- Date, requested start time, deadline, and time window.
- Estimated duration and whether the task can be split.
- Importance, urgency, consequences, and priority.
- Whether it is a fixed commitment or can move.
- Dependencies, preparation, travel, location, and relevant context.
- Recurrence and useful reminders.
- Uncertainty or missing information that could materially change the plan.

Chrona should extract multiple tasks from one message, connect related details, and let the user correct misunderstandings easily. It must not invent a deadline, commitment, or personal fact and present it as certain.

### B. Understand the user's real life

A useful plan must consider the user's whole day, not just an isolated task list. Relevant context may include existing calendar events, recurring routines, classes, work, meals, sleep, travel, preparation, rest, personal goals, and previously recorded task information.

Over time, Chrona should learn from appropriate feedback and observed behavior—for example, how long tasks tend to take, preferred working hours, and when the user often postpones certain types of work. It should use these patterns to improve suggestions, not to judge the user.

### C. Prioritize intelligently

Priority should not be a static label alone. Chrona should consider urgency, importance, deadline proximity, estimated effort, available time, dependencies, consequences of delay, and the user's stated goals.

It should explain important recommendations in plain language, especially when a task is prioritized or moved. It should distinguish "important," "urgent," and "quick to finish" rather than treating them as interchangeable.

### D. Turn tasks into a real schedule

Chrona should transform tasks into a calendar-based plan with realistic time blocks. It should schedule flexible work around fixed commitments and respect explicit user constraints.

The plan should account for realistic task duration, transitions, travel, preparation, breaks, meals, and rest where relevant. It should avoid packing the day unrealistically or scheduling every task back-to-back simply because a gap exists.

When the user specifies a time, Chrona should preserve it whenever feasible. If it cannot, it should explain the conflict and propose practical alternatives instead of silently ignoring the request.

### E. Adapt when life changes

Plans are not static. When a meeting appears, a task runs long, the user misses a session, or a deadline changes, Chrona should reassess the affected schedule.

It should:
- Identify the impact on other tasks and deadlines.
- Preserve completed work and fixed commitments.
- Make the smallest sensible set of changes.
- Move, split, defer, or re-plan work where appropriate.
- Explain meaningful changes and show what needs the user's attention.
- Be honest when there is not enough time to complete everything.

Chrona should not treat a missed task as a failure. It should help the user recover and move forward.

### F. Make useful suggestions proactively

Chrona should help answer "What should I do next?" without requiring the user to manually review every task.

Useful proactive assistance can include:
- A daily plan and a concise view of the day's priorities.
- Important tasks that are at risk of being forgotten or becoming overdue.
- Suggested quick wins when they genuinely help.
- Warnings about overloaded days, deadline risks, and schedule conflicts.
- A short end-of-day review of completed, unfinished, and upcoming work.
- Reminders and follow-ups at useful times, without creating notification overload.

Suggestions should be timely and actionable, not a stream of generic productivity advice.

## 3. Task behavior and scheduling rules

Chrona should understand three broad kinds of time commitments:

### Fixed commitments
Events such as classes, appointments, a scheduled class, or a commitment with another person. These should be treated as unavailable time unless the user explicitly permits a change.

### Flexible tasks
Work that needs to be completed but can be placed in a suitable available slot, such as studying, an assignment, or personal work.

### Changeable tasks
Tasks that can be moved, split into sessions, shortened where appropriate, or deferred when the day changes. Their deadlines and required outcomes still matter.

For every plan, Chrona should reason about feasibility. It must not claim a task is scheduled successfully if the resulting slot conflicts with another commitment, exceeds the available time, or violates a hard deadline.

If a task is too large for one slot, Chrona should consider breaking it into meaningful sessions while preserving the total work required. If a plan is impossible, it should state why and help the user choose what to change.

## 4. How Chrona should interact with the user

The interface and conversation should reduce cognitive load.

- Let users capture thoughts quickly without deciding where every item belongs first.
- Ask a follow-up question only when the missing detail could materially change the plan.
- Make reasonable, low-risk assumptions when appropriate, and make important assumptions visible.
- Clearly distinguish a suggestion from an action that has actually been applied.
- Seek confirmation before consequential changes, such as moving an important appointment or changing an externally synced calendar event.
- Allow quick corrections such as "move it to tomorrow," "keep the class fixed," or "split this into two sessions."
- Keep the main plan easy to scan, with deeper explanations available when needed.
- Avoid clutter, excessive text, unnecessary setup, and a UI that forces users to manage the planner instead of using it.

The assistant should feel supportive, calm, practical, and context-aware—not bossy, judgmental, or overconfident.

## 5. Learning and personalization

Chrona should gradually improve its recommendations from useful, appropriate signals, including:

- Actual versus estimated task duration.
- Tasks completed, postponed, or repeatedly rescheduled.
- Preferred time windows for different kinds of work.
- Typical routines and recurring commitments.
- User corrections to AI interpretation or schedule proposals.
- Explicit preferences such as keeping evenings free or allowing a buffer before classes.

Personalization should remain revisable. Chrona should not assume that a past behavior is a permanent preference, and users should be able to correct what it has learned.

## 6. Product direction inspired by Saner.AI

Public Saner.AI materials describe a broader personal assistant experience, rather than only a calendar or checklist. The publicly described themes most relevant to Chrona are:

- **Natural capture:** add thoughts and tasks through text or voice.
- **AI-assisted organization:** turn unstructured information into useful tasks and structured knowledge.
- **Daily planning:** surface priorities and help users decide where to start.
- **Calendar-based scheduling:** connect tasks to actual time blocks and existing commitments.
- **Context-aware assistance:** use relevant saved information to answer questions and connect related items.
- **Proactive support:** bring important follow-ups and reminders to the user's attention.
- **Task breakdown and rescheduling:** help turn large tasks into manageable work and adapt plans as circumstances change.
- **A low-friction experience:** reduce context switching and the effort needed to maintain a productivity system.

Chrona's initial product goal is to capture the core value of this experience: **an assistant that understands what is going on in the user's life and actively helps them plan and follow through.** It should not be limited to reproducing a particular screen or feature list. Improvements should focus on reliability, realistic schedules, clear explanations, and graceful recovery when plans change.

These points are based on public Saner.AI product pages and articles. They are not claims that Chrona's author has personally tested every Saner.AI feature.

## 7. Example of the intended behavior

**User:** "Tomorrow I have college from 9 to 3, a flute class at 5:30, and an assignment due Thursday. I need about three hours for it, and I also need to work out."

**Chrona should:**

1. Recognize college and flute class as fixed commitments.
2. Understand the assignment's deadline and estimated effort.
3. Consider travel, preparation, meals, recovery, and reasonable transitions.
4. Find realistic work blocks before the deadline, potentially splitting the assignment if useful.
5. Fit the workout only where it makes sense without creating an impossible day.
6. Flag any assumptions that materially affect feasibility.
7. Present a coherent plan and explain important trade-offs.
8. If there is not enough time, say so and offer alternatives rather than pretending everything fits.

**Later:** "I didn't finish the assignment today."

Chrona should reassess the remaining time before Thursday, preserve fixed commitments, move or split flexible tasks where feasible, and clearly explain any new risks. It should not blindly push everything into the next available slot.

## 8. What success looks like

Chrona is succeeding when:

- A user can describe a messy day naturally and receive a useful plan.
- Explicit times, deadlines, and fixed commitments are respected.
- Schedules are feasible and include realistic transitions.
- Conflicts are detected and resolved transparently.
- Missed tasks lead to thoughtful recovery plans rather than cascading failures.
- The assistant remembers relevant context and becomes more helpful over time.
- Users know what to do next without spending more time maintaining the tool.
- The application never pretends an action succeeded when it did not.

## 9. Boundaries and principles

- Do not fabricate task details, observations, or completed actions.
- Do not hide infeasible schedules behind confident language.
- Do not silently change fixed commitments or make consequential external changes without appropriate confirmation.
- Do not overwhelm users with notifications or excessive interface complexity.
- Treat personal information and connected data carefully, with clear permissions and access controls.
- Prefer useful, understandable assistance over flashy AI behavior.

## 10. Saner.AI observation status

This document defines Chrona's product vision using the user's previously stated goals and publicly available Saner.AI material. It is **not** a verified, step-by-step audit of Saner.AI's authenticated product.

Before claiming exact feature parity, conduct authorized hands-on observation of the relevant workflows and record evidence for:
- Creating a plan from natural language.
- How fixed events and flexible tasks are treated.
- How requested start times, duration, and deadlines are handled.
- What happens when tasks overlap or the day is overloaded.
- Whether tasks can be moved, split, deferred, or automatically rescheduled.
- Which changes require confirmation.
- What persists after edits or refreshes.
- How daily planning and proactive suggestions behave.

Clearly label each finding as **observed**, **publicly documented**, or **not yet verified**. Do not invent private behavior or use this document as proof of testing.

## Public references

1. Saner.AI — product overview and use cases: https://www.saner.ai/
2. Saner.AI — Getting Started guide: https://www.saner.ai/blogs/getting-started-with-saner-ai
3. Saner.AI — AI task management guide: https://blog.saner.ai/ai-task-management-guide/
4. Saner.AI — product vision and mission: https://www.saner.ai/about-us
5. Saner.AI — task assistant, mobile, and focus-mode update: https://www.saner.ai/blogs/release-note-14-feb-2025

**Final product statement:** Chrona should be an intelligent, context-aware personal assistant that turns a user's intentions into a realistic plan, continuously helps keep that plan aligned with real life, and reduces the mental burden of deciding, remembering, and reorganizing everything alone.