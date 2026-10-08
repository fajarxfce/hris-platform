-- Pre-release session skeletons have no access credential and must fail closed.
UPDATE native_sessions SET revoked_at=coalesce(revoked_at,now());
ALTER TABLE native_sessions
    ADD access_hash varchar(64) UNIQUE,
    ADD access_expires_at timestamptz,
    ADD credential_version bigint NOT NULL DEFAULT -1,
    ADD mfa_verified_at timestamptz,
    ADD device_name varchar(100) NOT NULL DEFAULT 'Mobile',
    ADD created_at timestamptz NOT NULL DEFAULT now(),
    ADD exchange_operation_id uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD exchange_encrypted text,
    ADD exchange_replay_until timestamptz,
    ADD rotated_at timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT native_exchange_unique UNIQUE(account_id,exchange_operation_id);
CREATE INDEX native_session_account ON native_sessions(account_id,created_at,id);
CREATE INDEX native_session_expiry ON native_sessions(expires_at,id);
ALTER TABLE consumed_refresh_tokens
    DROP CONSTRAINT consumed_refresh_tokens_session_id_fkey,
    ADD CONSTRAINT consumed_refresh_session_fk FOREIGN KEY(session_id) REFERENCES native_sessions(id) ON DELETE CASCADE,
    ADD operation_id uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD successor_version bigint NOT NULL DEFAULT -1,
    ADD replay_encrypted text,
    ADD replay_until timestamptz;
CREATE INDEX consumed_refresh_session ON consumed_refresh_tokens(session_id);
