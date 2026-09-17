-- Create drafts table for expired/incomplete events
CREATE TABLE IF NOT EXISTS drafts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT,
    venue TEXT,
    event_datetime TIMESTAMPTZ,
    severity_level TEXT DEFAULT 'medium',
    complexity_score INTEGER DEFAULT 5,
    estimated_prep_hours INTEGER DEFAULT 2,
    status TEXT DEFAULT 'pending',
    original_event_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    archived_at TIMESTAMPTZ DEFAULT NOW(),
    notes TEXT
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_drafts_category ON drafts(category);
CREATE INDEX IF NOT EXISTS idx_drafts_severity ON drafts(severity_level);
CREATE INDEX IF NOT EXISTS idx_drafts_status ON drafts(status);
CREATE INDEX IF NOT EXISTS idx_drafts_archived_at ON drafts(archived_at);