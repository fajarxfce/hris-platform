DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='hris_worker_capability') THEN
        CREATE ROLE hris_worker_capability NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END $$;
CREATE TABLE background_jobs (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL REFERENCES companies(id),
    actor_id uuid NOT NULL REFERENCES accounts(id),
    kind varchar(80) NOT NULL,
    operation_id uuid NOT NULL,
    request jsonb NOT NULL CHECK(octet_length(request::text)<=8192),
    authenticated_at timestamptz NOT NULL,
    credential_version bigint NOT NULL CHECK(credential_version>=0),
    correlation_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    status varchar(16) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    available_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
    lease_owner uuid,
    lease_token uuid,
    lease_until timestamptz,
    cancellation_requested boolean NOT NULL DEFAULT false,
    completed_items integer NOT NULL DEFAULT 0 CHECK(completed_items BETWEEN 0 AND 1000000),
    total_items integer NOT NULL CHECK(total_items BETWEEN 1 AND 1000000),
    checkpoint jsonb NOT NULL DEFAULT '{}' CHECK(octet_length(checkpoint::text)<=8192),
    failure_code varchar(80),
    finished_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    UNIQUE(company_id,actor_id,kind,operation_id),
    CHECK(completed_items<=total_items),
    CHECK((status='RUNNING' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (status<>'RUNNING' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX jobs_company_created ON background_jobs(company_id,created_at DESC,id);
CREATE INDEX jobs_available ON background_jobs(available_at,created_at,id) WHERE status IN ('QUEUED','RUNNING');
ALTER TABLE background_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE background_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY jobs_company_scope ON background_jobs USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE POLICY jobs_worker_scope ON background_jobs TO hris_worker_capability USING(true) WITH CHECK(true);
GRANT SELECT, INSERT, UPDATE, DELETE ON background_jobs TO hris_worker_capability;

CREATE FUNCTION protect_job_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (OLD.id,OLD.company_id,OLD.actor_id,OLD.kind,OLD.operation_id,OLD.request,OLD.authenticated_at,OLD.credential_version,OLD.correlation_id,OLD.created_at,OLD.total_items)
        IS DISTINCT FROM (NEW.id,NEW.company_id,NEW.actor_id,NEW.kind,NEW.operation_id,NEW.request,NEW.authenticated_at,NEW.credential_version,NEW.correlation_id,NEW.created_at,NEW.total_items) THEN
        RAISE EXCEPTION 'Job requests are immutable' USING ERRCODE='42501';
    END IF;
    IF OLD.status IN ('SUCCEEDED','FAILED','CANCELLED') AND OLD IS DISTINCT FROM NEW THEN
        RAISE EXCEPTION 'Terminal job results are immutable' USING ERRCODE='42501';
    END IF;
    IF NEW.completed_items<OLD.completed_items THEN RAISE EXCEPTION 'Job progress cannot decrease' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_job_request BEFORE UPDATE ON background_jobs FOR EACH ROW EXECUTE FUNCTION protect_job_request();

-- Invoker rights and a queue-only RLS policy; application credentials cannot lease work.
CREATE FUNCTION claim_background_jobs(p_owner uuid,p_limit integer,p_seconds integer,p_kinds text[],p_max_attempts integer)
RETURNS SETOF background_jobs LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE item public.background_jobs%ROWTYPE; claimed public.background_jobs%ROWTYPE; amount integer:=0; active_count integer;
BEGIN
    IF p_max_attempts IS NULL OR p_max_attempts NOT BETWEEN 1 AND 8 OR p_owner IS NULL OR p_limit IS NULL OR p_seconds IS NULL OR p_kinds IS NULL OR cardinality(p_kinds) NOT BETWEEN 1 AND 32 OR p_limit NOT BETWEEN 1 AND 4 OR p_seconds NOT BETWEEN 15 AND 300 THEN
        RAISE EXCEPTION 'Invalid lease request' USING ERRCODE='22023';
    END IF;
    IF NOT pg_try_advisory_xact_lock(hashtextextended('hris:job-claims',0)) THEN RETURN; END IF;
    SELECT count(*) INTO active_count FROM public.background_jobs WHERE status='RUNNING' AND lease_until>clock_timestamp();
    FOR item IN SELECT * FROM public.background_jobs
        WHERE ((status='QUEUED' AND available_at<=clock_timestamp()) OR (status='RUNNING' AND lease_until<=clock_timestamp())) AND attempts<p_max_attempts AND kind=ANY(p_kinds)
        ORDER BY available_at,created_at,id FOR UPDATE SKIP LOCKED LIMIT 64 LOOP
        EXIT WHEN amount>=p_limit OR active_count>=4;
        IF (SELECT count(*) FROM public.background_jobs WHERE company_id=item.company_id AND status='RUNNING' AND lease_until>clock_timestamp())>=2 THEN CONTINUE; END IF;
        UPDATE public.background_jobs SET status='RUNNING',attempts=attempts+1,lease_owner=p_owner,lease_token=gen_random_uuid(),
            lease_until=clock_timestamp()+make_interval(secs=>p_seconds),version=version+1
            WHERE id=item.id RETURNING * INTO claimed;
        RETURN NEXT claimed; amount:=amount+1; active_count:=active_count+1;
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION claim_background_jobs(uuid,integer,integer,text[],integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_background_jobs(uuid,integer,integer,text[],integer) TO hris_worker_capability;
