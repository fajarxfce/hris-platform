CREATE TABLE company_client_policy_heads (
    company_id uuid PRIMARY KEY REFERENCES companies(id),
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 9999)
);

CREATE FUNCTION valid_client_modules(names varchar[]) RETURNS boolean LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT cardinality(names)<=8
        AND (cardinality(names)=0 OR array_ndims(names)=1)
        AND array_position(names,NULL) IS NULL
        AND names<@ARRAY['PEOPLE','WORKFORCE','LEAVE','EXPENSES','PAYROLL','DOCUMENTS','COMMUNICATIONS','REPORTING']::varchar[]
        AND cardinality(names)=(SELECT count(DISTINCT item) FROM unnest(names) item)
$$;

CREATE TABLE company_client_policy_revisions (
    company_id uuid NOT NULL REFERENCES company_client_policy_heads(company_id),
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 9999),
    activate_at timestamptz NOT NULL CHECK(isfinite(activate_at)),
    disabled_modules varchar[] NOT NULL CHECK(valid_client_modules(disabled_modules)),
    minimum_android_build integer NOT NULL CHECK(minimum_android_build BETWEEN 0 AND 999999999),
    minimum_ios_build integer NOT NULL CHECK(minimum_ios_build BETWEEN 0 AND 999999999),
    minimum_web_build integer NOT NULL CHECK(minimum_web_build BETWEEN 0 AND 999999999),
    maintenance_starts_at timestamptz, maintenance_ends_at timestamptz,
    recorded_at timestamptz NOT NULL CHECK(isfinite(recorded_at)),
    actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,version),
    CHECK(activate_at>=recorded_at),
    CHECK((maintenance_starts_at IS NULL AND maintenance_ends_at IS NULL) OR
        (maintenance_starts_at IS NOT NULL AND maintenance_ends_at IS NOT NULL
            AND isfinite(maintenance_starts_at) AND isfinite(maintenance_ends_at)
            AND maintenance_ends_at>maintenance_starts_at
            AND maintenance_ends_at<=maintenance_starts_at+interval '7 days'))
);
ALTER TABLE company_client_policy_heads ADD FOREIGN KEY(company_id,version)
    REFERENCES company_client_policy_revisions(company_id,version) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX client_policy_activation ON company_client_policy_revisions(company_id,activate_at,version);

CREATE FUNCTION protect_client_policy_head() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN RAISE EXCEPTION 'Client policy starts at zero' USING ERRCODE='23514'; END IF;
    ELSIF NEW.company_id IS DISTINCT FROM OLD.company_id OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Client policy identity and version are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER client_policy_head_version BEFORE INSERT OR UPDATE ON company_client_policy_heads
    FOR EACH ROW EXECUTE FUNCTION protect_client_policy_head();
CREATE TRIGGER retained_client_policy_head BEFORE DELETE ON company_client_policy_heads
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION validate_client_policy_revision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM company_client_policy_heads WHERE company_id=NEW.company_id AND version=NEW.version)
        OR EXISTS(SELECT 1 FROM company_client_policy_revisions WHERE company_id=NEW.company_id
            AND version=NEW.version-1 AND recorded_at>NEW.recorded_at) THEN
        RAISE EXCEPTION 'Client policy revision does not match its head' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER client_policy_revision_head BEFORE INSERT ON company_client_policy_revisions
    FOR EACH ROW EXECUTE FUNCTION validate_client_policy_revision();
CREATE TRIGGER immutable_client_policy_revision BEFORE UPDATE OR DELETE ON company_client_policy_revisions
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

ALTER TABLE company_client_policy_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE company_client_policy_heads FORCE ROW LEVEL SECURITY;
ALTER TABLE company_client_policy_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE company_client_policy_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY client_policy_head_scope ON company_client_policy_heads
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY client_policy_revision_scope ON company_client_policy_revisions
    USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY client_policy_revision_author ON company_client_policy_revisions AS RESTRICTIVE FOR INSERT
    WITH CHECK(actor_id=current_actor_id());
