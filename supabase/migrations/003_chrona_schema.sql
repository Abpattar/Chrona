-- ============================================================
-- CHRONA SCHEMA MIGRATION v1.0
-- ============================================================
-- Run this in Supabase SQL Editor (Dashboard > SQL > New Query)
-- This migration EXTENDS the existing Neuravex schema.
-- It does NOT drop any existing tables.
-- Safe to run multiple times (all statements use IF NOT EXISTS / IF EXISTS).
-- ============================================================

-- ============================================================
-- 1. EXTEND EXISTING EVENTS TABLE
-- ============================================================
-- Add new columns needed by Chrona for user scoping, dedup, and
-- richer metadata. These use ADD COLUMN IF NOT EXISTS so they
-- are safe to re-run.

ALTER TABLE events ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE events ADD COLUMN IF NOT EXISTS source_hash TEXT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS key_topics TEXT[];
ALTER TABLE events ADD COLUMN IF NOT EXISTS action_items JSONB;

-- Add unique constraint on source_hash for deduplication
-- (wrapped in DO block to be idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'events_source_hash_unique'
    ) THEN
        ALTER TABLE events ADD CONSTRAINT events_source_hash_unique UNIQUE (source_hash);
    END IF;
END $$;

-- Index for user-scoped queries
CREATE INDEX IF NOT EXISTS idx_events_user_id ON events(user_id);

-- ============================================================
-- 2. BEHAVIOR PROFILES
-- ============================================================
-- Stores per-user behavioral weights (EMA-updated).
-- Synced from Redis every 10 updates.
-- Cold start: initialized from archetype on onboarding.

CREATE TABLE IF NOT EXISTS behavior_profiles (
    user_id         UUID PRIMARY KEY,
    slot_weights    JSONB DEFAULT '{"morning":0.5,"afternoon":0.5,"evening":0.5,"night":0.5}',
    type_weights    JSONB DEFAULT '{}',
    avg_delay_min   FLOAT DEFAULT 0.0,
    sample_count    INT DEFAULT 0,
    archetype       TEXT DEFAULT 'student_balanced',
    last_updated    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 3. SCHEDULE ITEMS
-- ============================================================
-- Auto-generated task blocks from events.
-- Each event can spawn multiple schedule_items (study blocks, prep blocks).
-- EDF scheduler writes to this table.

CREATE TABLE IF NOT EXISTS schedule_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    event_id        UUID REFERENCES events(id) ON DELETE CASCADE,
    title           TEXT NOT NULL,
    task_type       TEXT DEFAULT 'task',
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    deadline        TIMESTAMPTZ,
    priority        INT DEFAULT 3,
    flexibility     BOOLEAN DEFAULT TRUE,
    status          TEXT DEFAULT 'scheduled',
    actual_start    TIMESTAMPTZ,
    actual_end      TIMESTAMPTZ,
    completion_note TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT no_zero_duration CHECK (end_time > start_time)
);

-- Performance index for conflict detection (overlap query)
CREATE INDEX IF NOT EXISTS idx_schedule_overlap
    ON schedule_items (user_id, start_time, end_time);

-- Index for user + status filtering
CREATE INDEX IF NOT EXISTS idx_schedule_user_status
    ON schedule_items (user_id, status);

-- ============================================================
-- 4. BEHAVIOR EVENTS (append-only log)
-- ============================================================
-- Every task completion, skip, delay, or reschedule is logged here.
-- This is the training data source for the ML predictor (Phase 2).
-- NEVER update rows — only INSERT.

CREATE TABLE IF NOT EXISTS behavior_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    task_id         UUID REFERENCES schedule_items(id),
    event_type      TEXT NOT NULL,
    scheduled_start TIMESTAMPTZ,
    scheduled_end   TIMESTAMPTZ,
    actual_complete TIMESTAMPTZ,
    confirmed_at    TIMESTAMPTZ,
    metadata        JSONB DEFAULT '{}',
    logged_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_behavior_events_user
    ON behavior_events (user_id, logged_at);

-- ============================================================
-- 5. REPAIR PROPOSALS LOG
-- ============================================================
-- Stores proposed schedule repairs and whether they were accepted.
-- Used for repair quality tracking and future ML improvement.

CREATE TABLE IF NOT EXISTS repair_proposals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    conflict_hash   TEXT,
    proposals       JSONB NOT NULL,
    accepted        BOOLEAN,
    accepted_idx    INT,
    agent_reasoning TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 6. ONBOARDING RESPONSES
-- ============================================================
-- Cold start data: captures user preferences during onboarding.
-- Used to initialize behavior_profiles with the right archetype.

CREATE TABLE IF NOT EXISTS onboarding_responses (
    user_id         UUID PRIMARY KEY,
    productive_time TEXT,
    work_type       TEXT,
    task_preference TEXT,
    study_hours     INT DEFAULT 4,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 7. ENABLE REALTIME (for schedule_items live updates)
-- ============================================================
-- Uncomment the following if you want Supabase Realtime
-- subscriptions on schedule_items (required for CalendarView):
--
-- ALTER PUBLICATION supabase_realtime ADD TABLE schedule_items;
-- ALTER PUBLICATION supabase_realtime ADD TABLE behavior_profiles;

-- ============================================================
-- DONE
-- ============================================================
-- After running this migration:
-- 1. Verify tables exist: SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
-- 2. Enable RLS policies as needed for user_id scoping
-- 3. Enable Realtime on schedule_items if using CalendarView
