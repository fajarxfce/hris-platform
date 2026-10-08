ALTER TABLE accounts ADD COLUMN invitation_pending boolean NOT NULL DEFAULT false;
ALTER TABLE accounts ADD CONSTRAINT pending_account_inactive CHECK(NOT (active AND invitation_pending));
CREATE FUNCTION protect_account_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.email,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.email,OLD.created_at)
       OR NEW.version<OLD.version OR NEW.security_version<OLD.security_version THEN
        RAISE EXCEPTION 'Account identity and credential versions are protected' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_account_identity BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION protect_account_identity();
