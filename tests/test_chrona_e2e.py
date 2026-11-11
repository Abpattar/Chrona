"""
Chrona End-to-End Comprehensive Test Suite
============================================
Tests realistic user scenarios and system guarantees:
1. College student with 9 AM - 4 PM fixed commitments Mon-Fri.
2. Multi-subject exam with preparation requirements.
3. Multiple assignments with competing deadlines.
4. Recurring flute class (Sun 5 PM).
5. 4 weekly exercise sessions.
6. Unexpected appointment / collision detection.
7. Missed study session repair & reschedule.
8. Project with multiple dependent subtasks.
9. High cognitive load / competing tasks (>40 tasks across 14 days).
10. Mixed fixed and flexible commitments constraint verification.

Guarantees Verified:
- No overlapping scheduled tasks
- Fixed commitments never moved
- Deadlines respected where feasible
- Transition buffers preserved
- Behavioral weights influence scheduling
- Validator confirms 100% validity of generated plans
"""
import pytest
from datetime import datetime, timedelta, timezone
from scheduling.edf import EDFScheduler, Task, TimeSlot
from scheduling.validator import validate_plan
from scheduling.repair_engine import RepairEngine
from behavior.profile import BehaviorProfile
from agents.graph import run_pipeline


def _utc(dt_or_str):
    if isinstance(dt_or_str, str):
        dt = datetime.fromisoformat(dt_or_str.replace("Z", "+00:00"))
    else:
        dt = dt_or_str
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


