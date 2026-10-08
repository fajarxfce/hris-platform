CREATE TABLE leave_requests (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,employee_number varchar(32) NOT NULL,employee_name varchar(200) NOT NULL,owner_account_id uuid REFERENCES accounts(id),author_id uuid NOT NULL REFERENCES accounts(id),
    submitted_at timestamptz NOT NULL,type_id uuid NOT NULL,type_revision bigint NOT NULL,type_snapshot jsonb NOT NULL,days jsonb NOT NULL,
    type_code varchar(32) GENERATED ALWAYS AS (type_snapshot->>'code') STORED NOT NULL,
    type_name varchar(200) GENERATED ALWAYS AS (type_snapshot->'policy'->>'name') STORED NOT NULL,
    starts_on date NOT NULL,ends_on date NOT NULL,half_days integer NOT NULL CHECK(half_days BETWEEN 1 AND 732),reason varchar(1000) NOT NULL,
    status varchar(32) NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED','CANCELLED','CANCELLATION_PENDING')),
    approval_id uuid NOT NULL,cancellation_approval_id uuid,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,id,employment_id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,type_id,type_revision) REFERENCES leave_type_revisions(company_id,type_id,revision),
    FOREIGN KEY(company_id,approval_id) REFERENCES approval_requests(company_id,id),
    FOREIGN KEY(company_id,cancellation_approval_id) REFERENCES approval_requests(company_id,id),
    CHECK(ends_on>=starts_on AND ends_on-starts_on<=365),CHECK(status<>'CANCELLATION_PENDING' OR cancellation_approval_id IS NOT NULL)
);
CREATE INDEX leave_request_list ON leave_requests(company_id,status,submitted_at DESC,id DESC);
CREATE INDEX leave_employee_requests ON leave_requests(company_id,employment_id,submitted_at DESC,id DESC);
CREATE TABLE leave_request_changes (
    company_id uuid NOT NULL,request_id uuid NOT NULL,revision bigint NOT NULL,kind varchar(32) NOT NULL CHECK(kind IN ('SUBMITTED','DECIDED','WITHDRAWN','CANCELLATION_REQUESTED')),status varchar(32) NOT NULL,
    cancellation_approval_id uuid,actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL,
    PRIMARY KEY(company_id,request_id,revision),FOREIGN KEY(company_id,request_id) REFERENCES leave_requests(company_id,id),
    FOREIGN KEY(company_id,cancellation_approval_id) REFERENCES approval_requests(company_id,id)
);
CREATE TABLE leave_allocations (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,work_date date NOT NULL,slot smallint NOT NULL CHECK(slot IN (1,2)),request_id uuid NOT NULL,
    PRIMARY KEY(company_id,employment_id,work_date,slot),
    FOREIGN KEY(company_id,request_id,employment_id) REFERENCES leave_requests(company_id,id,employment_id)
);
CREATE INDEX leave_allocations_request ON leave_allocations(company_id,request_id);
ALTER TABLE leave_ledger ADD FOREIGN KEY(company_id,request_id) REFERENCES leave_requests(company_id,id);
CREATE FUNCTION protect_leave_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Leave requests retain their history' USING ERRCODE='23514'; END IF;
    -- Generated columns are recalculated after BEFORE triggers. Their source
    -- (type_snapshot) remains part of the immutable comparison.
    IF (to_jsonb(NEW)-ARRAY['status','cancellation_approval_id','version','type_code','type_name']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','cancellation_approval_id','version','type_code','type_name']) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Leave snapshots are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_snapshot BEFORE UPDATE OR DELETE ON leave_requests FOR EACH ROW EXECUTE FUNCTION protect_leave_snapshot();
CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON leave_request_changes FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_requests','leave_request_changes','leave_allocations'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;
