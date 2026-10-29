"""
Pytest tests for scheduling engine defects (P1-P8 from CHRONA_AUDIT.md).

Run with: python -m pytest tests/test_scheduling_engine.py -v
"""
import pytest
from datetime import datetime, timezone, timedelta
import sys
sys.path.insert(0, "/home/neo/Codes/neuravex/backend")

from scheduling.edf import EDFScheduler, Task, TimeSlot, ScheduleResult
from scheduling.repair_engine import RepairEngine
from scheduling.conflict import detect_conflicts

from tests.fixtures.scheduling.test_fixtures import (
    P1_TASK_DUE_IN_3H,
    P2_NIGHT_PREFERENCE,
    P3_FIXED_COMMITMENT,
    P5_TIMEZONE_AWARE,
    P6_REPAIR_PAST_DEADLINE,
    P7_REPAIR_SPLIT_NO_TIMES,
    P8_URGENCY_FROM_SLOT,
    VALIDATOR_TESTS,
)


# ============================================================
# Helper functions
# ============================================================
def make_task(data: dict) -> Task:
    """Create Task from fixture dict."""
    deadline = datetime.fromisoformat(data["deadline"])
    # Ensure timezone-aware if deadline has timezone
    if deadline.tzinfo is None:
        deadline = deadline.replace(tzinfo=timezone.utc)
    
    return Task(
        id=data["id"],
        title=data["title"],
        duration_minutes=data["duration_minutes"],
        deadline=deadline,
        priority=data.get("priority", 3),
        flexibility=data.get("flexibility", True),
        task_type=data.get("task_type", "task")
    )


def make_slot(data: dict) -> TimeSlot:
    """Create TimeSlot from fixture dict."""
    start = datetime.fromisoformat(data["start"])
    end = datetime.fromisoformat(data["end"])
    if start.tzinfo is None:
        start = start.replace(tzinfo=timezone.utc)
    if end.tzinfo is None:
        end = end.replace(tzinfo=timezone.utc)
    return TimeSlot(start=start, end=end)


def parse_dt(s: str) -> datetime:
    """Parse ISO string to timezone-aware datetime."""
    dt = datetime.fromisoformat(s)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


# ============================================================
# P1: Deadline respect - task due in 3h, slot 10-12h out
# ============================================================
class TestP1DeadlineRespect:
    """P1: Task due in 3h, only slot is 10-12h out → scheduled past deadline"""

    def test_task_past_deadline_marked_impossible(self):
        """A task that cannot fit before its deadline must be marked impossible, never scheduled late."""
        tasks = [make_task(t) for t in P1_TASK_DUE_IN_3H["tasks"]]
        slots = [make_slot(s) for s in P1_TASK_DUE_IN_3H["free_slots"]]
        weights = P1_TASK_DUE_IN_3H["behavior_weights"]

        scheduler = EDFScheduler()
        result = scheduler.schedule(tasks, slots, weights)

        # Should NOT schedule the task (slot is past deadline)
        assert len(result.scheduled) == P1_TASK_DUE_IN_3H["expected"]["scheduled_count"], \
            f"Expected 0 scheduled, got {len(result.scheduled)}: {[a.task.title for a in result.scheduled]}"

        # Should be marked impossible (urgency > 1.0 at current time, but more importantly: no valid slot before deadline)
        # The current code checks urgency_score() against now(), not against slot end time
        # This is the defect - it should check if slot.end <= deadline
        assert len(result.impossible) == P1_TASK_DUE_IN_3H["expected"]["impossible_count"], \
            f"Expected 1 impossible, got {len(result.impossible)}"


# ============================================================
# P2: Behavior weights must not make invalid slot valid
# ============================================================
class TestP2BehaviorWeights:
    """P2: Night preference pushes task to 21:00 instead of 09:00"""

    def test_behavior_weights_rank_only_valid_slots(self):
        """Behavior weights should only rank AMONG valid slots, never make an invalid slot valid."""
        tasks = [make_task(t) for t in P2_NIGHT_PREFERENCE["tasks"]]
        slots = [make_slot(s) for s in P2_NIGHT_PREFERENCE["free_slots"]]
        weights = P2_NIGHT_PREFERENCE["behavior_weights"]

        scheduler = EDFScheduler()
        result = scheduler.schedule(tasks, slots, weights)

        assert len(result.scheduled) == 1
        assigned = result.scheduled[0]

        # Should pick the EARLIER valid slot (09:00), not the night slot (21:00)
        # Even though night has higher weight (1.0 vs 0.2), both slots are valid
        # The bug: current code sorts ALL slots by weight first, then tries to fit
        # This means night slot (weight 1.0) is tried first and task fits there
        expected_start = parse_dt(P2_NIGHT_PREFERENCE["expected"]["assigned_start"])
        assert assigned.assigned_start == expected_start, \
            f"Expected {expected_start}, got {assigned.assigned_start} - behavior weight incorrectly prioritized invalid slot"


