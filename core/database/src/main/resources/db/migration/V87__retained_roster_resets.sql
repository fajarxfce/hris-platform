SET LOCAL lock_timeout = '5s';
LOCK TABLE roster_days, roster_revisions IN SHARE ROW EXCLUSIVE MODE;

-- A reset removes the override, not its identity or history. Null shift on an
-- active row continues to mean an explicitly scheduled day off.
ALTER TABLE roster_days ADD COLUMN active boolean NOT NULL DEFAULT true;
ALTER TABLE roster_revisions ADD COLUMN active boolean NOT NULL DEFAULT true;
ALTER TABLE roster_days ADD CONSTRAINT roster_reset_has_no_shift
    CHECK(active OR shift IS NULL);
ALTER TABLE roster_revisions ADD CONSTRAINT roster_revision_reset_has_no_shift
    CHECK(active OR shift IS NULL);

CREATE OR REPLACE FUNCTION capture_roster_day_sync() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    VALUES(NEW.company_id,'ROSTER_DAYS',NEW.id,NEW.employment_id,NEW.version,
        CASE WHEN NEW.active THEN 'UPSERT' ELSE 'DELETE' END);
    RETURN NEW;
END $$;
