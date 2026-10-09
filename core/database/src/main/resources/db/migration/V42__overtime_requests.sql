CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE overtime_requests (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,
    employee_number varchar(40) NOT NULL,employee_name varchar(200) NOT NULL,
    requester_account_id uuid REFERENCES accounts(id),author_id uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,
    work_date date NOT NULL CHECK(extract(year FROM work_date) BETWEEN 2000 AND 2100),timezone varchar(80) NOT NULL,
    schedule jsonb NOT NULL CHECK(jsonb_typeof(schedule)='object' AND pg_column_size(schedule)<=16384),
    requested_start timestamptz NOT NULL,requested_end timestamptz NOT NULL,requested_break integer NOT NULL CHECK(requested_break BETWEEN 0 AND 120),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    status varchar(16) NOT NULL CHECK(status IN ('PLANNED','PENDING','APPROVED','REJECTED','WITHDRAWN')),
    actual_start timestamptz,actual_end timestamptz,actual_break integer,
    submitted_by uuid REFERENCES accounts(id),submitted_at timestamptz,approval_id uuid,
    approved_minutes integer NOT NULL DEFAULT 0 CHECK(approved_minutes BETWEEN 0 AND 720),decided_at timestamptz,
    version bigint NOT NULL CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,approval_id) REFERENCES approval_requests(company_id,id),
    CHECK(isfinite(requested_start) AND isfinite(requested_end) AND requested_end>requested_start AND requested_end-requested_start<=interval '12 hours'),
    CHECK(extract(epoch FROM requested_start)::numeric%60=0 AND extract(epoch FROM requested_end)::numeric%60=0),
    CHECK(extract(epoch FROM requested_end-requested_start)/60>requested_break),
    CHECK(num_nonnulls(actual_start,actual_end,actual_break,submitted_by,submitted_at,approval_id) IN (0,6)),
    CHECK(actual_start IS NULL OR (isfinite(actual_start) AND isfinite(actual_end) AND actual_end>actual_start AND actual_start>=requested_start AND actual_end<=requested_end
        AND extract(epoch FROM actual_start)::numeric%60=0 AND extract(epoch FROM actual_end)::numeric%60=0
        AND actual_break BETWEEN 0 AND 120 AND extract(epoch FROM actual_end-actual_start)/60>actual_break
        AND extract(epoch FROM actual_end-actual_start)/60-actual_break<=extract(epoch FROM requested_end-requested_start)/60-requested_break)),
    CHECK((status='PLANNED' AND actual_start IS NULL AND decided_at IS NULL)
        OR (status='PENDING' AND actual_start IS NOT NULL AND decided_at IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND actual_start IS NOT NULL AND decided_at IS NOT NULL)
        OR (status='WITHDRAWN' AND decided_at IS NOT NULL)),
    CHECK((status='APPROVED' AND approved_minutes=extract(epoch FROM actual_end-actual_start)/60-actual_break) OR (status<>'APPROVED' AND approved_minutes=0)),
    CHECK(submitted_at IS NULL OR (submitted_at>=created_at AND actual_end<=submitted_at)),
    CHECK(decided_at IS NULL OR decided_at>=coalesce(submitted_at,created_at)),
    EXCLUDE USING gist (company_id WITH =, employment_id WITH =, tstzrange(requested_start,requested_end,'[)') WITH &&)
        WHERE (status IN ('PLANNED','PENDING','APPROVED'))
);
CREATE INDEX overtime_month ON overtime_requests(company_id,work_date,employment_id,id);
CREATE INDEX overtime_employee_month ON overtime_requests(company_id,employment_id,work_date,id);
CREATE INDEX overtime_unresolved ON overtime_requests(company_id,work_date) WHERE status IN ('PLANNED','PENDING');

