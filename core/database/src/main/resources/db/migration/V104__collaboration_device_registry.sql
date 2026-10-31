-- Device identity survives parent-session cleanup. A binding alone grants no access:
-- every acquisition must also admit the current HRIS session and company authority.
CREATE TABLE collaboration_devices (
    company_id uuid NOT NULL REFERENCES companies(id),
    id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES accounts(id),
    platform varchar(16) NOT NULL CHECK(platform IN ('ANDROID','IOS','WEB')),
    label varchar(64) NOT NULL CHECK(length(label) BETWEEN 1 AND 64 AND label=btrim(label) AND label !~ '[[:cntrl:]]'),
    state varchar(16) NOT NULL CHECK(state IN ('PROVISIONING','ACTIVE','REVOKED')),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999999),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at) AND updated_at>=created_at),
    revoked_at timestamptz CHECK(revoked_at IS NULL OR isfinite(revoked_at)),
    PRIMARY KEY(company_id,id),
    UNIQUE(company_id,id,account_id),
    CHECK((state='REVOKED')=(revoked_at IS NOT NULL)),
    CHECK(revoked_at IS NULL OR (revoked_at>=created_at AND revoked_at<=updated_at))
);
CREATE INDEX collaboration_device_owner ON collaboration_devices(account_id,company_id,created_at,id);

CREATE TABLE collaboration_device_sessions (
    company_id uuid NOT NULL,
    device_id uuid NOT NULL,
    account_id uuid NOT NULL,
    native_session_id uuid NOT NULL,
    bound_at timestamptz NOT NULL CHECK(isfinite(bound_at)),
    PRIMARY KEY(company_id,device_id,native_session_id),
    UNIQUE(company_id,native_session_id),
    FOREIGN KEY(company_id,device_id,account_id) REFERENCES collaboration_devices(company_id,id,account_id),
    FOREIGN KEY(native_session_id,account_id) REFERENCES native_sessions(id,account_id) ON DELETE CASCADE
);
CREATE INDEX collaboration_binding_owner ON collaboration_device_sessions(account_id,native_session_id);

ALTER TABLE collaboration_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_devices FORCE ROW LEVEL SECURITY;
ALTER TABLE collaboration_device_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE collaboration_device_sessions FORCE ROW LEVEL SECURITY;

-- Own-device metadata across companies is readable for the global device limit.
-- Product reads still require a live, explicitly selected company. No other
-- account's records become readable through company selection alone.
CREATE POLICY collaboration_device_read ON collaboration_devices FOR SELECT
    USING(account_id=current_actor_id());
CREATE POLICY collaboration_device_insert ON collaboration_devices FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY collaboration_device_update ON collaboration_devices FOR UPDATE
    USING(company_id=current_company_id() AND account_id=current_actor_id())
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());
CREATE POLICY collaboration_binding_read ON collaboration_device_sessions FOR SELECT
    USING(account_id=current_actor_id());
CREATE POLICY collaboration_binding_insert ON collaboration_device_sessions FOR INSERT
    WITH CHECK(company_id=current_company_id() AND account_id=current_actor_id());

CREATE FUNCTION protect_collaboration_device() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Collaboration device identity is retained' USING ERRCODE='23514';
    ELSIF TG_OP='INSERT' THEN
        IF NEW.state<>'PROVISIONING' OR NEW.version<>0 OR NEW.created_at<>NEW.updated_at THEN
            RAISE EXCEPTION 'Invalid initial collaboration device' USING ERRCODE='23514';
        END IF;
    ELSE
        IF (NEW.company_id,NEW.id,NEW.account_id,NEW.platform,NEW.created_at)
            IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.account_id,OLD.platform,OLD.created_at)
            OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at
            OR (OLD.state='REVOKED' AND (NEW.state<>'REVOKED' OR NEW.revoked_at<>OLD.revoked_at))
            OR (OLD.state='ACTIVE' AND NEW.state='PROVISIONING') THEN
            RAISE EXCEPTION 'Invalid collaboration device transition' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_collaboration_device BEFORE INSERT OR UPDATE OR DELETE ON collaboration_devices
    FOR EACH ROW EXECUTE FUNCTION protect_collaboration_device();

-- Bindings cannot be reassigned. Deletion is reserved for FK cascade when the
-- expired parent session is purged; the immutable device identity remains.
CREATE TRIGGER immutable_collaboration_binding BEFORE UPDATE ON collaboration_device_sessions
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
