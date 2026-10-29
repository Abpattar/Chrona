"""
Earliest Deadline First (EDF) Scheduler
========================================
Greedy EDF-based heuristic for non-preemptive, slot-constrained scheduling.
(Liu & Layland optimality applies to preemptive independent jobs; this variant is a heuristic.)

Usage:
    from scheduling.edf import EDFScheduler, Task, TimeSlot

    scheduler = EDFScheduler()
    result = scheduler.schedule(tasks, free_slots, behavior_weights, fixed_items)

    result.scheduled  → list of Assignment objects
    result.impossible → tasks that cannot fit before deadline
    result.deferred   → feasible tasks with no valid slot found

Vercel Equivalent: N/A (scheduling runs only on Python backend)
"""
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Optional, List
import heapq


def _ensure_utc(dt: datetime) -> datetime:
    """Ensure datetime is timezone-aware in UTC."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _now_utc() -> datetime:
    """Current time in UTC."""
    return datetime.now(timezone.utc)


@dataclass
class Task:
    id: str
    title: str
    duration_minutes: int
    deadline: datetime
    priority: int = 3          # 1=critical, 2=important, 3=normal, 4=optional
    flexibility: bool = True
    task_type: str = "task"
    event_id: Optional[str] = None
    # Fixed commitment fields (P3)
    fixed_start: Optional[datetime] = None
    fixed_end: Optional[datetime] = None

    def __post_init__(self):
        self.deadline = _ensure_utc(self.deadline)
        if self.fixed_start:
            self.fixed_start = _ensure_utc(self.fixed_start)
        if self.fixed_end:
            self.fixed_end = _ensure_utc(self.fixed_end)

    def urgency_score(self, from_time: Optional[datetime] = None) -> float:
        """
        Ratio of work needed to time available.
        > 1.0 means impossible to complete on time.
        
        Args:
            from_time: Calculate urgency from this time (for repair validation).
                       Defaults to now (for scheduling).
        """
        ref_time = _ensure_utc(from_time or _now_utc())
        remaining_minutes = max(
            (self.deadline - ref_time).total_seconds() / 60,
            1
        )
        return self.duration_minutes / remaining_minutes

    def is_feasible(self, from_time: Optional[datetime] = None) -> bool:
        return self.urgency_score(from_time) <= 1.0

    def is_fixed(self) -> bool:
        """True if this task has a fixed start/end time."""
        return self.fixed_start is not None and self.fixed_end is not None

    def __lt__(self, other):
        # Heap ordering: earliest deadline first, then highest priority
        if self.deadline == other.deadline:
            return self.priority < other.priority
        return self.deadline < other.deadline


@dataclass
class TimeSlot:
    start: datetime
    end: datetime
    assigned_tasks: list = field(default_factory=list)

    def __post_init__(self):
        self.start = _ensure_utc(self.start)
        self.end = _ensure_utc(self.end)

    def remaining_minutes(self) -> int:
        used = sum(t.duration_minutes for t in self.assigned_tasks)
        total = int((self.end - self.start).total_seconds() / 60)
        return total - used

    def can_fit(self, task: Task) -> bool:
        return self.remaining_minutes() >= task.duration_minutes

    def consume(self, task: Task) -> datetime:
        """Returns the start_time assigned to this task within the slot."""
        used = sum(t.duration_minutes for t in self.assigned_tasks)
        task_start = self.start + timedelta(minutes=used)
        self.assigned_tasks.append(task)
        return task_start


@dataclass
class Assignment:
    task: Task
    slot: TimeSlot
    assigned_start: datetime
    assigned_end: datetime


@dataclass
class ScheduleResult:
    scheduled: list      # list[Assignment]
    impossible: list     # list[Task] — cannot fit before deadline
    deferred: list       # list[Task] — feasible but no valid slot found


def _subtract_fixed_items(free_slots: List[TimeSlot], fixed_items: List[Task]) -> List[TimeSlot]:
    """
    Subtract fixed commitments from free slots.
    Returns new list of TimeSlots with fixed items removed.
    """
    if not fixed_items:
        return free_slots
    
    # Sort fixed items by start time
    fixed_sorted = sorted(fixed_items, key=lambda f: f.fixed_start)
    
    result = []
    for slot in free_slots:
        current_start = slot.start
        
        for fixed in fixed_sorted:
            fixed_start = fixed.fixed_start
            fixed_end = fixed.fixed_end
            
            # Skip fixed items that don't overlap this slot
            if fixed_end <= current_start:
                continue
            if fixed_start >= slot.end:
                break
            
            # Fixed item overlaps with current free time
            if fixed_start > current_start:
                # Free time before fixed item
                result.append(TimeSlot(start=current_start, end=min(fixed_start, slot.end)))
            
            # Move past fixed item
            current_start = max(current_start, fixed_end)
            
            if current_start >= slot.end:
                break
        
        # Remaining free time after last fixed item
        if current_start < slot.end:
            result.append(TimeSlot(start=current_start, end=slot.end))
    
    return result


class EDFScheduler:
    """
    EDF Scheduler with behavior-weighted slot preference.
    Greedy EDF-based heuristic for non-preemptive, slot-constrained scheduling.
    (Liu & Layland optimality applies to preemptive independent jobs; this variant is a heuristic.)

    Algorithm:
    1. Separate impossible tasks (urgency > 1.0 from now) — flagged immediately
    2. Sort remaining tasks by deadline (earliest first), priority as tiebreaker
    3. Subtract fixed items from free slots to get available time
    4. Filter available slots: only keep slots where slot.end <= task.deadline
    5. Sort valid slots by behavior weight (prefer slots user historically completes in)
    6. Greedily assign tasks to best-fitting valid slots
    7. Tasks that don't fit go to deferred list

    Complexity: O(n * m) where n=tasks, m=slots. Fine for < 1000 tasks.
    """

    MAX_REPAIR_ITERATIONS = 10

    def schedule(
        self,
        tasks: list,
        free_slots: list,
        behavior_weights: dict = None,
        fixed_items: list = None
    ) -> ScheduleResult:
        weights = behavior_weights or {
            "morning": 0.5, "afternoon": 0.5,
            "evening": 0.5, "night": 0.5
        }

        # Separate fixed and flexible tasks
        fixed_tasks = [t for t in tasks if t.is_fixed()]
        flexible_tasks = [t for t in tasks if not t.is_fixed()]

        # Subtract fixed items from free slots
        available_slots = _subtract_fixed_items(free_slots, fixed_tasks)

        # Check feasibility from NOW for flexible tasks
        impossible = [t for t in flexible_tasks if not t.is_feasible()]
        feasible = [t for t in flexible_tasks if t.is_feasible()]

        heap = list(feasible)
        heapq.heapify(heap)

        scheduled = []
        deferred = []

        while heap:
            task = heapq.heappop(heap)
            placed = False

            # Filter slots: only those where task can fit AND slot.end <= task.deadline
            valid_slots = [
                s for s in available_slots
                if s.can_fit(task) and s.end <= task.deadline
            ]

            # Sort valid slots by behavior weight
            sorted_slots = sorted(
                valid_slots,
                key=lambda s: -weights.get(self._time_to_slot(s.start), 0.5)
            )

            for slot in sorted_slots:
                if slot.can_fit(task):
                    task_start = slot.consume(task)
                    task_end = task_start + timedelta(minutes=task.duration_minutes)
                    # Double-check: task_end must not exceed deadline (redundant with slot filter)
                    if task_end <= task.deadline:
                        scheduled.append(Assignment(task, slot, task_start, task_end))
                        placed = True
                        break
            
            if not placed:
                deferred.append(task)

        # Add fixed tasks to scheduled (they're already at their fixed times)
        for fixed in fixed_tasks:
            scheduled.append(Assignment(
                task=fixed,
                slot=TimeSlot(start=fixed.fixed_start, end=fixed.fixed_end),
                assigned_start=fixed.fixed_start,
                assigned_end=fixed.fixed_end
            ))

        return ScheduleResult(
            scheduled=scheduled,
            impossible=impossible,
            deferred=deferred
        )

    def _time_to_slot(self, dt: datetime) -> str:
        dt = _ensure_utc(dt)
        h = dt.hour
        if 5 <= h < 12: return "morning"
        if 12 <= h < 17: return "afternoon"
        if 17 <= h < 21: return "evening"
        return "night"


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    from datetime import datetime, timedelta, timezone

    now = _now_utc()
    tasks = [
        Task(id="1", title="Study OS Chapter 1", duration_minutes=60,
             deadline=now + timedelta(days=2), priority=2, task_type="study_block"),
        Task(id="2", title="Study OS Chapter 2", duration_minutes=45,
             deadline=now + timedelta(days=2), priority=2, task_type="study_block"),
        Task(id="3", title="Gym Session", duration_minutes=60,
             deadline=now + timedelta(days=1), priority=4, task_type="exercise"),
        Task(id="4", title="Impossible Task", duration_minutes=480,
             deadline=now + timedelta(minutes=30), priority=1, task_type="task"),
    ]

    slots = [
        TimeSlot(start=now + timedelta(hours=1), end=now + timedelta(hours=3)),
        TimeSlot(start=now + timedelta(hours=4), end=now + timedelta(hours=6)),
    ]

    scheduler = EDFScheduler()
    result = scheduler.schedule(tasks, slots)

    print(f"Scheduled: {len(result.scheduled)}")
    for a in result.scheduled:
        print(f"  {a.task.title} -> {a.assigned_start.strftime('%H:%M')} - {a.assigned_end.strftime('%H:%M')}")
    print(f"Impossible: {len(result.impossible)}")
    for t in result.impossible:
        print(f"  {t.title} (urgency: {t.urgency_score():.2f})")
    print(f"Deferred: {len(result.deferred)}")
    for t in result.deferred:
        print(f"  {t.title}")
    print("EDF Scheduler: OK")