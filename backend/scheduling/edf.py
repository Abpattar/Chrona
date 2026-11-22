"""
Earliest Deadline First (EDF) Scheduler
========================================
Greedy EDF-based heuristic for non-preemptive, slot-constrained scheduling.
Fixes partial-day deadline placement: checks (slot_start + used + duration <= task.deadline)
instead of requiring the entire multi-hour free window to end before deadline.
"""
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Optional, List
import heapq


def _ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class Task:
    id: str
    title: str
    duration_minutes: int
    deadline: datetime
    priority: int = 3
    flexibility: bool = True
    task_type: str = "task"
    event_id: Optional[str] = None
    fixed_start: Optional[datetime] = None
    fixed_end: Optional[datetime] = None
    movability: str = "fluid"       # immovable | negotiable | fluid
    effort_level: str = "medium"    # low | medium | high
    location: str = "home"

    def __post_init__(self):
        self.duration_minutes = int(self.duration_minutes or 60)
        self.deadline = _ensure_utc(self.deadline)
        if self.fixed_start:
            self.fixed_start = _ensure_utc(self.fixed_start)
        if self.fixed_end:
            self.fixed_end = _ensure_utc(self.fixed_end)

    def urgency_score(self, from_time: Optional[datetime] = None) -> float:
        ref_time = _ensure_utc(from_time or _now_utc())
        remaining_minutes = max((self.deadline - ref_time).total_seconds() / 60, 1)
        return self.duration_minutes / remaining_minutes

    def is_feasible(self, from_time: Optional[datetime] = None) -> bool:
        return self.urgency_score(from_time) <= 1.0

    def is_fixed(self) -> bool:
        return self.fixed_start is not None and self.fixed_end is not None

    def __lt__(self, other):
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

    def used_minutes(self) -> int:
        return sum(t.duration_minutes for t in self.assigned_tasks)

    def remaining_minutes(self) -> int:
        total = int((self.end - self.start).total_seconds() / 60)
        return total - self.used_minutes()

    def next_available_start(self) -> datetime:
        return self.start + timedelta(minutes=self.used_minutes())

    def can_fit(self, task: Task) -> bool:
        return self.remaining_minutes() >= task.duration_minutes

    def can_finish_before_deadline(self, task: Task) -> bool:
        return (self.next_available_start() + timedelta(minutes=task.duration_minutes)) <= task.deadline

    def consume(self, task: Task) -> datetime:
        task_start = self.next_available_start()
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
    scheduled: list
    impossible: list
    deferred: list


def _subtract_fixed_items(free_slots: List[TimeSlot], fixed_items: List[Task]) -> List[TimeSlot]:
    if not fixed_items:
        return free_slots

    fixed_sorted = sorted(fixed_items, key=lambda f: f.fixed_start)
    result = []
    for slot in free_slots:
        current_start = slot.start
        for fixed in fixed_sorted:
            if fixed.fixed_end <= current_start:
                continue
            if fixed.fixed_start >= slot.end:
                break
            if fixed.fixed_start > current_start:
                result.append(TimeSlot(start=current_start, end=min(fixed.fixed_start, slot.end)))
            current_start = max(current_start, fixed.fixed_end)
            if current_start >= slot.end:
                break
        if current_start < slot.end:
            result.append(TimeSlot(start=current_start, end=slot.end))
    return result


class EDFScheduler:
    """Greedy EDF-based heuristic for non-preemptive, slot-constrained scheduling."""
    MAX_REPAIR_ITERATIONS = 10

    def schedule(
        self,
        tasks: list,
        free_slots: list,
        behavior_weights: dict = None,
        fixed_items: list = None,
        from_time: Optional[datetime] = None,
    ) -> ScheduleResult:
        weights = behavior_weights or {
            "morning": 0.5, "afternoon": 0.5,
            "evening": 0.5, "night": 0.5,
        }

        fixed_tasks = [t for t in tasks if t.is_fixed()]
        if fixed_items:
            for f in fixed_items:
                if f not in fixed_tasks:
                    fixed_tasks.append(f)
        flexible_tasks = [t for t in tasks if not t.is_fixed()]

        available_slots = _subtract_fixed_items(free_slots, fixed_tasks)

        ref_time = _ensure_utc(from_time or _now_utc())
        if available_slots:
            earliest_slot = min(s.start for s in available_slots)
            if earliest_slot < ref_time:
                ref_time = earliest_slot

        impossible = [t for t in flexible_tasks if not t.is_feasible(from_time=ref_time)]
        feasible = [t for t in flexible_tasks if t.is_feasible(from_time=ref_time)]

        heap = list(feasible)
        heapq.heapify(heap)

        scheduled = []
        deferred = []

        while heap:
            task = heapq.heappop(heap)
            placed = False

            valid_slots = [
                s for s in available_slots
                if s.can_fit(task) and s.can_finish_before_deadline(task)
            ]

            sorted_slots = sorted(
                valid_slots,
                key=lambda s: -weights.get(self._time_to_slot(s.next_available_start()), 0.5),
            )

            for slot in sorted_slots:
                if slot.can_fit(task) and slot.can_finish_before_deadline(task):
                    task_start = slot.consume(task)
                    task_end = task_start + timedelta(minutes=task.duration_minutes)
                    scheduled.append(Assignment(task, slot, task_start, task_end))
                    placed = True
                    break

            if not placed:
                deferred.append(task)

        for fixed in fixed_tasks:
            scheduled.append(
                Assignment(
                    task=fixed,
                    slot=TimeSlot(start=fixed.fixed_start, end=fixed.fixed_end),
                    assigned_start=fixed.fixed_start,
                    assigned_end=fixed.fixed_end,
                )
            )

        return ScheduleResult(scheduled=scheduled, impossible=impossible, deferred=deferred)

    def _time_to_slot(self, dt: datetime) -> str:
        dt = _ensure_utc(dt)
        h = dt.hour
        if 5 <= h < 12:
            return "morning"
        if 12 <= h < 17:
            return "afternoon"
        if 17 <= h < 21:
            return "evening"
        return "night"
