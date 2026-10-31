-- One retained provider-room candidate per canonical direct conversation. Provider
-- calls happen outside transactions; the pointer and job lease fence publication.
CREATE TABLE collaboration_direct_room_provisioning (
    company_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    account_id uuid NOT NULL,
    device_id uuid NOT NULL,
    job_id uuid NOT NULL UNIQUE REFERENCES background_jobs(id),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999999),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    PRIMARY KEY(company_id,conversation_id),
    FOREIGN KEY(company_id,conversation_id) REFERENCES collaboration_direct_conversations(company_id,id),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id)
);
CREATE TABLE collaboration_direct_room_bindings (
    company_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES accounts(id),
    job_id uuid NOT NULL UNIQUE REFERENCES background_jobs(id),
    provisioning_version bigint NOT NULL CHECK(provisioning_version BETWEEN 0 AND 999999),
    matrix_server_name varchar(253) NOT NULL CHECK(length(matrix_server_name)>0 AND matrix_server_name !~ '[[:space:][:cntrl:]/?#@]'),
    matrix_room_id varchar(255) NOT NULL CHECK(length(matrix_room_id)>1 AND left(matrix_room_id,1)='!' AND matrix_room_id !~ '[[:space:][:cntrl:]]'),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    PRIMARY KEY(company_id,conversation_id),
    UNIQUE(matrix_server_name,matrix_room_id),
    FOREIGN KEY(company_id,conversation_id) REFERENCES collaboration_direct_room_provisioning(company_id,conversation_id)
);
ALTER TABLE collaboration_direct_room_provisioning ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_direct_room_provisioning FORCE ROW LEVEL SECURITY;
ALTER TABLE collaboration_direct_room_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_direct_room_bindings FORCE ROW LEVEL SECURITY;
CREATE POLICY direct_room_provisioning_read ON collaboration_direct_room_provisioning FOR SELECT
    USING(company_id=current_company_id() AND EXISTS(SELECT 1 FROM collaboration_direct_conversations d
        WHERE d.company_id=collaboration_direct_room_provisioning.company_id AND d.id=conversation_id));
CREATE POLICY direct_room_provisioning_insert ON collaboration_direct_room_provisioning FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY direct_room_provisioning_update ON collaboration_direct_room_provisioning FOR UPDATE
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY direct_room_binding_read ON collaboration_direct_room_bindings FOR SELECT
    USING(company_id=current_company_id() AND EXISTS(SELECT 1 FROM collaboration_direct_conversations d
        WHERE d.company_id=collaboration_direct_room_bindings.company_id AND d.id=conversation_id));
CREATE POLICY direct_room_binding_insert ON collaboration_direct_room_bindings FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE FUNCTION protect_collaboration_direct_room_provisioning() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Direct room provisioning is retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.created_at<>NEW.updated_at THEN
            RAISE EXCEPTION 'Invalid initial direct room provisioning' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.conversation_id,NEW.account_id,NEW.created_at)
            IS DISTINCT FROM (OLD.company_id,OLD.conversation_id,OLD.account_id,OLD.created_at)
            OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at OR NEW.job_id=OLD.job_id
            OR NOT EXISTS(SELECT 1 FROM background_jobs WHERE id=OLD.job_id AND status IN ('FAILED','CANCELLED'))
            OR EXISTS(SELECT 1 FROM collaboration_direct_room_bindings b WHERE b.company_id=NEW.company_id AND b.conversation_id=NEW.conversation_id) THEN
            RAISE EXCEPTION 'Invalid direct room provisioning retry' USING ERRCODE='23514';
        END IF;
    END IF;
    -- Creator is retained across retries; a different current device may resume
    -- the same account. Parent cleanup cannot erase this history (no parent FK).
    IF NOT EXISTS(
        SELECT 1 FROM background_jobs j
        JOIN collaboration_direct_conversations c ON c.company_id=NEW.company_id AND c.id=NEW.conversation_id
        JOIN collaboration_devices d ON d.company_id=NEW.company_id AND d.id=NEW.device_id AND d.account_id=NEW.account_id
        JOIN collaboration_device_sessions s ON s.company_id=d.company_id AND s.device_id=d.id AND s.account_id=d.account_id
        WHERE j.id=NEW.job_id AND j.company_id=NEW.company_id AND j.actor_id=NEW.account_id
          AND NEW.account_id IN (c.first_account_id,c.second_account_id)
          AND j.kind='COLLABORATION_DIRECT_PROVISION' AND j.status='QUEUED' AND j.attempts=0
          AND j.completed_items=0 AND j.total_items=1 AND j.progress_mode='FIXED_TOTAL'
          AND j.created_at=NEW.updated_at AND d.state='ACTIVE' AND d.revoked_at IS NULL
          AND j.request->>'conversationId'=c.id::text AND j.request->>'deviceId'=d.id::text
          AND j.request->>'nativeSessionId'=s.native_session_id::text AND j.request->>'platform'=d.platform
    ) THEN
        RAISE EXCEPTION 'Direct room provisioning scope is invalid' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_direct_room_provisioning BEFORE INSERT OR UPDATE OR DELETE ON collaboration_direct_room_provisioning
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_direct_room_provisioning();
CREATE FUNCTION protect_collaboration_direct_room_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP<>'INSERT' THEN
        RAISE EXCEPTION 'Direct room identity is immutable' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS(
        SELECT 1 FROM collaboration_direct_room_provisioning p
        JOIN background_jobs j ON j.id=p.job_id AND j.company_id=p.company_id AND j.actor_id=p.account_id
        WHERE p.company_id=NEW.company_id AND p.conversation_id=NEW.conversation_id
          AND p.account_id=NEW.account_id AND p.job_id=NEW.job_id AND p.version=NEW.provisioning_version
          AND j.kind='COLLABORATION_DIRECT_PROVISION' AND j.status='RUNNING' AND j.completed_items=0
          AND NEW.created_at>=p.updated_at
    ) THEN
        RAISE EXCEPTION 'Direct room publication fence is invalid' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_direct_room_binding BEFORE INSERT OR UPDATE OR DELETE ON collaboration_direct_room_bindings
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_direct_room_binding();