# ============================================================
# P3: Fixed commitments and explicit times
# ============================================================
class TestP3FixedCommitments:
    """P3: Fixed commitments / explicit requested times not representable"""

    def test_task_model_has_fixed_start_end(self):
        """Task should support fixed_start and fixed_end fields."""
        # Current Task model doesn't have these fields
        # This test documents the requirement
        task_data = P3_FIXED_COMMITMENT["tasks"][0]
        deadline = parse_dt(task_data["deadline"])
        
        # This will fail until Task model is extended
        task = Task(
            id=task_data["id"],
            title=task_data["title"],
            duration_minutes=task_data["duration_minutes"],
            deadline=deadline,
            priority=task_data["priority"],
            flexibility=task_data["flexibility"],
            task_type=task_data["task_type"]
        )
        
        # These fields don't exist yet - test documents the requirement
        assert hasattr(task, 'fixed_start'), "Task needs fixed_start field"
        assert hasattr(task, 'fixed_end'), "Task needs fixed_end field"

    def test_fixed_items_subtracted_from_free_time(self):
        """Fixed items must be subtracted from free time before scheduling flexible tasks."""
        # This is an integration test - requires the scheduler to understand fixed items
        # Current EDFScheduler doesn't accept fixed_items parameter
        pytest.skip("Requires EDFScheduler extension to accept fixed_items")


# ============================================================
# P5: Timezone-aware datetimes
# ============================================================
class TestP5Timezone:
    """P5: Timezone crash - naive/aware datetime mix"""

    def test_timezone_aware_deadline_no_crash(self):
        """Scheduler must handle timezone-aware deadlines without crashing."""
        tasks = [make_task(t) for t in P5_TIMEZONE_AWARE["tasks"]]
        slots = [make_slot(s) for s in P5_TIMEZONE_AWARE["free_slots"]]
        weights = P5_TIMEZONE_AWARE["behavior_weights"]

        scheduler = EDFScheduler()
        
        # This currently crashes with "can't subtract offset-naive and offset-aware datetimes"
        # because urgency_score() uses datetime.now() (naive) vs deadline (aware)
        result = scheduler.schedule(tasks, slots, weights)
        
        assert P5_TIMEZONE_AWARE["expected"]["no_crash"]
        assert len(result.scheduled) == P5_TIMEZONE_AWARE["expected"]["scheduled_count"]


# ============================================================
# P6: Repair proposals validated against deadline
# ============================================================
class TestP6RepairDeadline:
    """P6: Repair for task due in 2h with slot 5h out"""

    def test_repair_does_not_propose_past_deadline(self):
        """Repair proposals must be validated against the task deadline."""
        task_data = P6_REPAIR_PAST_DEADLINE["disrupted_task"]
        free_slots = P6_REPAIR_PAST_DEADLINE["free_slots"]

        engine = RepairEngine()
        proposals = engine.propose_repairs(task_data, free_slots)

        # Should NOT have a valid move_today proposal (slot is past deadline)
        move_proposals = [p for p in proposals if p["strategy"] == "move_today"]
        assert len(move_proposals) == 0, \
            f"Should not propose move_today past deadline, got: {move_proposals}"

        # Should have defer_impossible
        defer_impossible = [p for p in proposals if p["strategy"] == "defer_impossible"]
        assert len(defer_impossible) == 1, \
            f"Expected defer_impossible proposal, got: {proposals}"


# ============================================================
# P7: Repair split returns actual slots
# ============================================================
class TestP7RepairSplit:
    """P7: Repair split returns block sizes only, no start times"""

    def test_split_proposal_has_actual_times(self):
        """Split repair must return concrete start/end times for each block."""
        task_data = P7_REPAIR_SPLIT_NO_TIMES["disrupted_task"]
        free_slots = P7_REPAIR_SPLIT_NO_TIMES["free_slots"]

        engine = RepairEngine()
        proposals = engine.propose_repairs(task_data, free_slots)

        split_proposals = [p for p in proposals if p["strategy"] == "split"]
        assert len(split_proposals) == 1, "Should have split proposal"

        split = split_proposals[0]
        
        # Current code only returns block_duration and blocks count
        # Should return actual slot assignments
        assert "block_assignments" in split, \
            "Split proposal must include block_assignments with start/end times"
        
        assignments = split["block_assignments"]
        assert len(assignments) == 2, "Should have 2 block assignments"
        
        for assignment in assignments:
            assert "start" in assignment and "end" in assignment, \
                "Each block must have start and end times"
            
            # Verify each block fits in a free slot
            block_start = parse_dt(assignment["start"])
            block_end = parse_dt(assignment["end"])
            fits = any(
                parse_dt(s["start"]) <= block_start and block_end <= parse_dt(s["end"])
                for s in free_slots
            )
            assert fits, f"Block {block_start}-{block_end} doesn't fit in any free slot"


