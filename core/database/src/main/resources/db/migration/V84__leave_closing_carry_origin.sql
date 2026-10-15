-- Retain the reviewed batch calendar without reattributing historical closings.
ALTER TABLE leave_year_closings ADD COLUMN batch_id uuid,
    ADD CONSTRAINT leave_closing_batch_origin FOREIGN KEY(company_id,batch_id) REFERENCES leave_batches(company_id,id);
CREATE INDEX leave_closing_batch ON leave_year_closings(company_id,batch_id) WHERE batch_id IS NOT NULL;

CREATE OR REPLACE FUNCTION validate_leave_year_closing() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a leave_accounts; d leave_accounts; p leave_type_revisions; months integer; carry_limit integer; BEGIN
    SELECT * INTO a FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year;
    SELECT * INTO d FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year+1;
    SELECT * INTO p FROM leave_type_revisions WHERE company_id=NEW.company_id AND type_id=NEW.type_id AND revision=NEW.policy_revision;
    IF (NEW.policy_snapshot->>'typeId') IS DISTINCT FROM NEW.type_id::text OR (NEW.policy_snapshot->>'revision') IS DISTINCT FROM NEW.policy_revision::text
        OR (NEW.policy_snapshot->'policy') IS DISTINCT FROM (p.details || jsonb_build_object('attachmentRequired',coalesce(p.details->'attachmentRequired','false'::jsonb),'accrual',p.details->'accrual'))
        OR (NEW.policy_snapshot->>'code') IS DISTINCT FROM (SELECT code FROM leave_types WHERE company_id=NEW.company_id AND id=NEW.type_id)
        THEN RAISE EXCEPTION 'Leave policy evidence is inconsistent' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM leave_carry_buckets WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id
        AND type_id=NEW.type_id AND balance_year=NEW.balance_year AND expires_at<=NEW.recorded_at AND available_half_days>0)
        THEN RAISE EXCEPTION 'Closing must settle expired carried credit first' USING ERRCODE='23514'; END IF;
    months:=(p.details->'accrual'->>'carryValidityMonths')::integer;
    carry_limit:=coalesce((p.details->'accrual'->>'carryLimitHalfDays')::integer,0);
    IF (months IS NOT NULL AND (months NOT BETWEEN 1 AND 12 OR NOT NEW.carry_tracking))
       OR (NEW.carry_tracking AND NEW.batch_id IS NULL AND NEW.company_timezone IS DISTINCT FROM (SELECT timezone FROM companies WHERE id=NEW.company_id))
        THEN RAISE EXCEPTION 'Closing carry policy metadata is invalid' USING ERRCODE='23514'; END IF;
    IF NEW.carry_tracking AND months IS NOT NULL AND NEW.recorded_at>=
        ((make_date(NEW.balance_year+1,1,1)+make_interval(months=>months))::timestamp AT TIME ZONE NEW.company_timezone)
        THEN carry_limit:=0; END IF;
    IF p.type_id IS NULL OR a.closing_id IS NOT NULL OR coalesce(a.version,0)<>NEW.source_version OR coalesce(a.available_half_days,0)<>NEW.available_half_days OR coalesce(a.reserved_half_days,0)<>0 OR coalesce(a.consumed_half_days,0)<>NEW.consumed_half_days
        OR NEW.carry_half_days<>least(NEW.available_half_days,carry_limit)
        OR (NEW.carry_half_days>0 AND (d.closing_id IS NOT NULL OR coalesce(d.version,0)<>NEW.destination_version))
        THEN RAISE EXCEPTION 'Leave year closing snapshot is inconsistent' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM leave_requests WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id
        AND status IN ('PENDING','CANCELLATION_PENDING') AND starts_on<=make_date(NEW.balance_year,12,31) AND ends_on>=make_date(NEW.balance_year,1,1))
        THEN RAISE EXCEPTION 'Unresolved leave requests prevent year closing' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION validate_leave_closing_origin() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_batches; j background_jobs; BEGIN
    IF NEW.batch_id IS NULL THEN RETURN NEW; END IF;
    SELECT * INTO b FROM leave_batches WHERE company_id=NEW.company_id AND id=NEW.batch_id;
    SELECT * INTO j FROM background_jobs WHERE company_id=NEW.company_id AND id=b.job_id;
    IF b.id IS NULL OR b.kind<>'YEAR_CLOSE' OR b.status<>'RUNNING' OR NOT NEW.carry_tracking
        OR (NEW.type_id,NEW.policy_revision,NEW.policy_snapshot,NEW.actor_id,NEW.reason,NEW.company_timezone)
            IS DISTINCT FROM (b.type_id,b.policy_revision,b.policy_snapshot,b.actor_id,b.reason,b.timezone)
        OR NEW.balance_year<>extract(year FROM b.period)::integer OR NEW.recorded_at<b.created_at
        OR j.id IS NULL OR j.status<>'RUNNING' OR j.cancellation_requested OR j.lease_until<=clock_timestamp()
        OR NOT EXISTS(SELECT 1 FROM leave_batch_targets WHERE company_id=NEW.company_id AND batch_id=b.id AND employment_id=NEW.employment_id)
        OR EXISTS(SELECT 1 FROM leave_batch_results WHERE company_id=NEW.company_id AND batch_id=b.id AND employment_id=NEW.employment_id)
        THEN RAISE EXCEPTION 'Closing requires its reviewed active batch target' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_closing_origin BEFORE INSERT ON leave_year_closings
    FOR EACH ROW EXECUTE FUNCTION validate_leave_closing_origin();

CREATE FUNCTION check_leave_closing_origin() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.batch_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM leave_batch_results WHERE company_id=NEW.company_id
        AND batch_id=NEW.batch_id AND employment_id=NEW.employment_id AND resource_id=NEW.id AND status='APPLIED')
        THEN RAISE EXCEPTION 'Closing batch outcome is missing' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_closing_origin_complete AFTER INSERT ON leave_year_closings
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_closing_origin();

CREATE FUNCTION validate_leave_carry_batch_result() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c leave_year_closings; BEGIN
    IF NEW.batch_kind='YEAR_CLOSE' AND NEW.status='APPLIED' THEN
        SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.resource_id;
        IF c.carry_tracking AND c.batch_id IS DISTINCT FROM NEW.batch_id
            THEN RAISE EXCEPTION 'Tracked closing belongs to another batch origin' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_result_carry_origin BEFORE INSERT ON leave_batch_results
    FOR EACH ROW EXECUTE FUNCTION validate_leave_carry_batch_result();
