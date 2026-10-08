CREATE TABLE object_cleanup_queue (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES companies(id),
    resource_id uuid NOT NULL,
    object_key varchar(300) NOT NULL UNIQUE,
    object_bytes bigint NOT NULL CHECK(object_bytes BETWEEN 1 AND 5242880),
    created_by uuid NOT NULL REFERENCES accounts(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    eligible_at timestamptz NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_token uuid,lease_owner uuid,lease_until timestamptz,
    failure_code varchar(80),
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    CHECK(object_key LIKE company_id::text || '/%'),
    CHECK((status='RUNNING' AND lease_token IS NOT NULL AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (status<>'RUNNING' AND lease_token IS NULL AND lease_owner IS NULL AND lease_until IS NULL))
);
CREATE INDEX object_cleanup_due ON object_cleanup_queue(eligible_at,id) WHERE status IN ('PENDING','RUNNING');
CREATE INDEX object_cleanup_resource ON object_cleanup_queue(company_id,resource_id,id);
ALTER TABLE object_cleanup_queue ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_cleanup_queue FORCE ROW LEVEL SECURITY;
CREATE POLICY object_cleanup_company ON object_cleanup_queue USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id());
CREATE POLICY object_cleanup_insert_actor ON object_cleanup_queue AS RESTRICTIVE FOR INSERT TO PUBLIC
    WITH CHECK(created_by=current_actor_id());
CREATE POLICY object_cleanup_worker ON object_cleanup_queue TO hris_worker_capability USING(true) WITH CHECK(true);
GRANT SELECT,INSERT,UPDATE,DELETE ON object_cleanup_queue TO hris_worker_capability;
CREATE FUNCTION protect_cleanup_key() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.id,NEW.company_id,NEW.resource_id,NEW.object_key,NEW.object_bytes,NEW.created_by,NEW.created_at)
        IS DISTINCT FROM (OLD.id,OLD.company_id,OLD.resource_id,OLD.object_key,OLD.object_bytes,OLD.created_by,OLD.created_at)
        OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Cleanup identity is immutable and changes must be versioned' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_cleanup_identity BEFORE UPDATE ON object_cleanup_queue FOR EACH ROW EXECUTE FUNCTION protect_cleanup_key();
CREATE FUNCTION claim_object_cleanup(p_owner uuid,p_limit integer,p_seconds integer,p_attempts integer)
RETURNS SETOF object_cleanup_queue LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE item public.object_cleanup_queue%ROWTYPE;claimed public.object_cleanup_queue%ROWTYPE;amount integer:=0;active_count integer;
BEGIN
    IF p_owner IS NULL OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 4 OR p_seconds IS NULL OR p_seconds NOT BETWEEN 30 AND 300
       OR p_attempts IS NULL OR p_attempts NOT BETWEEN 1 AND 8 THEN RAISE EXCEPTION 'Invalid cleanup lease' USING ERRCODE='22023';END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:object-cleanup-claims',0)) THEN RETURN;END IF;
    SELECT count(*) INTO active_count FROM public.object_cleanup_queue WHERE status='RUNNING' AND lease_until>clock_timestamp();
    FOR item IN SELECT * FROM public.object_cleanup_queue WHERE
        ((status='PENDING' AND eligible_at<=clock_timestamp()) OR (status='RUNNING' AND lease_until<=clock_timestamp())) AND attempts<p_attempts
        ORDER BY eligible_at,id FOR UPDATE SKIP LOCKED LIMIT 64 LOOP
        EXIT WHEN amount>=p_limit OR active_count>=4;
        IF (SELECT count(*) FROM public.object_cleanup_queue WHERE company_id=item.company_id AND status='RUNNING' AND lease_until>clock_timestamp())>=2 THEN CONTINUE;END IF;
        UPDATE public.object_cleanup_queue SET status='RUNNING',attempts=attempts+1,lease_owner=p_owner,lease_token=gen_random_uuid(),
            lease_until=clock_timestamp()+make_interval(secs=>p_seconds),version=version+1 WHERE id=item.id RETURNING * INTO claimed;
        RETURN NEXT claimed;amount:=amount+1;active_count:=active_count+1;
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION claim_object_cleanup(uuid,integer,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_object_cleanup(uuid,integer,integer,integer) TO hris_worker_capability;
