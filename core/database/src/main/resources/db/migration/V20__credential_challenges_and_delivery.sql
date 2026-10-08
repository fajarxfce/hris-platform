ALTER TABLE identity_challenges ADD COLUMN credential_version bigint NOT NULL DEFAULT 0;
ALTER TABLE identity_challenges ADD COLUMN created_by uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
ALTER TABLE identity_challenges ADD COLUMN issued_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE identity_challenges ADD COLUMN revoked_at timestamptz;
ALTER TABLE identity_challenges ADD CONSTRAINT credential_challenge_kind CHECK(kind IN ('INVITATION','PASSWORD_RECOVERY'));
ALTER TABLE identity_challenges ADD CONSTRAINT credential_challenge_time CHECK(expires_at>issued_at AND (consumed_at IS NULL OR consumed_at>=issued_at) AND (revoked_at IS NULL OR revoked_at>=issued_at));
CREATE UNIQUE INDEX one_pending_credential_challenge ON identity_challenges(account_id,kind) WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX credential_challenge_retention ON identity_challenges(expires_at,id);
CREATE FUNCTION protect_credential_challenge() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.account_id,NEW.kind,NEW.token_hash,NEW.expires_at,NEW.credential_version,NEW.created_by,NEW.issued_at)
        IS DISTINCT FROM (OLD.id,OLD.account_id,OLD.kind,OLD.token_hash,OLD.expires_at,OLD.credential_version,OLD.created_by,OLD.issued_at)
        OR (OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS DISTINCT FROM OLD.consumed_at)
        OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at) THEN
        RAISE EXCEPTION 'Credential challenge identity is immutable' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_credential_challenge BEFORE UPDATE ON identity_challenges FOR EACH ROW EXECUTE FUNCTION protect_credential_challenge();

ALTER TABLE identity_challenges ADD CONSTRAINT credential_challenge_delivery_identity UNIQUE(id,account_id,created_by,expires_at);

CREATE TABLE identity_mail_deliveries (
    challenge_id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts(id),
    created_by uuid NOT NULL,
    token_encrypted varchar(4096),
    state varchar(24) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','LEASED','SENT','FAILED','SUPERSEDED')),
    available_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid,
    lease_token uuid,
    lease_until timestamptz,
    delivered_at timestamptz,
    failure_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY(challenge_id,account_id,created_by,expires_at) REFERENCES identity_challenges(id,account_id,created_by,expires_at) ON DELETE CASCADE,
    CHECK(state NOT IN ('PENDING','LEASED') OR token_encrypted IS NOT NULL),
    CHECK(state NOT IN ('SENT','FAILED','SUPERSEDED') OR token_encrypted IS NULL),
    CHECK(state<>'LEASED' OR (lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX identity_mail_due ON identity_mail_deliveries(available_at,challenge_id) WHERE state IN ('PENDING','LEASED');
CREATE FUNCTION current_actor_is_identity_administrator() RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER AS $$
    SELECT EXISTS(SELECT 1 FROM accounts a JOIN platform_permissions p ON p.account_id=a.id
        WHERE a.id=current_actor_id() AND a.active AND p.permission='identity.manage')
$$;
ALTER TABLE identity_mail_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_mail_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY identity_mail_scope ON identity_mail_deliveries USING(created_by=current_actor_id() OR account_id=current_actor_id() OR current_actor_is_identity_administrator())
    WITH CHECK(created_by=current_actor_id() OR account_id=current_actor_id() OR current_actor_is_identity_administrator());
CREATE POLICY identity_mail_worker ON identity_mail_deliveries TO hris_worker_capability USING(true) WITH CHECK(true);
CREATE FUNCTION protect_identity_mail() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.challenge_id,NEW.account_id,NEW.created_by,NEW.created_at,NEW.expires_at) IS DISTINCT FROM (OLD.challenge_id,OLD.account_id,OLD.created_by,OLD.created_at,OLD.expires_at)
        OR (OLD.token_encrypted IS NOT NULL AND NEW.token_encrypted IS NOT NULL AND NEW.token_encrypted<>OLD.token_encrypted)
        OR (OLD.state IN ('SENT','FAILED','SUPERSEDED') AND NEW IS DISTINCT FROM OLD)
        OR NEW.attempts<OLD.attempts THEN
        RAISE EXCEPTION 'Delivery identity and terminal outcomes are immutable' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_identity_mail BEFORE UPDATE ON identity_mail_deliveries FOR EACH ROW EXECUTE FUNCTION protect_identity_mail();

CREATE FUNCTION claim_identity_mail(p_owner uuid,p_limit integer,p_lease_seconds integer,p_max_attempts integer)
RETURNS SETOF identity_mail_deliveries LANGUAGE plpgsql SECURITY INVOKER AS $$
BEGIN
    IF p_limit NOT BETWEEN 1 AND 2 OR p_lease_seconds NOT BETWEEN 30 AND 300 OR p_max_attempts NOT BETWEEN 1 AND 8 THEN
        RAISE EXCEPTION 'Invalid mail lease limits' USING ERRCODE='22023';
    END IF;
    RETURN QUERY
    WITH candidates AS (
        SELECT d.challenge_id FROM identity_mail_deliveries d
        WHERE d.state IN ('PENDING','LEASED') AND d.available_at<=clock_timestamp()
            AND (d.state='PENDING' OR d.lease_until<=clock_timestamp()) AND d.attempts<p_max_attempts
        ORDER BY d.available_at,d.challenge_id FOR UPDATE SKIP LOCKED LIMIT p_limit
    )
    UPDATE identity_mail_deliveries d SET state='LEASED',attempts=d.attempts+1,lease_owner=p_owner,
        lease_token=gen_random_uuid(),lease_until=clock_timestamp()+make_interval(secs=>p_lease_seconds)
    FROM candidates c WHERE d.challenge_id=c.challenge_id RETURNING d.*;
END $$;
REVOKE ALL ON FUNCTION claim_identity_mail(uuid,integer,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_identity_mail(uuid,integer,integer,integer) TO hris_worker_capability;
