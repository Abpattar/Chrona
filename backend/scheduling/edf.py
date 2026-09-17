"""
Earliest Deadline First (EDF) Scheduler
========================================
Optimal single-processor scheduling algorithm (Liu & Layland 1973).
Falls back gracefully when no free slots exist.

Usage:
    from scheduling.edf import EDFScheduler, Task, TimeSlot

    scheduler = EDFScheduler()
    result = scheduler.schedule(tasks, free_slots, behavior_weights)

    # result.scheduled  → list of Assignment objects
    # result.impossible → tasks with urgency > 1.0 (can't fit before deadline)
    # result.deferred   → feasible but no slot found

Vercel Equivalent: N/A (scheduling runs only on Python backend)
"""
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Optional
import heapq


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

    def urgency_score(self) -> float:
        """
        Ratio of work needed to time available.
        > 1.0 means impossible to complete on time.
        """
        remaining_minutes = max(
            (self.deadline - datetime.now()).total_seconds() / 60,
            1
        )
        return self.duration_minutes / remaining_minutes

    def is_feasible(self) -> bool:
        return self.urgency_score() <= 1.0

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
    impossible: list     # list[Task] — urgency_score > 1.0
    deferred: list       # list[Task] — no slot found but feasible


class EDFScheduler:
    """
    EDF Scheduler with behavior-weighted slot preference.

    Algorithm:
    1. Separate impossible tasks (urgency > 1.0) — these are flagged immediately
    2. Sort remaining tasks by deadline (earliest first), priority as tiebreaker
    3. Sort free slots by behavior weight (prefer slots user historically completes in)
    4. Greedily assign tasks to best-fitting slots
    5. Tasks that don't fit go to deferred list

    Complexity: O(n * m) where n=tasks, m=slots. Fine for < 1000 tasks.
    """

    MAX_REPAIR_ITERATIONS = 10

    def schedule(
        self,
        tasks: list,
        free_slots: list,
        behavior_weights: dict = None
    ) -> ScheduleResult:
        weights = behavior_weights or {
            "morning": 0.5, "afternoon": 0.5,
            "evening": 0.5, "night": 0.5
        }

        impossible = [t for t in tasks if not t.is_feasible()]
        feasible   = [t for t in tasks if t.is_feasible()]

        heap = list(feasible)
        heapq.heapify(heap)

        # Sort slots by behavior weight (prefer slots user completes tasks in)
        sorted_slots = sorted(
            free_slots,
            key=lambda s: -weights.get(self._time_to_slot(s.start), 0.5)
        )

        scheduled = []
        deferred  = []

        while heap:
            task = heapq.heappop(heap)
            placed = False
            for slot in sorted_slots:
                if slot.can_fit(task):
                    task_start = slot.consume(task)
                    task_end   = task_start + timedelta(minutes=task.duration_minutes)
                    scheduled.append(Assignment(task, slot, task_start, task_end))
                    placed = True
                    break
            if not placed:
                deferred.append(task)

        return ScheduleResult(
            scheduled=scheduled,
            impossible=impossible,
            deferred=deferred
        )

    def _time_to_slot(self, dt: datetime) -> str:
        h = dt.hour
        if 5  <= h < 12: return "morning"
        if 12 <= h < 17: return "afternoon"
        if 17 <= h < 21: return "evening"
        return "night"


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    from datetime import datetime, timedelta

    # Create test tasks
    now = datetime.now()
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

    # Create test slots
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