# ============================================================
# P8: urgency_after computed from proposed slot, not now
# ============================================================
class TestP8UrgencyAfter:
    """P8: urgency_after computed from now, not proposed slot"""

    def test_urgency_after_reflects_proposed_time(self):
        """urgency_after should be calculated from the proposed slot time, not current time."""
        task_data = P8_URGENCY_FROM_SLOT["disrupted_task"]
        free_slots = P8_URGENCY_FROM_SLOT["free_slots"]

        engine = RepairEngine()
        proposals = engine.propose_repairs(task_data, free_slots)

        move_proposals = [p for p in proposals if p["strategy"] == "move_today"]
        assert len(move_proposals) == 1
        
        proposal = move_proposals[0]
        
        # Current code uses task.urgency_score() which computes from datetime.now()
        # Should compute from proposal's new_start time
        proposed_start = parse_dt(proposal["new_start"])
        proposed_end = parse_dt(proposal["new_end"])
        deadline = parse_dt(task_data["deadline"])
        
        # Correct urgency: duration / minutes from proposed_end to deadline
        remaining_minutes = (deadline - proposed_end).total_seconds() / 60
        correct_urgency = task_data["duration_min"] / max(remaining_minutes, 1)
        
        # Allow small floating point difference
        assert abs(proposal["urgency_after"] - correct_urgency) < 0.05, \
            f"urgency_after {proposal['urgency_after']} should reflect proposed time ({correct_urgency:.2f}), not current time"


# ============================================================
# Independent Validator
# ============================================================
class TestValidator:
    """Independent validator that every plan and repair must pass."""

    def test_validator_exists(self):
        """A validate_plan function must exist and be used by scheduler and repair engine."""
        # This test documents the requirement - will pass when validator is implemented
        from scheduling.validator import validate_plan
        assert callable(validate_plan)

    @pytest.mark.parametrize("test_case", VALIDATOR_TESTS)
    def test_validator_cases(self, test_case):
        """Run validator against known scenarios."""
        from scheduling.validator import validate_plan
        
        plan = test_case["plan"]
        fixed_items = test_case["fixed_items"]
        windows = test_case["windows"]
        
        # Convert string times to datetime
        plan_dt = []
        for item in plan:
            item_copy = item.copy()
            item_copy["start"] = parse_dt(item["start"])
            item_copy["end"] = parse_dt(item["end"])
            if "deadline" in item_copy:
                item_copy["deadline"] = parse_dt(item_copy["deadline"])
            plan_dt.append(item_copy)
        
        fixed_dt = []
        for item in fixed_items:
            fixed_dt.append({
                "start": parse_dt(item["start"]),
                "end": parse_dt(item["end"])
            })
        
        windows_dt = []
        for w in windows:
            windows_dt.append({
                "start": parse_dt(w["start"]),
                "end": parse_dt(w["end"])
            })
        
        result = validate_plan(plan_dt, fixed_dt, windows_dt)
        assert result["valid"] == test_case["expected_valid"], \
            f"{test_case['name']}: expected valid={test_case['expected_valid']}, got {result}"


# ============================================================
# Additional: EDF algorithm documentation
# ============================================================
class TestEDFAlgorithmDocs:
    """Verify algorithm is described as heuristic, not optimal."""

    def test_docstring_not_claiming_optimal(self):
        """EDFScheduler docstring should not claim Liu & Layland optimality for this variant."""
        import inspect
        doc = inspect.getdoc(EDFScheduler)
        # The audit found: "edf.py cites Liu & Layland optimality. That result is for preemptive
        # independent jobs; this non-preemptive, slot-constrained version is a greedy heuristic."
        # Updated docstring should say "greedy EDF-based heuristic"
        assert "heuristic" in doc.lower(), "Docstring should describe algorithm as heuristic"
        assert "greedy" in doc.lower(), "Docstring should mention greedy approach"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])