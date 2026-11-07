-- ============================================================
-- CHRONA SCHEMA MIGRATION v2.0
-- ============================================================
-- Run in Supabase SQL Editor (Dashboard > SQL > New Query)
-- Safe to run multiple times (all use IF NOT EXISTS).
-- This EXTENDS v1 schema with new tables needed for Chrona v2.
-- ============================================================

-- ============================================================
-- 1. EXTEND schedule_items with more fields
-- ============================================================

ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS duration_minutes INT DEFAULT 60;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS recurrence TEXT;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS is_prep_block BOOLEAN DEFAULT FALSE;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS for_event_id UUID;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS session_number INT DEFAULT 1;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS total_sessions INT DEFAULT 1;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS completion_note TEXT;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS actual_start TIMESTAMPTZ;
ALTER TABLE schedule_items ADD COLUMN IF NOT EXISTS actual_end TIMESTAMPTZ;

-- Index for pending tasks (schedule generation)
CREATE INDEX IF NOT EXISTS idx_schedule_pending
    ON schedule_items (user_id, status, priority, deadline);

-- ============================================================
-- 2. EXTEND behavior_profiles
-- ============================================================

ALTER TABLE behavior_profiles ADD COLUMN IF NOT EXISTS wake_time TEXT DEFAULT '07:00';
ALTER TABLE behavior_profiles ADD COLUMN IF NOT EXISTS sleep_time TEXT DEFAULT '23:00';
ALTER TABLE behavior_profiles ADD COLUMN IF NOT EXISTS study_hours INT DEFAULT 4;
ALTER TABLE behavior_profiles ADD COLUMN IF NOT EXISTS timezone TEXT DEFAULT 'UTC';

-- ============================================================
-- 3. PROJECTS TABLE
-- ============================================================
-- Top-level projects that contain multiple schedule_items as subtasks.

CREATE TABLE IF NOT EXISTS projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    title       TEXT NOT NULL,
    description TEXT,
    deadline    TIMESTAMPTZ,
    priority    INT DEFAULT 3,
    status      TEXT DEFAULT 'active',  -- active, completed, cancelled
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_projects_user ON projects (user_id, status);

-- ============================================================
-- 4. USER CONTEXT TABLE (persistent memory)
-- ============================================================
-- Stores key facts about the user that Chrona should remember
-- across sessions (commitments, preferences, life context).

CREATE TABLE IF NOT EXISTS user_context (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    context_key TEXT NOT NULL,       -- e.g. "college_schedule", "exam_period", "goal_fitness"
    context_val JSONB NOT NULL,      -- the actual context data
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW(),
    expires_at  TIMESTAMPTZ,         -- optional expiry
    UNIQUE (user_id, context_key)
);

CREATE INDEX IF NOT EXISTS idx_user_context_user ON user_context (user_id);

-- ============================================================
-- 5. CHAT SESSIONS TABLE
-- ============================================================
-- Stores conversation history for the Chrona chat interface.

CREATE TABLE IF NOT EXISTS chat_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    session_id  TEXT NOT NULL,
    messages    JSONB DEFAULT '[]',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions (user_id);

-- ============================================================
-- 6. SCHEDULE VERSIONS TABLE
-- ============================================================
-- Tracks proposed schedules before acceptance.
-- Allows user to compare/revert schedule versions.

CREATE TABLE IF NOT EXISTS schedule_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    version_hash TEXT NOT NULL,
    proposed    JSONB NOT NULL,       -- the full proposed schedule
    accepted    BOOLEAN DEFAULT FALSE,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_schedule_versions_user ON schedule_versions (user_id, created_at DESC);

-- ============================================================
-- 7. FIX: Add missing columns to events table
-- ============================================================

ALTER TABLE events ADD COLUMN IF NOT EXISTS duration_minutes INT DEFAULT 60;
ALTER TABLE events ADD COLUMN IF NOT EXISTS priority INT DEFAULT 3;
ALTER TABLE events ADD COLUMN IF NOT EXISTS flexibility BOOLEAN DEFAULT TRUE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS task_type TEXT DEFAULT 'task';
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_fixed BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS fixed_start TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN IF NOT EXISTS fixed_end TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN IF NOT EXISTS deadline TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN IF NOT EXISTS recurrence TEXT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS prep_hours INT DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_project BOOLEAN DEFAULT FALSE;

-- ============================================================
-- 8. ENABLE REALTIME
-- ============================================================
-- Uncomment to enable live updates in CalendarView:
-- ALTER PUBLICATION supabase_realtime ADD TABLE schedule_items;
-- ALTER PUBLICATION supabase_realtime ADD TABLE projects;
-- ALTER PUBLICATION supabase_realtime ADD TABLE user_context;

-- ============================================================
-- 9. RLS POLICIES (enable when using Supabase Auth)
-- ============================================================
-- Uncomment these when you add proper authentication:
--
-- ALTER TABLE schedule_items ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "Users see own items" ON schedule_items
--     FOR ALL USING (auth.uid()::text = user_id::text);
--
-- ALTER TABLE behavior_profiles ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "Users see own profile" ON behavior_profiles
--     FOR ALL USING (auth.uid()::text = user_id::text);
--
-- ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "Users see own projects" ON projects
--     FOR ALL USING (auth.uid()::text = user_id::text);

-- ============================================================
-- DONE
-- ============================================================
SELECT 'Chrona v2 schema migration complete' AS status;

