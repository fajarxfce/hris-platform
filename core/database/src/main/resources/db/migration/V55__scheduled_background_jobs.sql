ALTER TABLE background_jobs ADD COLUMN scheduled_for timestamptz;
ALTER TABLE background_jobs ADD CONSTRAINT finite_job_schedule CHECK (
    isfinite(available_at) AND (scheduled_for IS NULL OR isfinite(scheduled_for))
);
ALTER TABLE background_jobs ADD CONSTRAINT job_schedule_floor CHECK (
    scheduled_for IS NULL OR cancellation_requested OR available_at >= scheduled_for
);

-- A cancelled future job becomes due once so its feature can release reservations.
-- Cleanup retries still use available_at; cancellation never bypasses retry backoff.
CREATE FUNCTION protect_job_schedule() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF OLD.scheduled_for IS DISTINCT FROM NEW.scheduled_for THEN
        RAISE EXCEPTION 'The requested job schedule is immutable' USING ERRCODE='42501';
    END IF;
    IF OLD.cancellation_requested AND NOT NEW.cancellation_requested THEN
        RAISE EXCEPTION 'Job cancellation cannot be reversed' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_job_schedule BEFORE UPDATE ON background_jobs
    FOR EACH ROW EXECUTE FUNCTION protect_job_schedule();
