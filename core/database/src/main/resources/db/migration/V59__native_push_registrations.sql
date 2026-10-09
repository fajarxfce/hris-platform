ALTER TABLE native_sessions ADD CONSTRAINT native_session_account_identity UNIQUE(id,account_id);

CREATE TABLE native_push_registrations (
    session_id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    platform varchar(16) NOT NULL CHECK(platform IN ('ANDROID','IOS')),
    token_hash varchar(64) UNIQUE CHECK(token_hash ~ '^[0-9a-f]{64}$'),
    token_encrypted varchar(4096),
    enabled boolean NOT NULL,
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 10000),
    registered_at timestamptz NOT NULL CHECK(isfinite(registered_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at)),
    expires_at timestamptz NOT NULL CHECK(isfinite(expires_at)),
    FOREIGN KEY(session_id,account_id) REFERENCES native_sessions(id,account_id) ON DELETE CASCADE,
    CHECK(updated_at>=registered_at AND expires_at>registered_at),
    CHECK((enabled AND token_hash IS NOT NULL AND token_encrypted IS NOT NULL)
        OR (NOT enabled AND token_hash IS NULL AND token_encrypted IS NULL))
);
CREATE INDEX native_push_account ON native_push_registrations(account_id,session_id);
ALTER TABLE native_push_registrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE native_push_registrations FORCE ROW LEVEL SECURITY;
CREATE POLICY native_push_owner ON native_push_registrations USING(account_id=current_actor_id())
    WITH CHECK(account_id=current_actor_id());
CREATE POLICY native_push_worker ON native_push_registrations TO hris_worker_capability USING(true) WITH CHECK(true);

CREATE FUNCTION protect_native_push_registration() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN
            RAISE EXCEPTION 'Initial push registration version required' USING ERRCODE='23514';
        END IF;
    ELSIF (NEW.session_id,NEW.account_id,NEW.registered_at) IS DISTINCT FROM (OLD.session_id,OLD.account_id,OLD.registered_at)
        OR NEW.version<>OLD.version+1 OR NEW.updated_at<OLD.updated_at THEN
        RAISE EXCEPTION 'Push registration identity and versions are protected' USING ERRCODE='23514';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM native_sessions s WHERE s.id=NEW.session_id AND s.account_id=NEW.account_id
        AND NEW.registered_at>=s.created_at AND NEW.expires_at<=s.expires_at) THEN
        RAISE EXCEPTION 'Push registration must belong to its native session lifetime' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_native_push_registration BEFORE INSERT OR UPDATE ON native_push_registrations
    FOR EACH ROW EXECUTE FUNCTION protect_native_push_registration();
