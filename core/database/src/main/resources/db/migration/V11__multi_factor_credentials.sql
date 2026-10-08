ALTER TABLE accounts ADD security_version bigint NOT NULL DEFAULT 0 CHECK(security_version>=0);
CREATE TABLE mfa_enrollments (
    account_id uuid PRIMARY KEY REFERENCES accounts(id), operation_id uuid NOT NULL,
    secret_encrypted text NOT NULL, expires_at timestamptz NOT NULL
);
CREATE TABLE mfa_attempts (
    account_id uuid PRIMARY KEY REFERENCES accounts(id), window_start timestamptz NOT NULL,
    attempts integer NOT NULL CHECK(attempts BETWEEN 1 AND 100)
);
CREATE TABLE mfa_recovery_codes (
    account_id uuid NOT NULL REFERENCES accounts(id), code_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL, used_at timestamptz,
    PRIMARY KEY(account_id,code_hash)
);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['mfa_enrollments','mfa_attempts','mfa_recovery_codes'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY account_scope ON %I USING(account_id=current_actor_id()) WITH CHECK(account_id=current_actor_id())',t);
    END LOOP;
END $$;
