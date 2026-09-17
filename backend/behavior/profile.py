"""
Meta-Style Behavioral Learning System
=====================================
Inspired by Meta's real-time feedback weight update approach used in their
recommendation systems (similar to DLRM - Deep Learning Recommendation Model).

Key insight from Meta's architecture:
- Signals are cheap to collect (completion, skip, delay)
- Weights update in near-real-time using exponential moving averages
- No batch ML job needed for the base layer - statistical updates are sufficient
- A lightweight gradient-based layer (LightGBM) activates at 50+ samples

This mirrors Meta's two-tier approach:
  Tier 1: Fast, always-on statistical updates (EMA weights) - this file
  Tier 2: Slower, richer ML model trained on accumulated signals - predictor.py

The key behavioral signals we track (mirroring Meta's engagement signals):
  - POSITIVE: task_completed_on_time  -> increase slot weight
  - WEAK_POS: task_completed_late     -> slight increase, penalize time slot
  - NEGATIVE: task_skipped            -> decrease slot weight sharply
  - NEUTRAL:  task_rescheduled        -> no weight change, log for pattern analysis

Usage:
    from behavior.profile import BehaviorProfile, ARCHETYPES

    # Cold start from onboarding
    profile = BehaviorProfile.from_archetype(user_id, "student_morning")

    # Real-time update
    signal = profile.update(task_type, scheduled_start, scheduled_end, actual_complete, confirmed_at)

    # Get weights for scheduler
    weights = profile.get_scheduling_weights()

Vercel Equivalent: N/A (behavior learning runs only on Python backend)
"""

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Optional
import json

# ============================================================
# ARCHETYPES (cold start defaults)
# ============================================================

ARCHETYPES = {
    "student_morning":  {"morning": 0.75, "afternoon": 0.55, "evening": 0.40, "night": 0.25},
    "student_night":    {"morning": 0.25, "afternoon": 0.45, "evening": 0.65, "night": 0.75},
    "student_balanced": {"morning": 0.50, "afternoon": 0.55, "evening": 0.55, "night": 0.40},
    "developer":        {"morning": 0.45, "afternoon": 0.70, "evening": 0.60, "night": 0.35},
    "manager":          {"morning": 0.70, "afternoon": 0.50, "evening": 0.40, "night": 0.20},
    "freelancer":       {"morning": 0.40, "afternoon": 0.60, "evening": 0.55, "night": 0.50},
}

# ============================================================
# SIGNAL STRENGTHS
# ============================================================

# How much each outcome shifts the weight.
# Mirrors Meta's approach: negative signals have higher magnitude than positive.
SIGNAL_STRENGTH = {
    "completed_on_time": +0.12,   # strong positive
    "completed_late":    +0.04,   # weak positive - they did it, but timing was wrong
    "skipped":           -0.18,   # strong negative - stronger than positive (recency bias)
    "rescheduled":       +0.00,   # neutral - don't punish, don't reward
    "delayed_start":     -0.06,   # mild negative - started late but did it
}

GRACE_WINDOW_MINUTES = 15  # completion within 15min of end = "on time"
ALPHA = 0.1                # EMA learning rate - slow, stable (Meta uses ~0.01 in prod)