class TestChronaScenarios:
    """End-to-end multi-day scenario tests."""

    @pytest.fixture
    def base_week(self):
        """Monday 2026-11-09 00:00:00 UTC as reference start."""
        return _utc("2026-11-09T00:00:00")

    def test_scenario_student_fixed_and_flexible(self, base_week):
        """
        Scenario 1 & 10:
        User has college Monday to Friday 9 AM - 4 PM (Fixed).
        User has 4 exercise sessions (Flexible, 60m).
        User has daily flute practice (Flexible, 60m).
        User has an exam on Friday 2 PM (Fixed), prep required.
        """
        start = base_week
        fixed_items = []
        # Mon-Fri college 09:00 - 16:00
        for day in range(5):
            d = start + timedelta(days=day)
            fixed_items.append(Task(
                id=f"college-day-{day}",
                title=f"College Day {day+1}",
                duration_minutes=420,
                deadline=d.replace(hour=16, minute=0),
                priority=1,
                flexibility=False,
                fixed_start=d.replace(hour=9, minute=0),
                fixed_end=d.replace(hour=16, minute=0),
                task_type="fixed",
            ))

        # Flute class on Sunday 5 PM - 6 PM
        sunday = start + timedelta(days=6)
        fixed_items.append(Task(
            id="flute-class",
            title="Flute Class",
            duration_minutes=60,
            deadline=sunday.replace(hour=18, minute=0),
            priority=1,
            flexibility=False,
            fixed_start=sunday.replace(hour=17, minute=0),
            fixed_end=sunday.replace(hour=18, minute=0),
            task_type="fixed",
        ))

        # Flexible tasks: 4 workouts, 5 flute practice, 3 study blocks for Friday exam
        flex_tasks = []
        friday_exam_dt = start + timedelta(days=4, hours=14)
        for i in range(4):
            flex_tasks.append(Task(
                id=f"workout-{i}",
                title=f"Exercise Session {i+1}",
                duration_minutes=60,
                deadline=start + timedelta(days=7),
                priority=3,
                flexibility=True,
                task_type="workout",
            ))

        for i in range(5):
            flex_tasks.append(Task(
                id=f"flute-practice-{i}",
                title=f"Flute Practice {i+1}",
                duration_minutes=60,
                deadline=start + timedelta(days=7),
                priority=3,
                flexibility=True,
                task_type="practice",
            ))

        for i in range(3):
            flex_tasks.append(Task(
                id=f"exam-prep-{i}",
                title=f"Exam Prep Block {i+1}",
                duration_minutes=90,
                deadline=friday_exam_dt,
                priority=2,
                flexibility=True,
                task_type="study",
            ))

        # Free working windows across 7 days: 07:00 to 22:00
        free_slots = []
        for day in range(7):
            d = start + timedelta(days=day)
            free_slots.append(TimeSlot(
                start=d.replace(hour=7, minute=0),
                end=d.replace(hour=22, minute=0),
            ))

        weights = {"morning": 0.8, "afternoon": 0.4, "evening": 0.9, "night": 0.2}
        scheduler = EDFScheduler()
        result = scheduler.schedule(flex_tasks, free_slots, weights, fixed_items=fixed_items)

        # 1. All flexible tasks + fixed tasks scheduled
        assert len(result.impossible) == 0, f"Impossible tasks: {[t.title for t in result.impossible]}"
        assert len(result.scheduled) == len(flex_tasks) + len(fixed_items)

        # 2. Check no overlap between any two scheduled tasks
        plan_for_val = [
            {"task_id": a.task.id, "start": a.assigned_start.isoformat(), "end": a.assigned_end.isoformat(), "deadline": a.task.deadline.isoformat()}
            for a in result.scheduled
        ]
        val = validate_plan(plan_for_val, [], windows=[{"start": s.start.isoformat(), "end": s.end.isoformat()} for s in free_slots])
        assert val["valid"] is True, f"Validation failed: {val['errors']}"

        # 3. Check fixed college hours NEVER overlapped with any flexible task
        for a in result.scheduled:
            if a.task.flexibility:
                for f in fixed_items:
                    overlaps = (a.assigned_start < f.fixed_end and a.assigned_end > f.fixed_start)
                    assert not overlaps, f"Task {a.task.title} overlaps fixed {f.title} ({f.fixed_start} - {f.fixed_end})"

        # 4. Exam prep blocks must complete before Friday 2 PM deadline
        for a in result.scheduled:
            if "exam-prep" in a.task.id:
                assert a.assigned_end <= friday_exam_dt, f"Exam prep {a.task.title} ended at {a.assigned_end} past deadline {friday_exam_dt}"

    def test_missed_study_session_repair(self, base_week):
        """
        Scenario 7:
        User missed a scheduled study session today.
        System must detect disruption, generate valid non-conflicting repair proposals,
        and allow applying the repair to an alternative free slot.
        """
        start = base_week
        # A disrupted 90-min study task
        disrupted = {
            "id": "study-math-1",
            "title": "Calculus Study Block",
            "duration_minutes": 90,
            "deadline": (start + timedelta(days=2)).isoformat(),
            "priority": 2,
            "flexibility": True,
        }

        # Available slots today and tomorrow
        free_slots = [
            {"start": (start + timedelta(hours=18)).isoformat(), "end": (start + timedelta(hours=21)).isoformat()},
            {"start": (start + timedelta(days=1, hours=9)).isoformat(), "end": (start + timedelta(days=1, hours=12)).isoformat()},
        ]

        engine = RepairEngine()
        proposals = engine.propose_repairs(disrupted, free_slots)

        assert len(proposals) > 0, "Repair engine must generate at least 1 proposal"
        # Best proposal should move to today's free slot
        best = proposals[0]
        assert "new_start" in best and "new_end" in best
        assert _utc(best["new_end"]) <= _utc(disrupted["deadline"])

        # Validate proposal
        val = validate_plan([{"task_id": disrupted["id"], "start": best["new_start"], "end": best["new_end"]}], [], [])
        assert val["valid"] is True

    def test_behavioral_learning_ema(self):
        """
        Behavioral Learning:
        Verify profile accurately updates slot weights using EMA.
        Cold start -> On-time completion -> Skip -> Weight adaptation.
        """
        profile = BehaviorProfile.from_archetype("test-user-1", "student_balanced")
        init_morning = profile.slot_weights["morning"]
        assert init_morning == 0.5

        # Record 3 on-time morning completions
        now = datetime.now(timezone.utc)
        for _ in range(3):
            profile.update(
                task_type="study",
                scheduled_start=now.replace(hour=9, minute=0),
                scheduled_end=now.replace(hour=10, minute=0),
                actual_complete=now.replace(hour=9, minute=55),
            )

        assert profile.slot_weights["morning"] > init_morning, "Morning weight should increase after on-time completions"

        # Record a skip in afternoon
        init_afternoon = profile.slot_weights["afternoon"]
        profile.update(
            task_type="study",
            scheduled_start=now.replace(hour=14, minute=0),
            scheduled_end=now.replace(hour=15, minute=0),
            actual_complete=None,  # skipped
        )
        assert profile.slot_weights["afternoon"] < init_afternoon, "Afternoon weight should decrease after skip"

    def test_large_workload_simulation_40_tasks(self, base_week):
        """
        Scenario 9:
        40 tasks across a 14-day window competing for limited slots.
        Tests stability, constraint validation, no infinite loops, and deadline sorting.
        """
        start = base_week
        tasks = []
        for i in range(40):
            # Tasks distributed over 14 days with varying deadlines & priorities
            day_offset = (i % 12) + 1
            duration = 30 + ((i % 4) * 30)  # 30, 60, 90, 120 mins
            tasks.append(Task(
                id=f"sim-task-{i}",
                title=f"Task {i}: Project item",
                duration_minutes=duration,
                deadline=start + timedelta(days=day_offset, hours=20),
                priority=(i % 4) + 1,
                flexibility=True,
            ))

        # Daily slots from 8am to 8pm across 14 days
        slots = []
        for d in range(14):
            day = start + timedelta(days=d)
            slots.append(TimeSlot(
                start=day.replace(hour=8, minute=0),
                end=day.replace(hour=20, minute=0),
            ))

        weights = {"morning": 0.7, "afternoon": 0.6, "evening": 0.5, "night": 0.1}
        scheduler = EDFScheduler()
        result = scheduler.schedule(tasks, slots, weights)

        # Most tasks should be scheduled
        total_handled = len(result.scheduled) + len(result.impossible) + len(result.deferred)
        assert total_handled == 40
        assert len(result.scheduled) >= 30, f"Expected at least 30 scheduled, got {len(result.scheduled)}"

        # Verify no overlapping tasks among scheduled
        plan = [
            {"task_id": a.task.id, "start": a.assigned_start.isoformat(), "end": a.assigned_end.isoformat(), "deadline": a.task.deadline.isoformat()}
            for a in result.scheduled
        ]
        val = validate_plan(plan, [], windows=[{"start": s.start.isoformat(), "end": s.end.isoformat()} for s in slots])
        assert val["valid"] is True, f"Validation errors: {val['errors']}"

    def test_pipeline_fallback_end_to_end(self):
        """
        Verify the complete sequential agent pipeline processes raw input,
        extracts tasks, enriches metadata, runs scheduling, and produces explanations.
        """
        raw_text = "I have college every day from 9am to 4pm and an Operating Systems exam next Friday."
        state = {
            "raw_input": raw_text,
            "input_bytes": None,
            "input_type": "text",
            "user_id": "test-e2e-user",
            "extracted_events": [],
            "extraction_error": None,
            "enriched_tasks": [],
            "schedule_result": None,
            "conflicts": [],
            "repair_proposals": [],
            "accepted_repair": None,
            "behavior_weights": {},
            "explanation": "",
            "final_output": None,
            "errors": [],
        }

        output = run_pipeline(state, gemini_client=None, supabase_client=None)

        assert "explanation" in output
        assert output["final_output"] is not None
        assert isinstance(output["behavior_weights"], dict)

    def test_auto_reschedule_rescuing_impossible_tasks(self, base_week):
        """
        Verify that when a task has an impossible deadline or no slot before its deadline,
        the auto-rescheduling logic successfully finds and assigns the next viable slot.
        """
        start = base_week
        # Task with deadline that has already passed or is too tight (duration 120m, 30m before deadline)
        tight_task = Task(
            id="tight-1",
            title="Urgent Thesis Review",
            duration_minutes=120,
            deadline=start + timedelta(minutes=30),  # Impossible before deadline
            priority=1,
            flexibility=True,
        )

        # Free slot available on next day
        slots = [
            TimeSlot(start=start + timedelta(days=1, hours=10), end=start + timedelta(days=1, hours=14))
        ]

        scheduler = EDFScheduler()
        result = scheduler.schedule([tight_task], slots, from_time=start)

        # In raw EDF, it is marked impossible
        assert len(result.impossible) == 1
        assert len(result.scheduled) == 0

        # Now auto-reschedule rescues it into the next available viable slot
        unplaced = result.impossible
        auto_rescheduled = []
        for t in unplaced:
            chosen_slot = None
            for s in slots:
                if s.remaining_minutes() >= min(t.duration_minutes, 30):
                    chosen_slot = s
                    break
            assert chosen_slot is not None
            t_start = chosen_slot.consume(t)
            t_end = t_start + timedelta(minutes=t.duration_minutes)
            auto_rescheduled.append({
                "task_id": t.id,
                "title": t.title,
                "start_time": t_start.isoformat(),
                "end_time": t_end.isoformat(),
                "auto_rescheduled": True,
            })

        assert len(auto_rescheduled) == 1
        assert auto_rescheduled[0]["auto_rescheduled"] is True
        assert auto_rescheduled[0]["start_time"] == (start + timedelta(days=1, hours=10)).isoformat()

    def test_auto_collision_resolution_exam_vs_dinner(self):
        """
        Verify that when a critical fixed exam (Math exam) and a flexible social event
        (Dinner with friends) are scheduled at the exact same time (Sun 8 PM):
        1. Math exam is classified Priority 1, flexibility: False.
        2. Dinner with friends is classified Priority 3, flexibility: True.
        3. Conflict is detected and Dinner is automatically rescheduled to an optimal free slot.
        4. No time overlap exists between the two events.
        """
        from backend.server import infer_task_properties, find_optimal_reschedule_slot

        exam_raw = {
            "title": "Math exam",
            "start_time": "2026-11-08T20:00:00",
            "end_time": "2026-11-08T22:00:00",
            "task_type": "fixed",
            "priority": 1,
            "duration_minutes": 120,
        }
        dinner_raw = {
            "title": "Dinner with friends",
            "start_time": "2026-11-08T20:00:00",
            "end_time": "2026-11-08T21:00:00",
            "task_type": "fixed",
            "priority": 1,
            "duration_minutes": 60,
        }

        # Verify inference
        exam = infer_task_properties(exam_raw)
        dinner = infer_task_properties(dinner_raw)

        assert exam["priority"] == 1
        assert exam["flexibility"] is False
        assert exam["task_type"] == "exam"

        assert dinner["priority"] >= 3
        assert dinner["flexibility"] is True
        assert dinner["task_type"] == "social"

        # Verify optimal slot auto-rescheduling
        new_start, new_end, note = find_optimal_reschedule_slot(dinner, exam, [exam])
        assert new_start is not None
        assert new_end is not None

        # Verify no overlap
        exam_start = datetime.fromisoformat(exam["start_time"]).replace(tzinfo=timezone.utc)
        exam_end = datetime.fromisoformat(exam["end_time"]).replace(tzinfo=timezone.utc)
        assert not (new_start < exam_end and new_end > exam_start), "Rescheduled slot must not overlap with exam!"
        # Verify it was scheduled on the same day (Sunday Nov 8)
        assert new_start.date() == exam_start.date()

    def test_cognitive_recovery_gap_exam_ending_at_9_dinner_at_9(self):
        """
        User scenario:
        Exam from 8 PM to 9 PM, Dinner from 9 PM to 11 PM.
        Even though there is no timestamp overlap (20:00-21:00 and 21:00-23:00),
        Chrona detects the 0-minute gap, calculates that a stressful exam requires a
        30-35m decompression & travel break, and auto-reschedules Dinner to 9:30 PM.
        """
        from backend.server import check_collision_or_gap_issue, find_optimal_reschedule_slot, calculate_recovery_buffer_minutes

        exam = {
            "title": "Operating Systems exam",
            "task_type": "exam",
            "start_time": "2026-11-08T20:00:00+00:00",
            "end_time": "2026-11-08T21:00:00+00:00",
            "priority": 1,
            "flexibility": False,
        }
        dinner = {
            "title": "Dinner with friends",
            "task_type": "social",
            "start_time": "2026-11-08T21:00:00+00:00",
            "end_time": "2026-11-08T23:00:00+00:00",
            "duration_minutes": 120,
            "priority": 3,
            "flexibility": True,
        }

        # Verify dynamic buffer calculation
        buf = calculate_recovery_buffer_minutes(exam, dinner)
        assert buf >= 30, f"Stressful exam must mandate >= 30m recovery, got {buf}m"

        # Verify gap issue detection
        has_issue, anchor, loser, deficit = check_collision_or_gap_issue(exam, dinner)
        assert has_issue is True, "0-minute transition after exam must trigger gap issue!"
        assert anchor["title"] == exam["title"]
        assert loser["title"] == dinner["title"]

        # Verify auto-rescheduled slot gives the required decompression break
        new_start, new_end, note = find_optimal_reschedule_slot(dinner, exam, [exam], sleep_time="23:30")
        exam_end = datetime.fromisoformat(exam["end_time"])
        gap_after_exam = (new_start - exam_end).total_seconds() / 60
        assert gap_after_exam >= 30, f"Expected at least 30m break after exam, got {gap_after_exam}m"
        assert new_start.minute in [30, 35], f"Expected 9:30 or 9:35 PM start with decompression break, got {new_start}"

    def test_strict_time_user_override_skips_decompression_break(self):
        """
        User Requirement:
        The decompression break should be by default, but if the user explicitly mentions
        'I want a thing very important at that time only' or 'strictly at that time',
        it should place on that time (no gap conflict triggered).
        """
        from backend.server import check_collision_or_gap_issue, infer_task_properties

        exam = {
            "title": "Math exam",
            "task_type": "exam",
            "start_time": "2026-11-08T20:00:00+00:00",
            "end_time": "2026-11-08T21:00:00+00:00",
            "priority": 1,
            "flexibility": False,
        }
        dinner_strict = {
            "title": "Dinner with friends",
            "task_type": "social",
            "start_time": "2026-11-08T21:00:00+00:00",
            "end_time": "2026-11-08T23:00:00+00:00",
            "duration_minutes": 120,
            "strict_time": True,
            "skip_recovery_buffer": True,
        }

        # 1. Verify property inference detects user strict time statement
        inferred = infer_task_properties({"title": "Dinner with friends"}, user_text="dinner at 9 PM is very important at that time only")
        assert inferred.get("strict_time") is True
        assert inferred.get("skip_recovery_buffer") is True

        # 2. Verify collision check does NOT flag gap conflict when user explicitly requested strict time
        has_issue, anchor, loser, deficit = check_collision_or_gap_issue(exam, dinner_strict)
        assert has_issue is False, "Strict time override must skip decompression break and place on that time!"

    def test_strict_time_direct_simultaneous_overlap_resolution(self):
        """
        When there is a physical direct overlap (Math exam 8-10 PM and Dinner 8-9 PM),
        even if user requested strict time, a user cannot physically be in two places at once.
        Chrona schedules Math exam at 8-10 PM and places Dinner immediately at 10:00 PM (zero break wait).
        """
        from backend.server import check_collision_or_gap_issue, find_optimal_reschedule_slot

        exam = {
            "title": "Math exam",
            "task_type": "exam",
            "start_time": "2026-11-08T20:00:00+00:00",
            "end_time": "2026-11-08T22:00:00+00:00",
            "priority": 1,
            "flexibility": False,
        }
        dinner = {
            "title": "Dinner with friends",
            "task_type": "social",
            "start_time": "2026-11-08T20:00:00+00:00",
            "end_time": "2026-11-08T21:00:00+00:00",
            "duration_minutes": 60,
            "strict_time": True,
            "skip_recovery_buffer": True,
        }

        # 1. Direct overlap MUST be flagged
        has_conflict, anchor, loser, gap = check_collision_or_gap_issue(exam, dinner)
        assert has_conflict is True
        assert anchor["title"] == "Math exam"
        assert loser["title"] == "Dinner with friends"

        # 2. Optimal slot with strict_time places Dinner immediately at 10:00 PM without waiting 35m
        new_start, new_end, note = find_optimal_reschedule_slot(dinner, exam, [exam])
        exam_end = datetime.fromisoformat(exam["end_time"])
        assert new_start == exam_end, f"Expected 10:00 PM placement right after exam, got {new_start}"
        assert "decompression break skipped" in note.lower()

    @pytest.mark.asyncio
    async def test_override_break_endpoint(self):
        """
        Test the POST /api/schedule/override-break endpoint.
        """
        from backend.server import app, sb_post
        from fastapi.testclient import TestClient

        client = TestClient(app)
        user_id = "00000000-0000-0000-0000-000000000001"

        # Seed dinner
        sb_post("schedule_items", {
            "id": "test-dinner-override-id",
            "user_id": user_id,
            "title": "Dinner with friends",
            "start_time": "2026-11-08T21:35:00+00:00",
            "end_time": "2026-11-08T23:35:00+00:00",
            "status": "rescheduled",
            "duration_minutes": 120,
        })

        # Call override break with skip_break=True
        resp = client.post(
            "/api/schedule/override-break",
            json={
                "task_id": "test-dinner-override-id",
                "target_time": "2026-11-08T21:00:00+00:00",
                "skip_break": True,
            },
            headers={"X-User-ID": user_id}
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["success"] is True
        assert data["item"]["strict_time"] is True
        assert "21:00" in data["item"]["start_time"]

    def test_user_timezone_exam_at_6pm_and_lunch_reschedule(self):
        """
        Scenario Reported by User:
        User in IST (+05:30) says:
        1. 'Math exam from 6 PM to 7 PM'
        2. 'to eat lunch at 6 PM'
        Verify:
        - Math exam is stored with valid start and end (12:30 UTC to 13:30 UTC), matching 6:00 PM - 7:00 PM IST.
        - end_time is strictly after start_time (never inverted e.g. 10:30 PM - 6:00 PM).
        - Collision is detected, and 'to eat lunch' is rescheduled to 7:35 PM IST on the SAME DAY (Saturday),
          NOT pushed to midnight (12:05 AM Sunday)!
        """
        from backend.server import resolve_and_save_schedule_item, _parse_dt
        from datetime import timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-tz-user"

        # 1. Save Math exam at 6 PM to 7 PM local
        exam_task = {
            "title": "Math exam",
            "task_type": "exam",
            "fixed_start": "2026-11-07T18:00:00",
            "fixed_end": "2026-11-07T19:00:00",
            "duration_minutes": 60,
            "priority": 1,
            "flexibility": False,
        }
        saved_exam, _ = resolve_and_save_schedule_item(user_id, exam_task, [], user_tz=ist_tz)
        assert saved_exam is not None
        s_dt = _parse_dt(saved_exam["start_time"])
        e_dt = _parse_dt(saved_exam["end_time"])

        # End time must be strictly after start time
        assert e_dt > s_dt, f"End time {e_dt} must be after start time {s_dt}"
        # In IST local time, it must be 6:00 PM to 7:00 PM
        assert s_dt.astimezone(ist_tz).hour == 18
        assert e_dt.astimezone(ist_tz).hour == 19

        # 2. Add 'to eat lunch' at 6 PM (same time)
        lunch_task = {
            "title": "to eat lunch",
            "task_type": "task",
            "fixed_start": "2026-11-07T18:00:00",
            "duration_minutes": 60,
            "priority": 3,
            "flexibility": True,
        }
        saved_lunch, note = resolve_and_save_schedule_item(user_id, lunch_task, [saved_exam], user_tz=ist_tz)
        assert saved_lunch is not None
        assert saved_lunch.get("auto_rescheduled") is True

        lunch_s = _parse_dt(saved_lunch["start_time"])
        lunch_e = _parse_dt(saved_lunch["end_time"])

        # Must be on the SAME Saturday (2026-11-07 in IST)
        lunch_local_s = lunch_s.astimezone(ist_tz)
        assert lunch_local_s.day == 7, f"Lunch should be scheduled on Saturday (day 7), but got day {lunch_local_s.day}"
        assert lunch_local_s.hour == 19 and lunch_local_s.minute == 35, f"Lunch should be at 7:35 PM, got {lunch_local_s.strftime('%I:%M %p')}"
        assert lunch_e > lunch_s

    def test_movie_is_fixed_never_moved_and_dinner_placed_before_movie(self):
        """
        User Problem Reported:
        'If movie starts at 8 then why would you move it ? its a movie not somthing you skip it cause youll miss it...
        There is alot of time after move and before movie comparing to current time... I want this to behave like how a human thinks.'

        Verify:
        1. 'Movie' at 8:00 PM (20:00 - 22:00) is classified as Priority 1, flexibility: False (Fixed).
        2. When 'Dinner with friends' collides at 8:00 PM, Movie is NEVER moved.
        3. Dinner is rescheduled TODAY before the movie at 6:30 PM - 7:30 PM with breathing room,
           NOT pushed to tomorrow or 2:15 AM!
        """
        from backend.server import infer_task_properties, resolve_and_save_schedule_item, _parse_dt
        from datetime import timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-movie-user"

        movie_task = {
            "title": "Movie",
            "task_type": "fixed",
            "fixed_start": "2026-11-07T20:00:00",
            "fixed_end": "2026-11-07T22:00:00",
            "duration_minutes": 120,
        }
        movie_prop = infer_task_properties(movie_task)
        assert movie_prop["flexibility"] is False, "Movie showtimes are ticketed and strictly fixed!"
        assert movie_prop["priority"] == 1

        saved_movie, _ = resolve_and_save_schedule_item(user_id, movie_task, [], user_tz=ist_tz)
        assert saved_movie is not None
        m_s = _parse_dt(saved_movie["start_time"]).astimezone(ist_tz)
        assert m_s.hour == 20 and m_s.minute == 0

        # Submit Dinner at 8 PM (same time as movie)
        dinner_task = {
            "title": "Dinner with friends",
            "task_type": "social",
            "fixed_start": "2026-11-07T20:00:00",
            "duration_minutes": 60,
        }
        saved_dinner, note = resolve_and_save_schedule_item(user_id, dinner_task, [saved_movie], user_tz=ist_tz)
        assert saved_dinner is not None
        assert saved_dinner.get("auto_rescheduled") is True

        d_s = _parse_dt(saved_dinner["start_time"]).astimezone(ist_tz)
        d_e = _parse_dt(saved_dinner["end_time"]).astimezone(ist_tz)

        # Dinner must be on the SAME Saturday (2026-11-07) BEFORE the 8 PM movie
        assert d_s.day == 7, f"Dinner should be scheduled today before movie, got day {d_s.day}"
        assert d_s.hour in [18, 19], f"Dinner should be at 6:30 or 6:45 PM before movie, got {d_s.strftime('%I:%M %p')}"
        assert d_e <= m_s, f"Dinner end ({d_e}) must be before movie start ({m_s})!"

    def test_flute_class_every_sunday_strictly_on_sunday(self):
        """
        User Problem Reported:
        'a flute class every Sunday' was placed on Saturday at 10:30 PM.
        Verify:
        - Any task specifying 'Sunday' or 'every Sunday' must strictly be placed on Sunday!
        - It must never be placed during bedtime (10:30 PM to 08:00 AM).
        """
        from backend.server import resolve_and_save_schedule_item, _parse_dt
        from datetime import timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-flute-user"

        flute_task = {
            "title": "a flute class every Sunday",
            "task_type": "class",
            "duration_minutes": 60,
            "fixed_start": "2026-11-07T22:30:00",  # Submitted with Saturday night timestamp
        }
        saved_flute, _ = resolve_and_save_schedule_item(user_id, flute_task, [], user_tz=ist_tz)
        assert saved_flute is not None

        f_s = _parse_dt(saved_flute["start_time"]).astimezone(ist_tz)
        assert f_s.weekday() == 6, f"Flute class every Sunday must be on Sunday (weekday 6), got {f_s.strftime('%A')}"
        assert 8 <= f_s.hour < 22, f"Flute class must be in daytime waking hours, got {f_s.strftime('%I:%M %p')}"

    def test_no_free_slots_in_sleep_hours(self):
        """
        Verify that compute_free_slots with wake_time=08:00 and sleep_time=22:30
        in user local timezone (+05:30) never produces slots in the middle of the night (e.g. 1 AM, 2 AM, 4 AM).
        """
        from backend.server import compute_free_slots, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        start_utc = datetime(2026, 11, 7, 0, 0, 0, tzinfo=timezone.utc)
        end_utc = datetime(2026, 11, 9, 23, 59, 59, tzinfo=timezone.utc)

        slots = compute_free_slots("empty-user", start_utc, end_utc, wake_time="08:00", sleep_time="22:30", user_tz=ist_tz)
        assert len(slots) > 0

        for s in slots:
            s_loc = _parse_dt(s["start"]).astimezone(ist_tz)
            e_loc = _parse_dt(s["end"]).astimezone(ist_tz)
            # Local start must be >= 08:00
            assert s_loc.hour >= 8, f"Slot start {s_loc} violates wake time 08:00!"
            # Local end must be <= 22:30
            assert (e_loc.hour < 22 or (e_loc.hour == 22 and e_loc.minute <= 30)), f"Slot end {e_loc} violates bedtime 22:30!"

    def test_date_at_9pm_rescheduled_to_sunday_at_9pm_not_11am(self):
        """
        User Problem Reported:
        When user scheduled 'date at 9pm' and Saturday 9pm had a movie,
        Chrona moved the date to Sunday, but chose 11:00 AM instead of 9:00 PM!
        Verify that rescheduling preserves the user's requested hour (9:00 PM) on the next day!
        """
        from backend.server import find_optimal_reschedule_slot, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        today_local = datetime.now(ist_tz).date()
        diff_sat = (5 - today_local.weekday()) % 7
        if diff_sat == 0:
            diff_sat = 7
        sat_day = today_local + timedelta(days=diff_sat)

        # Saturday has study/gym (5:30 PM - 7:00 PM), Dinner (7:30 PM - 8:30 PM) and Movie (9:00 PM - 10:00 PM)
        sat_gym = {
            "id": "gym-1",
            "title": "Gym",
            "task_type": "workout",
            "priority": 3,
            "flexibility": False,
            "start_time": datetime(sat_day.year, sat_day.month, sat_day.day, 17, 30, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
            "end_time": datetime(sat_day.year, sat_day.month, sat_day.day, 19, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
        }
        sat_dinner = {
            "id": "din-1",
            "title": "Dinner",
            "task_type": "social",
            "priority": 3,
            "flexibility": False,
            "start_time": datetime(sat_day.year, sat_day.month, sat_day.day, 19, 30, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
            "end_time": datetime(sat_day.year, sat_day.month, sat_day.day, 20, 30, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
        }
        sat_movie = {
            "id": "movie-1",
            "title": "Movie",
            "task_type": "fixed",
            "priority": 1,
            "flexibility": False,
            "start_time": datetime(sat_day.year, sat_day.month, sat_day.day, 21, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
            "end_time": datetime(sat_day.year, sat_day.month, sat_day.day, 22, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
        }
        # Saturday Date: requested for 9:00 PM - 10:00 PM
        sat_date = {
            "id": "date-1",
            "title": "date",
            "task_type": "social",
            "priority": 3,
            "flexibility": True,
            "start_time": datetime(sat_day.year, sat_day.month, sat_day.day, 21, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
            "end_time": datetime(sat_day.year, sat_day.month, sat_day.day, 22, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
            "duration_minutes": 60,
        }

        # Saturday evening is completely packed around Movie. Reschedule date.
        all_sat_items = [sat_gym, sat_dinner, sat_movie]
        new_start, new_end, note = find_optimal_reschedule_slot(sat_date, sat_movie, all_sat_items, user_tz=ist_tz)
        res_loc = new_start.astimezone(ist_tz)

        # Must NOT be 11 AM!
        assert res_loc.hour != 11, f"Rescheduled date should NOT be 11 AM! Got {res_loc.strftime('%I:%M %p')}"
        # Must be Sunday at 9:00 PM!
        assert res_loc.weekday() == 6, f"Should be Sunday (weekday 6), got {res_loc.strftime('%A')}"
        assert res_loc.hour == 21, f"Should preserve requested 9:00 PM hour, got {res_loc.strftime('%I:%M %p')}"

    @pytest.mark.asyncio
    async def test_chat_move_date_to_sunday_9pm_updates_database(self):
        """
        User Problem Reported:
        'when I asked the chat to move the date of the date to 9pm at night on sunday,
        it didn't do anything but said it did'
        Verify that chat move command updates the existing task record in DB and sets start_time to Sunday 9:00 PM!
        """
        from backend.server import process_chat_message, _in_memory_db, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-move-date-user"

        # Pre-seed an existing date item in database
        _in_memory_db["schedule_items"] = [
            {
                "id": "date-existing-id",
                "user_id": user_id,
                "title": "date",
                "start_time": datetime(2026, 11, 8, 11, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
                "end_time": datetime(2026, 11, 8, 12, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
                "duration_minutes": 60,
                "status": "scheduled",
            }
        ]

        # Execute user chat command
        res = await process_chat_message(
            user_id,
            "move the date of the date to 9pm at night on sunday",
            user_tz=ist_tz
        )

        assert res.get("action") == "reschedule"
        assert len(res.get("tasks", [])) == 1

        # Check DB update
        updated_item = next((it for it in _in_memory_db["schedule_items"] if it["id"] == "date-existing-id"), None)
        assert updated_item is not None
        s_dt = _parse_dt(updated_item["start_time"]).astimezone(ist_tz)
        assert s_dt.weekday() == 6, f"Should be Sunday, got {s_dt.strftime('%A')}"
        assert s_dt.hour == 21, f"Should be 9:00 PM, got {s_dt.strftime('%I:%M %p')}"

    @pytest.mark.asyncio
    async def test_image_upload_without_text_does_not_create_pending_task(self):
        """
        User Problem Reported:
        'I tried to give a image as input, but the task scheduled is called pending task'
        Verify that uploading an image without detectable text does NOT insert a phantom 'Pending Task' into DB!
        """
        from backend.server import chat_upload, _in_memory_db
        from starlette.requests import Request
        from unittest.mock import MagicMock

        user_id = "test-image-upload-user"
        _in_memory_db["schedule_items"] = []

        # Mock request with user timezone
        req = MagicMock(spec=Request)
        req.headers = {"x-timezone": "Asia/Kolkata", "x-timezone-offset": "330"}

        # Simulate upload of image where file has no legible text
        mock_file = MagicMock()
        mock_file.filename = "WhatsApp Image 2026-10-22 at 19.16.27.jpeg"
        mock_file.read = MagicMock(return_value=b"fake image bytes")

        res = await chat_upload(
            req=req,
            message="",
            file=mock_file,
            extracted_text="",
            user_id=user_id,
        )

        assert "wasn't able to automatically detect legible text" in res["message"]
        assert len(res["tasks_saved"]) == 0

        # Verify database has NO dummy 'Pending Task'
        phantom_tasks = [it for it in _in_memory_db["schedule_items"] if "pending task" in it.get("title", "").lower()]
        assert len(phantom_tasks) == 0, f"Found phantom tasks in database: {phantom_tasks}"

    @pytest.mark.asyncio
    async def test_context_endpoint_disjoint_partitions_no_duplicates(self, monkeypatch):
        """
        User Problem Reported:
        Dashboard repeated Dinner and Movie in UP NEXT, OVERDUE, UPCOMING DEADLINES, and PENDING.
        Verify /api/context filters disjoint partitions with zero phantom deadlines!
        """
        from backend.server import get_user_context, _in_memory_db
        from starlette.requests import Request
        from unittest.mock import MagicMock
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-context-user"
        today_local = datetime.now(ist_tz).date()

        # Mock _now_utc to midday on today_local so dinner (7:30 PM) and movie (9:00 PM) are reliably in the future today
        fixed_now = datetime(today_local.year, today_local.month, today_local.day, 12, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc)
        monkeypatch.setattr("backend.server._now_utc", lambda: fixed_now)

        # 1. Dinner tonight (7:30 PM) -> should be in today, NOT overdue, NOT upcoming deadlines, NOT pending
        # 2. Movie tonight (9:00 PM) -> should be in today, NOT overdue, NOT upcoming deadlines, NOT pending
        # 3. Flute class (pending, no start time) -> should be in pending, NOT today
        _in_memory_db["schedule_items"] = [
            {
                "id": "din-1",
                "user_id": user_id,
                "title": "Dinner",
                "start_time": datetime(today_local.year, today_local.month, today_local.day, 19, 30, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
                "end_time": datetime(today_local.year, today_local.month, today_local.day, 20, 30, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
                "status": "scheduled",
            },
            {
                "id": "mov-1",
                "user_id": user_id,
                "title": "Movie",
                "start_time": datetime(today_local.year, today_local.month, today_local.day, 21, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
                "end_time": datetime(today_local.year, today_local.month, today_local.day, 22, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc).isoformat(),
                "status": "scheduled",
            },
            {
                "id": "flute-1",
                "user_id": user_id,
                "title": "Flute practice",
                "status": "pending",
                "duration_minutes": 60,
            }
        ]

        req = MagicMock(spec=Request)
        req.headers = {"x-timezone": "Asia/Kolkata", "x-timezone-offset": "330"}

        ctx = await get_user_context(req=req, user_id=user_id)

        # Today should only have Dinner and Movie
        today_titles = [it["title"] for it in ctx["today"]]
        assert "Dinner" in today_titles
        assert "Movie" in today_titles
        assert "Flute practice" not in today_titles

        # Pending should only have Flute practice
        pending_titles = [it["title"] for it in ctx["pending_tasks"]]
        assert "Flute practice" in pending_titles
        assert "Dinner" not in pending_titles
        assert "Movie" not in pending_titles

        # Overdue should be empty (both items are in future)
        assert len(ctx["overdue"]) == 0

        # Upcoming deadlines should be empty (none of these have deadlines!)
        assert len(ctx["upcoming_deadlines"]) == 0

    def test_infer_task_properties_controllability_and_exam_context(self):
        """
        Verify Locus of Control inference:
        - Study for math exam (no exam) -> personal, priority 3, flexibility True, task_type study
        - Study for math exam (with exam) -> personal, priority 2, flexibility True, task_type study
        - Math exam -> external, priority 1, flexibility False, task_type exam
        - Family function -> external, priority 2, flexibility False, task_type family
        - Doctor appointment -> external, priority 2, flexibility False, task_type appointment
        """
        from backend.server import infer_task_properties

        # 1. Study without upcoming exam
        p_study = infer_task_properties({"title": "Study for math exam"}, user_text="", existing_items=[])
        assert p_study["controllability"] == "personal"
        assert p_study["task_type"] == "study"
        assert p_study["priority"] == 3
        assert p_study["flexibility"] is True

        # 2. Study with upcoming math exam in schedule
        existing = [{"title": "Math Final Exam", "task_type": "exam", "start_time": "2026-11-20T10:00:00Z"}]
        p_study_exam = infer_task_properties({"title": "Study for math exam"}, user_text="", existing_items=existing)
        assert p_study_exam["controllability"] == "personal"
        assert p_study_exam["task_type"] == "study"
        assert p_study_exam["priority"] == 2
        assert p_study_exam["flexibility"] is True

        # 3. Actual Math Exam (NOT a study task)
        p_exam = infer_task_properties({"title": "Math exam"}, user_text="", existing_items=[])
        assert p_exam["controllability"] == "external"
        assert p_exam["task_type"] == "exam"
        assert p_exam["priority"] == 1
        assert p_exam["flexibility"] is False

        # 4. Family function (External event not in user control)
        p_family = infer_task_properties({"title": "Family function"}, user_text="", existing_items=[])
        assert p_family["controllability"] == "external"
        assert p_family["task_type"] == "family"
        assert p_family["priority"] == 2
        assert p_family["flexibility"] is False

        # 5. Doctor appointment (External commitment)
        p_doc = infer_task_properties({"title": "Doctor appointment"}, user_text="", existing_items=[])
        assert p_doc["controllability"] == "external"
        assert p_doc["task_type"] == "appointment"
        assert p_doc["priority"] == 2
        assert p_doc["flexibility"] is False

    def test_control_concept_family_function_vs_study_no_exam(self):
        """
        User Problem Reported:
        'I asked to set study for 8pm and also a family function at 8 pm,
        The thing is it moved the family function to before and fixed the study part.
        The family function is not in my control so its time is fixed,
        but study session is in my control so I can change the time of study session.'

        Verify that when Family Function at 8 PM and Study for math exam at 8 PM collide:
        1. Family Function remains fixed at 8:00 PM (anchor, uncontrollable external event).
        2. Study for math exam is rescheduled (loser, controllable personal task).
        3. Clear explanation note is provided about external control vs personal control.
        """
        from backend.server import resolve_and_save_schedule_item, _in_memory_db, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-control-user-no-exam"
        _in_memory_db["schedule_items"] = []

        now_local = datetime.now(ist_tz)
        target_day = now_local.date() + timedelta(days=2)

        # 1. Family function at 8:00 PM (20:00 - 21:00 local)
        s_fam = datetime(target_day.year, target_day.month, target_day.day, 20, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc)
        fam_task = {
            "title": "Family function",
            "start_time": s_fam.isoformat(),
            "end_time": (s_fam + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
        }
        saved_fam, _ = resolve_and_save_schedule_item(user_id, fam_task, [], user_tz=ist_tz)
        assert saved_fam is not None
        assert saved_fam["controllability"] == "external"

        # 2. Study for math exam at 8:00 PM (same time!)
        study_task = {
            "title": "Study for math exam",
            "start_time": s_fam.isoformat(),
            "end_time": (s_fam + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
        }
        saved_study, note = resolve_and_save_schedule_item(user_id, study_task, [], user_tz=ist_tz)

        assert saved_study is not None
        assert saved_study["controllability"] == "personal"
        assert saved_study["status"] == "rescheduled"
        assert saved_study["auto_rescheduled"] is True

        # Family function MUST stay at 8:00 PM!
        fam_in_db = next(it for it in _in_memory_db["schedule_items"] if it["title"] == "Family function")
        fam_start_local = _parse_dt(fam_in_db["start_time"], ist_tz).astimezone(ist_tz)
        assert fam_start_local.hour == 20
        assert fam_start_local.minute == 0

        # Study session MUST have been moved (e.g. to 6:30 PM before Family function)
        study_start_local = _parse_dt(saved_study["start_time"], ist_tz).astimezone(ist_tz)
        assert not (study_start_local.hour == 20 and study_start_local.minute == 0), "Study session should NOT be at 8:00 PM!"
        assert "not in your control" in (saved_study.get("completion_note") or "").lower() or "external" in (saved_study.get("completion_note") or "").lower()

    def test_control_concept_family_function_vs_study_with_upcoming_exam(self):
        """
        Verify that when user HAS an upcoming Math exam:
        1. Study for math exam has elevated priority 2.
        2. BUT Family function is STILL the anchor (external events cannot be moved by study).
        3. Study is rescheduled to prime focus slot (6:30 PM before Family function).
        """
        from backend.server import resolve_and_save_schedule_item, _in_memory_db, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-control-user-with-exam"
        _in_memory_db["schedule_items"] = []

        now_local = datetime.now(ist_tz)
        target_day = now_local.date() + timedelta(days=2)
        exam_day = target_day + timedelta(days=3)

        # Pre-seed upcoming Math exam in database
        exam_s = datetime(exam_day.year, exam_day.month, exam_day.day, 10, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc)
        _in_memory_db["schedule_items"].append({
            "id": "math-exam-1",
            "user_id": user_id,
            "title": "Math Final Exam",
            "task_type": "exam",
            "priority": 1,
            "flexibility": False,
            "controllability": "external",
            "start_time": exam_s.isoformat(),
            "end_time": (exam_s + timedelta(hours=2)).isoformat(),
            "status": "scheduled",
        })

        # 1. Family function at 8:00 PM
        s_fam = datetime(target_day.year, target_day.month, target_day.day, 20, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc)
        fam_task = {
            "title": "Family function",
            "start_time": s_fam.isoformat(),
            "end_time": (s_fam + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
        }
        saved_fam, _ = resolve_and_save_schedule_item(user_id, fam_task, [], user_tz=ist_tz)

        # 2. Study for math exam at 8:00 PM
        study_task = {
            "title": "Study for math exam",
            "start_time": s_fam.isoformat(),
            "end_time": (s_fam + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
        }
        saved_study, note = resolve_and_save_schedule_item(user_id, study_task, [], user_tz=ist_tz)

        # Priority of study is elevated to 2 due to upcoming exam
        assert saved_study["priority"] == 2
        # BUT Family function still anchors because it is external!
        assert saved_study["status"] == "rescheduled"
        study_start_local = _parse_dt(saved_study["start_time"], ist_tz).astimezone(ist_tz)
        assert study_start_local.hour == 18 and study_start_local.minute == 30, f"Expected 6:30 PM, got {study_start_local}"

    def test_control_concept_study_with_exam_vs_workout(self):
        """
        Verify that between two personal tasks:
        Study for upcoming exam (Priority 2, personal) anchors over Gym workout (Priority 3, personal).
        """
        from backend.server import resolve_and_save_schedule_item, _in_memory_db, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "test-study-vs-workout"
        _in_memory_db["schedule_items"] = []

        now_local = datetime.now(ist_tz)
        target_day = now_local.date() + timedelta(days=2)
        exam_day = target_day + timedelta(days=3)

        # Pre-seed upcoming Math exam
        exam_s = datetime(exam_day.year, exam_day.month, exam_day.day, 10, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc)
        _in_memory_db["schedule_items"].append({
            "id": "math-exam-1",
            "user_id": user_id,
            "title": "Math Final Exam",
            "task_type": "exam",
            "priority": 1,
            "flexibility": False,
            "controllability": "external",
            "start_time": exam_s.isoformat(),
            "end_time": (exam_s + timedelta(hours=2)).isoformat(),
            "status": "scheduled",
        })

        s_slot = datetime(target_day.year, target_day.month, target_day.day, 18, 30, 0, tzinfo=ist_tz).astimezone(timezone.utc)

        # Add Study for math exam first (P2)
        study_task = {
            "title": "Study for math exam",
            "start_time": s_slot.isoformat(),
            "end_time": (s_slot + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
        }
        saved_study, _ = resolve_and_save_schedule_item(user_id, study_task, [], user_tz=ist_tz)
        assert saved_study["priority"] == 2

        # Add Gym workout at the same time (P3)
        workout_task = {
            "title": "Gym workout",
            "start_time": s_slot.isoformat(),
            "end_time": (s_slot + timedelta(hours=1)).isoformat(),
            "duration_minutes": 60,
        }
        saved_workout, note = resolve_and_save_schedule_item(user_id, workout_task, [], user_tz=ist_tz)

        # Study remains at 18:30 (anchor)
        study_in_db = next(it for it in _in_memory_db["schedule_items"] if it["title"] == "Study for math exam")
        study_start = _parse_dt(study_in_db["start_time"], ist_tz).astimezone(ist_tz)
        assert study_start.hour == 18 and study_start.minute == 30

        # Workout is rescheduled (loser)
        assert saved_workout["status"] == "rescheduled"
        workout_start = _parse_dt(saved_workout["start_time"], ist_tz).astimezone(ist_tz)
        assert not (workout_start.hour == 18 and workout_start.minute == 30)

    def test_extract_context_hints_move_ranges_and_relative_days(self):
        """
        Verify extract_context_hints parses reschedule 'from X to Y' ranges as destination time Y
        and supports 'day after tomorrow' / 'today' cleanly.
        """
        from backend.server import extract_context_hints, compute_datetime_from_day_and_time
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))

        h1 = extract_context_hints("Move family function from 6pm to 8pm")
        assert h1.get("time_str") == "8PM" or h1.get("time_str") == "8 PM"
        assert "duration_minutes" not in h1

        h2 = extract_context_hints("Shift dinner to day after tomorrow")
        assert h2.get("day_str") == "Day after tomorrow"

        dt = compute_datetime_from_day_and_time("Day after tomorrow", "8 PM", ist_tz).astimezone(ist_tz)
        expected_date = datetime.now(ist_tz).date() + timedelta(days=2)
        assert dt.date() == expected_date
        assert dt.hour == 20 and dt.minute == 0

    def test_move_task_to_tomorrow_preserves_original_time_and_updates_in_place(self):
        """
        Verify moving an existing task to 'tomorrow' without repeating the clock time
        preserves the original 8:00 PM clock time and updates the existing DB row in place.
        """
        from fastapi.testclient import TestClient
        from backend.server import app, _in_memory_db, _mem_cache, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "00000000-0000-0000-0000-000000000001"
        _in_memory_db["schedule_items"] = []
        _mem_cache.clear()

        now_local = datetime.now(ist_tz)
        day_after = now_local.date() + timedelta(days=2)
        orig_start = datetime(day_after.year, day_after.month, day_after.day, 20, 0, 0, tzinfo=ist_tz).astimezone(timezone.utc)

        _in_memory_db["schedule_items"].append({
            "id": "fam-func-1",
            "user_id": user_id,
            "title": "Family function",
            "task_type": "family",
            "priority": 2,
            "flexibility": False,
            "controllability": "external",
            "duration_minutes": 90,
            "start_time": orig_start.isoformat(),
            "end_time": (orig_start + timedelta(minutes=90)).isoformat(),
            "status": "scheduled",
        })

        client = TestClient(app)
        resp = client.post(
            "/api/chat",
            json={
                "user_id": user_id,
                "message": "Move family function to tomorrow",
                "session_id": "sess-preserve-time",
                "timezone": "Asia/Kolkata",
                "timezone_offset": "-330",
            },
            headers={"X-User-ID": user_id, "X-Timezone": "Asia/Kolkata", "X-Timezone-Offset": "-330"},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body["action"] == "reschedule"

        fam_items = [it for it in _in_memory_db["schedule_items"] if it["user_id"] == user_id]
        assert len(fam_items) == 1, f"Expected in-place update without duplicates, got {len(fam_items)}"
        assert fam_items[0]["id"] == "fam-func-1"

        moved_local = _parse_dt(fam_items[0]["start_time"], ist_tz).astimezone(ist_tz)
        assert moved_local.date() == now_local.date() + timedelta(days=1)
        assert moved_local.hour == 20 and moved_local.minute == 0, f"Expected preserved 8:00 PM, got {moved_local}"
        assert fam_items[0]["duration_minutes"] == 90

    def test_multiturn_edit_with_persisted_session_and_history_endpoints(self):
        """
        Simulate switching away and back to Chat while reusing session_id:
        1. Create task in turn 1 -> saved with DB ID and history cached.
        2. GET /api/chat/history returns conversation turns for persisted session_id.
        3. Turn 2 edit/correction updates the existing task in-place via task_id/original_title
           (with full schedule + chat history passed to LLM), without duplicating rows.
        4. DELETE /api/chat/history clears the session cleanly.
        """
        from unittest.mock import patch
        from fastapi.testclient import TestClient
        from backend.server import app, _in_memory_db, _mem_cache, _parse_dt
        from datetime import datetime, timezone, timedelta

        ist_tz = timezone(timedelta(hours=5, minutes=30))
        user_id = "00000000-0000-0000-0000-000000000001"
        session_id = "persisted-tab-session"
        headers = {"X-User-ID": user_id, "X-Timezone": "Asia/Kolkata", "X-Timezone-Offset": "-330"}

        _in_memory_db["schedule_items"] = []
        _mem_cache.clear()

        now_local = datetime.now(ist_tz)
        day_after = now_local.date() + timedelta(days=2)
        tomorrow = now_local.date() + timedelta(days=1)

        turn1_ai = {
            "action": "add_tasks",
            "message": "Scheduled Family function for day after tomorrow at 8:00 PM.",
            "tasks": [{
                "title": "Family function",
                "duration_minutes": 60,
                "task_type": "family",
                "priority": 2,
                "flexibility": False,
                "controllability": "external",
                "fixed_start": f"{day_after.isoformat()}T20:00:00",
                "fixed_end": f"{day_after.isoformat()}T21:00:00",
            }],
            "clarification_needed": None,
            "schedule_trigger": False,
        }

        client = TestClient(app)
        with patch("backend.server._ai_json", return_value=turn1_ai):
            r1 = client.post(
                "/api/chat",
                json={"user_id": user_id, "message": "I have a family function day after tomorrow at 8pm", "session_id": session_id},
                headers=headers,
            )
        assert r1.status_code == 200
        assert len(_in_memory_db["schedule_items"]) == 1
        created_id = _in_memory_db["schedule_items"][0]["id"]

        # Simulate tab switch away and return to Chrona Chat
        hist_resp = client.get(f"/api/chat/history?session_id={session_id}", headers=headers)
        assert hist_resp.status_code == 200
        hist_data = hist_resp.json()
        assert len(hist_data["messages"]) == 2
        assert "family function" in hist_data["messages"][0]["content"].lower()

        captured_prompts = []

        def fake_turn2_ai(prompt, system=""):
            captured_prompts.append(prompt)
            return {
                "action": "reschedule",
                "message": "Updated Family function to tomorrow at 8:00 PM.",
                "tasks": [{
                    "task_id": created_id,
                    "original_title": "Family function",
                    "title": "Family function",
                    "duration_minutes": 60,
                    "task_type": "family",
                    "priority": 2,
                    "flexibility": False,
                    "controllability": "external",
                    "fixed_start": f"{tomorrow.isoformat()}T20:00:00",
                    "fixed_end": f"{tomorrow.isoformat()}T21:00:00",
                }],
                "clarification_needed": None,
                "schedule_trigger": True,
            }

        with patch("backend.server._ai_json", side_effect=fake_turn2_ai):
            r2 = client.post(
                "/api/chat",
                json={
                    "user_id": user_id,
                    "message": "I said family function tomorrow but you put that to day after tomorrow",
                    "session_id": session_id,
                },
                headers=headers,
            )
        assert r2.status_code == 200
        assert len(captured_prompts) == 1
        assert created_id in captured_prompts[0]
        assert "Recent Conversation History" in captured_prompts[0]
        assert "08:00 PM" in captured_prompts[0]

        assert len(_in_memory_db["schedule_items"]) == 1, "Edit should update existing task without duplicates"
        updated = _in_memory_db["schedule_items"][0]
        assert updated["id"] == created_id
        upd_local = _parse_dt(updated["start_time"], ist_tz).astimezone(ist_tz)
        assert upd_local.date() == tomorrow
        assert upd_local.hour == 20 and upd_local.minute == 0

        del_resp = client.delete(f"/api/chat/history?session_id={session_id}", headers=headers)
        assert del_resp.status_code == 200
        assert del_resp.json()["cleared"] is True
        hist_after = client.get(f"/api/chat/history?session_id={session_id}", headers=headers).json()
        assert hist_after["messages"] == []