CREATE TABLE overtime_changes (
    company_id uuid NOT NULL,request_id uuid NOT NULL,revision bigint NOT NULL,
    kind varchar(32) NOT NULL CHECK(kind IN ('PLANNED','ACTUAL_SUBMITTED','DECIDED','WITHDRAWN')),
    status varchar(16) NOT NULL,actual_start timestamptz,actual_end timestamptz,actual_break integer,
    approved_minutes integer NOT NULL,approval_id uuid,actor_id uuid NOT NULL REFERENCES accounts(id),
    recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL,
    PRIMARY KEY(company_id,request_id,revision),FOREIGN KEY(company_id,request_id) REFERENCES overtime_requests(company_id,id)
);
ALTER TABLE overtime_requests ADD CONSTRAINT overtime_current_change FOREIGN KEY(company_id,id,version)
    REFERENCES overtime_changes(company_id,request_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE FUNCTION protect_overtime_request() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Overtime evidence is retained' USING ERRCODE='23514'; END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.status<>'PLANNED' THEN RAISE EXCEPTION 'Invalid initial overtime state' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF NEW.version<>OLD.version+1 OR (to_jsonb(NEW)-ARRAY['version','status','actual_start','actual_end','actual_break','submitted_by','submitted_at','approval_id','approved_minutes','decided_at'])
        IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['version','status','actual_start','actual_end','actual_break','submitted_by','submitted_at','approval_id','approved_minutes','decided_at'])
        THEN RAISE EXCEPTION 'Overtime request snapshot is immutable' USING ERRCODE='23514'; END IF;
    IF NOT ((OLD.status='PLANNED' AND NEW.status IN ('PENDING','WITHDRAWN')) OR (OLD.status='PENDING' AND NEW.status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')))
        THEN RAISE EXCEPTION 'Overtime transition is invalid' USING ERRCODE='23514'; END IF;
    IF OLD.approval_id IS NOT NULL AND (NEW.actual_start,NEW.actual_end,NEW.actual_break,NEW.submitted_by,NEW.submitted_at,NEW.approval_id)
        IS DISTINCT FROM (OLD.actual_start,OLD.actual_end,OLD.actual_break,OLD.submitted_by,OLD.submitted_at,OLD.approval_id)
        THEN RAISE EXCEPTION 'Overtime actual evidence is immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_overtime BEFORE INSERT OR UPDATE OR DELETE ON overtime_requests FOR EACH ROW EXECUTE FUNCTION protect_overtime_request();
CREATE FUNCTION validate_overtime_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE r overtime_requests; BEGIN
    SELECT * INTO r FROM overtime_requests WHERE company_id=NEW.company_id AND id=NEW.request_id;
    IF r.id IS NULL OR (r.version,r.status,r.actual_start,r.actual_end,r.actual_break,r.approved_minutes,r.approval_id)
        IS DISTINCT FROM (NEW.revision,NEW.status,NEW.actual_start,NEW.actual_end,NEW.actual_break,NEW.approved_minutes,NEW.approval_id)
        OR (NEW.kind='PLANNED' AND (NEW.revision<>0 OR NEW.status<>'PLANNED'))
        OR (NEW.kind='ACTUAL_SUBMITTED' AND (NEW.revision<>1 OR NEW.status<>'PENDING' OR NEW.actor_id<>r.submitted_by))
        OR (NEW.kind='DECIDED' AND NEW.status NOT IN ('PENDING','APPROVED','REJECTED'))
        OR (NEW.kind='WITHDRAWN' AND NEW.status<>'WITHDRAWN')
        THEN RAISE EXCEPTION 'Overtime revision does not match its request' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER overtime_change_state BEFORE INSERT ON overtime_changes FOR EACH ROW EXECUTE FUNCTION validate_overtime_change();
CREATE TRIGGER overtime_change_immutable BEFORE UPDATE OR DELETE ON overtime_changes FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION assert_overtime_approval(p_company uuid,p_request uuid) RETURNS void LANGUAGE plpgsql SECURITY INVOKER AS $$
DECLARE r overtime_requests; a approval_requests; decisions integer; BEGIN
    SELECT * INTO r FROM overtime_requests WHERE company_id=p_company AND id=p_request;
    IF r.id IS NULL THEN RAISE EXCEPTION 'Overtime request is missing' USING ERRCODE='23514'; END IF;
    IF r.approval_id IS NULL THEN RETURN; END IF;
    SELECT * INTO a FROM approval_requests WHERE company_id=p_company AND id=r.approval_id;
    IF a.id IS NULL OR a.kind<>'OVERTIME' OR a.resource_id<>r.id OR a.author_id<>r.submitted_by
        OR NOT ((r.status='PENDING' AND a.status IN ('PENDING','BLOCKED')) OR (r.status='APPROVED' AND a.status='APPROVED')
            OR (r.status='REJECTED' AND a.status='REJECTED') OR (r.status='WITHDRAWN' AND a.status='CANCELLED'))
        THEN RAISE EXCEPTION 'Overtime approval and business state disagree' USING ERRCODE='23514'; END IF;
    SELECT count(*) INTO decisions FROM approval_decisions WHERE company_id=p_company AND request_id=a.id;
    IF r.version<>(CASE WHEN r.status='WITHDRAWN' THEN 2 ELSE 1 END)+decisions
        THEN RAISE EXCEPTION 'Overtime decision evidence is incomplete' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM approval_decisions d WHERE d.company_id=p_company AND d.request_id=a.id AND
        (d.actor_id=ANY(array_remove(ARRAY[r.author_id,r.submitted_by,r.requester_account_id,a.requester_id],NULL))
        OR d.deciding_for=ANY(array_remove(ARRAY[r.author_id,r.submitted_by,r.requester_account_id,a.requester_id],NULL))))
        THEN RAISE EXCEPTION 'Overtime review must be independent' USING ERRCODE='23514'; END IF;
END $$;
CREATE FUNCTION check_overtime_approval() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_TABLE_NAME='overtime_requests' THEN PERFORM assert_overtime_approval(NEW.company_id,NEW.id);
    ELSIF NEW.kind='OVERTIME' THEN PERFORM assert_overtime_approval(NEW.company_id,NEW.resource_id); END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER overtime_approval_complete AFTER INSERT OR UPDATE ON overtime_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_overtime_approval();
CREATE CONSTRAINT TRIGGER approval_overtime_complete AFTER INSERT OR UPDATE ON approval_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_overtime_approval();

ALTER TABLE overtime_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE overtime_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY overtime_scope ON overtime_requests USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE overtime_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE overtime_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY overtime_change_scope ON overtime_changes USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS'));
CREATE FUNCTION record_overtime_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item overtime_requests%ROWTYPE; BEGIN
    IF TG_OP='DELETE' THEN item:=OLD; ELSE item:=NEW; END IF;
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
        VALUES(item.company_id,'OVERTIME_REQUESTS',item.id,item.employment_id,item.version,CASE WHEN TG_OP='DELETE' THEN 'DELETE' ELSE 'UPSERT' END);
    RETURN NULL;
END $$;
CREATE TRIGGER overtime_sync_change AFTER INSERT OR UPDATE OR DELETE ON overtime_requests FOR EACH ROW EXECUTE FUNCTION record_overtime_sync_change();