@dataclass
class BehaviorProfile:
    """
    Per-user behavioral profile with EMA-updated slot weights.

    Lifecycle:
    1. Created during onboarding with archetype defaults
    2. Updated on every task completion/skip/delay
    3. Cached in Redis (immediate reads)
    4. Synced to Supabase every 10 updates (durable storage)
    """

    user_id: str
    slot_weights: dict = field(default_factory=lambda: {
        "morning": 0.5, "afternoon": 0.5, "evening": 0.5, "night": 0.5
    })
    type_weights: dict = field(default_factory=dict)
    avg_delay_min: float = 0.0
    sample_count: int = 0
    archetype: str = "student_balanced"

    @classmethod
    def from_archetype(cls, user_id: str, archetype: str) -> "BehaviorProfile":
        """Create a new profile initialized from an archetype's default weights."""
        weights = ARCHETYPES.get(archetype, ARCHETYPES["student_balanced"])
        return cls(user_id=user_id, slot_weights=weights.copy(), archetype=archetype)

    @classmethod
    def from_db_row(cls, row: dict) -> "BehaviorProfile":
        """Reconstruct a profile from a Supabase row or Redis cache."""
        return cls(
            user_id=row["user_id"],
            slot_weights=row.get("slot_weights") or {"morning":0.5,"afternoon":0.5,"evening":0.5,"night":0.5},
            type_weights=row.get("type_weights") or {},
            avg_delay_min=row.get("avg_delay_min", 0.0),
            sample_count=row.get("sample_count", 0),
            archetype=row.get("archetype", "student_balanced")
        )

    def update(
        self,
        task_type: str,
        scheduled_start: datetime,
        scheduled_end: datetime,
        actual_complete: Optional[datetime],
        confirmed_at: Optional[datetime]
    ) -> str:
        """
        Meta-style real-time weight update.

        Signal determination:
        - No actual_complete -> skipped
        - Completed within grace window -> on_time
        - Completed after grace window -> completed_late

        Returns the signal type applied.
        """
        slot = self._time_to_slot(scheduled_start)

        # Determine signal
        if actual_complete is None:
            signal = "skipped"
        else:
            confirm_time = confirmed_at or actual_complete
            grace_end = scheduled_end + timedelta(minutes=GRACE_WINDOW_MINUTES)

            if confirm_time <= grace_end or actual_complete <= grace_end:
                signal = "completed_on_time"
            else:
                signal = "completed_late"
                # Update running average delay
                delay = (actual_complete - scheduled_end).total_seconds() / 60
                self.avg_delay_min = (
                    self.avg_delay_min * self.sample_count + delay
                ) / (self.sample_count + 1)

        # EMA update on slot weight (clamped to [0.1, 0.95])
        delta = SIGNAL_STRENGTH[signal]
        old_weight = self.slot_weights.get(slot, 0.5)
        new_weight = old_weight + ALPHA * delta
        self.slot_weights[slot] = round(max(0.1, min(0.95, new_weight)), 4)

        # Per-task-type weight update
        if task_type:
            old_type = self.type_weights.get(task_type, 0.5)
            self.type_weights[task_type] = round(
                max(0.1, min(0.95, old_type + ALPHA * SIGNAL_STRENGTH[signal])), 4
            )

        self.sample_count += 1
        return signal

    def get_scheduling_weights(self) -> dict:
        """Combined weights for the EDF scheduler."""
        return self.slot_weights.copy()

    def _time_to_slot(self, dt: datetime) -> str:
        h = dt.hour
        if 5  <= h < 12: return "morning"
        if 12 <= h < 17: return "afternoon"
        if 17 <= h < 21: return "evening"
        return "night"

    def to_db_dict(self) -> dict:
        """Serialize for Supabase upsert."""
        return {
            "user_id":       self.user_id,
            "slot_weights":  self.slot_weights,
            "type_weights":  self.type_weights,
            "avg_delay_min": self.avg_delay_min,
            "sample_count":  self.sample_count,
            "archetype":     self.archetype,
            "last_updated":  datetime.utcnow().isoformat()
        }


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    from datetime import datetime, timedelta

    # Test archetype creation
    profile = BehaviorProfile.from_archetype("user-001", "student_morning")
    assert profile.slot_weights["morning"] == 0.75
    print(f"Profile created: {profile.slot_weights}")

    # Test on-time completion
    now = datetime.now()
    signal = profile.update(
        task_type="study_block",
        scheduled_start=now.replace(hour=9),
        scheduled_end=now.replace(hour=10),
        actual_complete=now.replace(hour=9, minute=55),
        confirmed_at=now.replace(hour=9, minute=56)
    )
    assert signal == "completed_on_time"
    print(f"After on-time: morning={profile.slot_weights['morning']}, signal={signal}")

    # Test skip
    signal = profile.update(
        task_type="study_block",
        scheduled_start=now.replace(hour=14),
        scheduled_end=now.replace(hour=15),
        actual_complete=None,
        confirmed_at=None
    )
    assert signal == "skipped"
    print(f"After skip: afternoon={profile.slot_weights['afternoon']}, signal={signal}")

    # Test serialization
    db_dict = profile.to_db_dict()
    restored = BehaviorProfile.from_db_row(db_dict)
    assert restored.user_id == profile.user_id
    assert restored.sample_count == profile.sample_count
    print(f"Serialization roundtrip: OK")

    print("Behavior Profile: OK")
