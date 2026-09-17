-- Function to move expired events to drafts table
CREATE OR REPLACE FUNCTION archive_expired_events()
RETURNS TRIGGER AS $$
BEGIN
    -- Move the event to drafts table
    INSERT INTO drafts (
        title,
        category,
        venue,
        event_datetime,
        severity_level,
        complexity_score,
        estimated_prep_hours,
        status,
        original_event_id,
        notes
    )
    VALUES (
        OLD.title,
        OLD.category,
        OLD.venue,
        OLD.event_datetime,
        OLD.severity_level,
        OLD.complexity_score,
        OLD.estimated_prep_hours,
        'expired',
        OLD.id,
        'Automatically archived - event expired'
    );

    -- Return NULL to delete the original record
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically archive expired events
CREATE OR REPLACE TRIGGER trigger_archive_expired_events
    BEFORE DELETE ON events
    FOR EACH ROW
    EXECUTE FUNCTION archive_expired_events();