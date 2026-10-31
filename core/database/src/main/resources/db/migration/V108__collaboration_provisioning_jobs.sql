CREATE TABLE collaboration_device_provisioning (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    job_id uuid NOT NULL UNIQUE REFERENCES background_jobs(id),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999999),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    PRIMARY KEY(company_id,device_id),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id)
);
ALTER TABLE collaboration_device_provisioning ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_device_provisioning FORCE ROW LEVEL SECURITY;
CREATE POLICY collaboration_provisioning_scope ON collaboration_device_provisioning
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE FUNCTION protect_collaboration_provisioning() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Collaboration provisioning fence is retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.created_at<>NEW.updated_at THEN
            RAISE EXCEPTION 'Invalid initial collaboration provisioning' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.device_id,NEW.account_id,NEW.created_at)
            IS DISTINCT FROM (OLD.company_id,OLD.device_id,OLD.account_id,OLD.created_at)
            OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at OR NEW.job_id=OLD.job_id
            OR NOT EXISTS(SELECT 1 FROM background_jobs WHERE id=OLD.job_id AND status IN ('FAILED','CANCELLED')) THEN
            RAISE EXCEPTION 'Invalid collaboration provisioning retry' USING ERRCODE='23514';
        END IF;
    END IF;
    -- The immutable job request and device binding identify the same account/company/parent.
    -- No parent FK is retained here: parent purging must not erase the retry/lease fence.
    IF NOT EXISTS(
        SELECT 1 FROM background_jobs j
        JOIN collaboration_devices d ON d.company_id=NEW.company_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
        JOIN collaboration_device_sessions b ON b.company_id=d.company_id AND b.device_id=d.id AND b.account_id=d.account_id
        WHERE j.id=NEW.job_id AND j.company_id=NEW.company_id AND j.actor_id=NEW.account_id
          AND j.kind='COLLABORATION_PROVISION' AND j.status='QUEUED' AND j.attempts=0
          AND j.completed_items=0 AND j.total_items=1 AND j.progress_mode='FIXED_TOTAL'
          AND j.created_at=NEW.updated_at AND d.state='PROVISIONING'
          AND j.request->>'deviceId'=d.id::text AND j.request->>'nativeSessionId'=b.native_session_id::text
          AND j.request->>'platform'=d.platform
    ) THEN
        RAISE EXCEPTION 'Collaboration provisioning scope is invalid' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_provisioning BEFORE INSERT OR UPDATE OR DELETE ON collaboration_device_provisioning
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_provisioning();
