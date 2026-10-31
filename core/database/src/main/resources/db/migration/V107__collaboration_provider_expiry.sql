-- Unknown historical provider lifetimes must never be inferred as fresh. Keep
-- their ciphertext for recovery/terminal retirement; rotate before live use.
ALTER TABLE collaboration_provider_sessions ADD COLUMN token_expires_at timestamptz;
ALTER TABLE collaboration_provider_sessions ADD CONSTRAINT collaboration_provider_expiry_bounds
    CHECK(token_expires_at IS NULL OR (isfinite(token_expires_at)
        AND token_expires_at>created_at
        AND (token_encrypted IS NULL OR
            (token_expires_at>updated_at AND token_expires_at<=updated_at+interval '10 minutes'))));

CREATE FUNCTION require_collaboration_provider_expiry() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- V106 rows may retain a NULL deadline, but no new/rotated bearer can do so.
    IF NEW.token_encrypted IS NOT NULL AND NEW.token_expires_at IS NULL THEN
        RAISE EXCEPTION 'Provider credential expiry is required' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND NEW.token_encrypted IS NULL
        AND NEW.token_expires_at IS DISTINCT FROM OLD.token_expires_at THEN
        RAISE EXCEPTION 'Retired provider credential expiry is retained' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER require_collaboration_provider_expiry BEFORE INSERT OR UPDATE ON collaboration_provider_sessions
    FOR EACH ROW EXECUTE FUNCTION require_collaboration_provider_expiry();
