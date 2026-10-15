-- Carried credit is attributed only for new, explicitly tracked closing awards.
-- Existing closings and request funding remain unchanged.
ALTER TABLE leave_year_closings
    ADD COLUMN carry_tracking boolean NOT NULL DEFAULT false,
    ADD COLUMN company_timezone varchar(80),
    ADD CONSTRAINT leave_closing_carry_metadata CHECK(carry_tracking=(company_timezone IS NOT NULL));

CREATE TABLE leave_carry_buckets (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,type_id uuid NOT NULL,
    balance_year integer NOT NULL CHECK(balance_year BETWEEN 1901 AND 2200),
    initial_half_days integer NOT NULL CHECK(initial_half_days BETWEEN 1 AND 732),
    available_half_days integer NOT NULL CHECK(available_half_days BETWEEN 0 AND 732),
    reserved_half_days integer NOT NULL DEFAULT 0 CHECK(reserved_half_days BETWEEN 0 AND 732),
    consumed_half_days integer NOT NULL DEFAULT 0 CHECK(consumed_half_days BETWEEN 0 AND 732),
    expired_half_days integer NOT NULL DEFAULT 0 CHECK(expired_half_days BETWEEN 0 AND 732),
    transferred_half_days integer NOT NULL DEFAULT 0 CHECK(transferred_half_days BETWEEN 0 AND 732),
    deducted_half_days integer NOT NULL DEFAULT 0 CHECK(deducted_half_days BETWEEN 0 AND 732),
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 1000000000),
    company_timezone varchar(80) NOT NULL,validity_months integer CHECK(validity_months BETWEEN 1 AND 12),
    valid_through date,expires_at timestamptz,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,employment_id,type_id,balance_year),
    FOREIGN KEY(company_id,id,employment_id,type_id) REFERENCES leave_year_closings(company_id,id,employment_id,type_id),
    FOREIGN KEY(company_id,employment_id,type_id,balance_year) REFERENCES leave_accounts(company_id,employment_id,type_id,balance_year) DEFERRABLE INITIALLY DEFERRED,
    CHECK(initial_half_days=available_half_days+reserved_half_days+consumed_half_days+expired_half_days+transferred_half_days+deducted_half_days),
    CHECK((validity_months IS NULL AND valid_through IS NULL AND expires_at IS NULL)
       OR (validity_months IS NOT NULL AND valid_through IS NOT NULL AND expires_at IS NOT NULL AND extract(year FROM valid_through)=balance_year))
);
CREATE INDEX leave_carry_expiry_due ON leave_carry_buckets(company_id,expires_at,id)
    WHERE expires_at IS NOT NULL AND available_half_days>0;

ALTER TABLE leave_requests ADD COLUMN carry_allocation_count smallint NOT NULL DEFAULT 0
    CHECK(carry_allocation_count BETWEEN 0 AND 2);

CREATE TABLE leave_carry_allocations (
    company_id uuid NOT NULL,request_id uuid NOT NULL,bucket_id uuid NOT NULL,
    half_days integer NOT NULL CHECK(half_days BETWEEN 1 AND 732),
    PRIMARY KEY(company_id,request_id,bucket_id),
    FOREIGN KEY(company_id,request_id) REFERENCES leave_requests(company_id,id),
    FOREIGN KEY(company_id,bucket_id) REFERENCES leave_carry_buckets(company_id,id)
);

CREATE TABLE leave_carry_movements (
    company_id uuid NOT NULL,id uuid NOT NULL,bucket_id uuid NOT NULL,
    version bigint NOT NULL CHECK(version BETWEEN 1 AND 1000000000),
    effect varchar(12) NOT NULL CHECK(effect IN ('RESERVE','CONSUME','RELEASE','REFUND','EXPIRE','TRANSFER','DEDUCT')),
    half_days integer NOT NULL CHECK(half_days BETWEEN 1 AND 732),ledger_id uuid NOT NULL,request_id uuid,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,bucket_id,version),UNIQUE(company_id,bucket_id,ledger_id),
    UNIQUE(company_id,bucket_id,request_id,effect),
    FOREIGN KEY(company_id,bucket_id) REFERENCES leave_carry_buckets(company_id,id),
    FOREIGN KEY(company_id,ledger_id) REFERENCES leave_ledger(company_id,id) DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY(company_id,request_id,bucket_id) REFERENCES leave_carry_allocations(company_id,request_id,bucket_id) DEFERRABLE INITIALLY DEFERRED,
    CHECK((effect IN ('RESERVE','CONSUME','RELEASE','REFUND'))=(request_id IS NOT NULL))
);
CREATE INDEX leave_carry_movement_ledger ON leave_carry_movements(company_id,ledger_id);

-- Keep the public EXPIRE kind; its source is either a closing or a carry movement.
ALTER TABLE leave_ledger ADD COLUMN carry_expiry_id uuid;
ALTER TABLE leave_ledger ALTER COLUMN year_closing_id SET EXPRESSION AS
    (CASE WHEN kind IN ('CARRY_OUT','CARRY_IN','EXPIRE') AND carry_expiry_id IS NULL THEN source_id END);
ALTER TABLE leave_ledger ADD CONSTRAINT leave_ledger_carry_expiry
    FOREIGN KEY(company_id,carry_expiry_id) REFERENCES leave_carry_movements(company_id,id);
ALTER TABLE leave_ledger ADD CONSTRAINT leave_ledger_carry_expiry_kind
    CHECK(carry_expiry_id IS NULL OR (kind='EXPIRE' AND source_id=carry_expiry_id AND request_id IS NULL));

CREATE FUNCTION protect_leave_carry_bucket() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c leave_year_closings; months integer; boundary date; BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Carry accounting is retained' USING ERRCODE='23514'; END IF;
    IF TG_OP='UPDATE' THEN
        IF pg_trigger_depth()<2 OR NEW.version<>OLD.version+1
           OR (NEW.company_id,NEW.id,NEW.employment_id,NEW.type_id,NEW.balance_year,NEW.initial_half_days,NEW.company_timezone,NEW.validity_months,NEW.valid_through,NEW.expires_at)
              IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.type_id,OLD.balance_year,OLD.initial_half_days,OLD.company_timezone,OLD.validity_months,OLD.valid_through,OLD.expires_at)
            THEN RAISE EXCEPTION 'Carry balances are movement owned' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.id;
    months:=(c.policy_snapshot->'policy'->'accrual'->>'carryValidityMonths')::integer;
    IF c.id IS NULL OR NOT c.carry_tracking OR NEW.version<>0
        OR (NEW.employment_id,NEW.type_id,NEW.balance_year,NEW.initial_half_days,NEW.available_half_days,NEW.company_timezone,NEW.validity_months)
            IS DISTINCT FROM (c.employment_id,c.type_id,c.balance_year+1,c.carry_half_days,c.carry_half_days,c.company_timezone,months)
        OR NEW.reserved_half_days<>0 OR NEW.consumed_half_days<>0 OR NEW.expired_half_days<>0 OR NEW.transferred_half_days<>0 OR NEW.deducted_half_days<>0
        THEN RAISE EXCEPTION 'Carry award differs from its closing' USING ERRCODE='23514'; END IF;
    IF months IS NOT NULL THEN
        boundary:=(make_date(NEW.balance_year,1,1)+make_interval(months=>months))::date;
        IF NEW.valid_through IS DISTINCT FROM boundary-1 OR NEW.expires_at IS DISTINCT FROM boundary::timestamp AT TIME ZONE NEW.company_timezone
           OR c.recorded_at>=NEW.expires_at
            THEN RAISE EXCEPTION 'Carry validity differs from its policy' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_carry_bucket_protected BEFORE INSERT OR UPDATE OR DELETE ON leave_carry_buckets
    FOR EACH ROW EXECUTE FUNCTION protect_leave_carry_bucket();

CREATE FUNCTION project_leave_carry_movement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE updated uuid; BEGIN
    UPDATE leave_carry_buckets SET
        available_half_days=available_half_days+NEW.half_days*CASE NEW.effect WHEN 'RESERVE' THEN -1 WHEN 'RELEASE' THEN 1 WHEN 'REFUND' THEN 1 WHEN 'EXPIRE' THEN -1 WHEN 'TRANSFER' THEN -1 WHEN 'DEDUCT' THEN -1 ELSE 0 END,
        reserved_half_days=reserved_half_days+NEW.half_days*CASE NEW.effect WHEN 'RESERVE' THEN 1 WHEN 'CONSUME' THEN -1 WHEN 'RELEASE' THEN -1 ELSE 0 END,
        consumed_half_days=consumed_half_days+NEW.half_days*CASE NEW.effect WHEN 'CONSUME' THEN 1 WHEN 'REFUND' THEN -1 ELSE 0 END,
        expired_half_days=expired_half_days+CASE WHEN NEW.effect='EXPIRE' THEN NEW.half_days ELSE 0 END,
        transferred_half_days=transferred_half_days+CASE WHEN NEW.effect='TRANSFER' THEN NEW.half_days ELSE 0 END,
        deducted_half_days=deducted_half_days+CASE WHEN NEW.effect='DEDUCT' THEN NEW.half_days ELSE 0 END,
        version=NEW.version
        WHERE company_id=NEW.company_id AND id=NEW.bucket_id AND version=NEW.version-1 RETURNING id INTO updated;
    IF updated IS NULL THEN RAISE EXCEPTION 'Carry balance changed' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER leave_carry_movement_projection AFTER INSERT ON leave_carry_movements
    FOR EACH ROW EXECUTE FUNCTION project_leave_carry_movement();

CREATE FUNCTION validate_leave_carry_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE q leave_requests; b leave_carry_buckets; eligible integer; BEGIN
    SELECT * INTO q FROM leave_requests WHERE company_id=NEW.company_id AND id=NEW.request_id;
    SELECT * INTO b FROM leave_carry_buckets WHERE company_id=NEW.company_id AND id=NEW.bucket_id;
    IF q.id IS NULL OR b.id IS NULL OR q.version<>0 OR q.status<>'PENDING' OR q.carry_allocation_count=0
       OR (q.employment_id,q.type_id) IS DISTINCT FROM (b.employment_id,b.type_id)
       OR (b.expires_at IS NOT NULL AND q.submitted_at>=b.expires_at)
       OR NEW.half_days>b.initial_half_days
       OR q.submitted_at<(SELECT recorded_at FROM leave_year_closings WHERE company_id=b.company_id AND id=b.id)
        THEN RAISE EXCEPTION 'Carry funding differs from its request' USING ERRCODE='23514'; END IF;
    IF jsonb_array_length(q.days)>366 THEN RAISE EXCEPTION 'Carry funding date limit' USING ERRCODE='23514'; END IF;
    SELECT coalesce(sum(CASE d->>'portion' WHEN 'FULL' THEN 2 WHEN 'FIRST_HALF' THEN 1 WHEN 'SECOND_HALF' THEN 1 ELSE 0 END),0) INTO eligible
        FROM jsonb_array_elements(q.days) d WHERE extract(year FROM (d->>'workDate')::date)=b.balance_year
        AND (b.valid_through IS NULL OR (d->>'workDate')::date<=b.valid_through);
    IF NEW.half_days>eligible THEN RAISE EXCEPTION 'Carry funding exceeds eligible work dates' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_carry_allocation_scope BEFORE INSERT ON leave_carry_allocations
    FOR EACH ROW EXECUTE FUNCTION validate_leave_carry_allocation();

CREATE FUNCTION check_leave_carry_movement() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_carry_buckets; l leave_ledger; a leave_carry_allocations; BEGIN
    SELECT * INTO b FROM leave_carry_buckets WHERE company_id=NEW.company_id AND id=NEW.bucket_id;
    SELECT * INTO l FROM leave_ledger WHERE company_id=NEW.company_id AND id=NEW.ledger_id;
    IF l.id IS NULL OR (l.employment_id,l.type_id,l.balance_year,l.request_id) IS DISTINCT FROM (b.employment_id,b.type_id,b.balance_year,NEW.request_id)
        THEN RAISE EXCEPTION 'Carry movement differs from its ledger scope' USING ERRCODE='23514'; END IF;
    IF NEW.effect IN ('RESERVE','CONSUME','RELEASE','REFUND') THEN
        SELECT * INTO a FROM leave_carry_allocations WHERE company_id=NEW.company_id AND request_id=NEW.request_id AND bucket_id=NEW.bucket_id;
        IF a.request_id IS NULL OR a.half_days<>NEW.half_days OR l.kind<>NEW.effect
            OR NOT (CASE NEW.effect
              WHEN 'RESERVE' THEN l.available_delta<0 AND l.reserved_delta=-l.available_delta AND l.consumed_delta=0 AND NEW.half_days<=l.reserved_delta
              WHEN 'CONSUME' THEN l.reserved_delta<0 AND l.consumed_delta=-l.reserved_delta AND l.available_delta=0 AND NEW.half_days<=l.consumed_delta
              WHEN 'RELEASE' THEN l.reserved_delta<0 AND l.available_delta=-l.reserved_delta AND l.consumed_delta=0 AND NEW.half_days<=l.available_delta
              WHEN 'REFUND' THEN l.consumed_delta<0 AND l.available_delta=-l.consumed_delta AND l.reserved_delta=0 AND NEW.half_days<=l.available_delta END)
            THEN RAISE EXCEPTION 'Carry movement differs from original request funding' USING ERRCODE='23514'; END IF;
        IF NOT EXISTS(SELECT 1 FROM leave_carry_movements WHERE company_id=NEW.company_id AND bucket_id=NEW.bucket_id AND request_id=NEW.request_id AND effect='RESERVE')
            OR (NEW.effect IN ('CONSUME','REFUND') AND EXISTS(SELECT 1 FROM leave_carry_movements WHERE company_id=NEW.company_id AND bucket_id=NEW.bucket_id AND request_id=NEW.request_id AND effect='RELEASE'))
            OR (NEW.effect='RELEASE' AND EXISTS(SELECT 1 FROM leave_carry_movements WHERE company_id=NEW.company_id AND bucket_id=NEW.bucket_id AND request_id=NEW.request_id AND effect IN ('CONSUME','REFUND')))
            OR (NEW.effect='REFUND' AND NOT EXISTS(SELECT 1 FROM leave_carry_movements WHERE company_id=NEW.company_id AND bucket_id=NEW.bucket_id AND request_id=NEW.request_id AND effect='CONSUME'))
            THEN RAISE EXCEPTION 'Carry request funding transition is invalid' USING ERRCODE='23514'; END IF;
        IF NEW.effect IN ('RELEASE','REFUND') AND b.expires_at IS NOT NULL AND l.recorded_at>=b.expires_at AND b.available_half_days<>0
            THEN RAISE EXCEPTION 'Expired returned carry must be settled' USING ERRCODE='23514'; END IF;
    ELSIF l.reserved_delta<>0 OR l.consumed_delta<>0 OR NEW.half_days::bigint>-(l.available_delta::bigint)
        OR (NEW.effect='DEDUCT' AND l.kind<>'ADJUSTMENT')
        OR (NEW.effect='TRANSFER' AND l.kind<>'CARRY_OUT')
        OR (NEW.effect='EXPIRE' AND l.kind<>'EXPIRE')
        OR (l.carry_expiry_id IS NOT NULL AND (NEW.effect<>'EXPIRE' OR l.carry_expiry_id<>NEW.id OR b.expires_at IS NULL OR l.recorded_at<b.expires_at OR NEW.half_days<>-l.available_delta))
        THEN RAISE EXCEPTION 'Carry debit differs from its ledger effect' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_carry_movement_complete AFTER INSERT ON leave_carry_movements
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_movement();

CREATE FUNCTION check_leave_carry_funding() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a leave_carry_allocations; BEGIN
    IF TG_TABLE_NAME='leave_requests' THEN
        IF NEW.carry_allocation_count<>(SELECT count(*) FROM leave_carry_allocations WHERE company_id=NEW.company_id AND request_id=NEW.id)
            THEN RAISE EXCEPTION 'Carry request funding is incomplete' USING ERRCODE='23514'; END IF;
    ELSIF TG_TABLE_NAME='leave_carry_allocations' THEN
        IF (SELECT count(*) FROM leave_carry_allocations WHERE company_id=NEW.company_id AND request_id=NEW.request_id)
            IS DISTINCT FROM (SELECT carry_allocation_count::bigint FROM leave_requests WHERE company_id=NEW.company_id AND id=NEW.request_id)
            THEN RAISE EXCEPTION 'Carry request funding shape changed' USING ERRCODE='23514'; END IF;
        IF NOT EXISTS(SELECT 1 FROM leave_carry_movements WHERE company_id=NEW.company_id AND bucket_id=NEW.bucket_id AND request_id=NEW.request_id AND effect='RESERVE' AND half_days=NEW.half_days)
            THEN RAISE EXCEPTION 'Carry allocation was not reserved' USING ERRCODE='23514'; END IF;
    ELSIF NEW.request_id IS NOT NULL AND NEW.kind IN ('RESERVE','CONSUME','RELEASE','REFUND') THEN
        SELECT c.* INTO a FROM leave_carry_allocations c JOIN leave_carry_buckets b ON b.company_id=c.company_id AND b.id=c.bucket_id
            WHERE c.company_id=NEW.company_id AND c.request_id=NEW.request_id AND b.balance_year=NEW.balance_year;
        IF a.request_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM leave_carry_movements WHERE company_id=NEW.company_id AND bucket_id=a.bucket_id AND ledger_id=NEW.id AND effect=NEW.kind AND half_days=a.half_days)
            THEN RAISE EXCEPTION 'Leave ledger lost original carry funding' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_request_carry_complete AFTER INSERT ON leave_requests
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_funding();
CREATE CONSTRAINT TRIGGER leave_carry_allocation_complete AFTER INSERT ON leave_carry_allocations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_funding();
CREATE CONSTRAINT TRIGGER leave_carry_ledger_funding AFTER INSERT ON leave_ledger
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_funding();

CREATE FUNCTION check_leave_carry_balance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE b leave_carry_buckets; a leave_accounts; BEGIN
    SELECT * INTO b FROM leave_carry_buckets WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year;
    IF b.id IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO a FROM leave_accounts WHERE company_id=b.company_id AND employment_id=b.employment_id AND type_id=b.type_id AND balance_year=b.balance_year;
    IF a.id IS NULL OR b.available_half_days>a.available_half_days OR b.reserved_half_days>a.reserved_half_days OR b.consumed_half_days>a.consumed_half_days
       OR (a.closing_id IS NOT NULL AND (b.available_half_days<>0 OR b.reserved_half_days<>0))
        THEN RAISE EXCEPTION 'Carry balance exceeds its ledger account' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_carry_balance_complete AFTER INSERT OR UPDATE ON leave_carry_buckets
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_balance();
CREATE CONSTRAINT TRIGGER leave_account_carry_complete AFTER INSERT OR UPDATE ON leave_accounts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_balance();

CREATE FUNCTION check_leave_carry_award() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.carry_tracking AND NEW.carry_half_days>0 AND NOT EXISTS(SELECT 1 FROM leave_carry_buckets WHERE company_id=NEW.company_id AND id=NEW.id)
        THEN RAISE EXCEPTION 'Closing carry award is missing' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_closing_carry_complete AFTER INSERT ON leave_year_closings
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_carry_award();

CREATE OR REPLACE FUNCTION validate_leave_year_closing() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a leave_accounts; d leave_accounts; p leave_type_revisions; months integer; carry_limit integer; BEGIN
    SELECT * INTO a FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year;
    SELECT * INTO d FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year+1;
    SELECT * INTO p FROM leave_type_revisions WHERE company_id=NEW.company_id AND type_id=NEW.type_id AND revision=NEW.policy_revision;
    IF (NEW.policy_snapshot->>'typeId') IS DISTINCT FROM NEW.type_id::text OR (NEW.policy_snapshot->>'revision') IS DISTINCT FROM NEW.policy_revision::text
        OR (NEW.policy_snapshot->'policy') IS DISTINCT FROM (p.details || jsonb_build_object('attachmentRequired',coalesce(p.details->'attachmentRequired','false'::jsonb),'accrual',p.details->'accrual'))
        OR (NEW.policy_snapshot->>'code') IS DISTINCT FROM (SELECT code FROM leave_types WHERE company_id=NEW.company_id AND id=NEW.type_id)
        THEN RAISE EXCEPTION 'Leave policy evidence is inconsistent' USING ERRCODE='23514'; END IF;
    months:=(p.details->'accrual'->>'carryValidityMonths')::integer;
    carry_limit:=coalesce((p.details->'accrual'->>'carryLimitHalfDays')::integer,0);
    IF (months IS NOT NULL AND (months NOT BETWEEN 1 AND 12 OR NOT NEW.carry_tracking))
       OR (NEW.carry_tracking AND NEW.company_timezone IS DISTINCT FROM (SELECT timezone FROM companies WHERE id=NEW.company_id))
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

CREATE OR REPLACE FUNCTION validate_leave_accounting_ledger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p leave_accrual_postings; c leave_year_closings; a leave_accounts; m leave_carry_movements; b leave_carry_buckets; BEGIN
    IF NEW.kind='GRANT' THEN
        SELECT * INTO p FROM leave_accrual_postings WHERE company_id=NEW.company_id AND id=NEW.source_id;
        IF p.id IS NULL OR (p.employment_id,p.type_id,p.balance_year,p.half_days,p.actor_id) IS DISTINCT FROM (NEW.employment_id,NEW.type_id,NEW.balance_year,NEW.available_delta,NEW.actor_id) OR NEW.reserved_delta<>0 OR NEW.consumed_delta<>0
            THEN RAISE EXCEPTION 'Invalid leave grant effect' USING ERRCODE='23514'; END IF;
        INSERT INTO leave_accounts(company_id,employment_id,type_id,balance_year) VALUES(NEW.company_id,NEW.employment_id,NEW.type_id,NEW.balance_year) ON CONFLICT DO NOTHING;
        SELECT * INTO a FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year FOR UPDATE;
        IF a.closing_id IS NOT NULL OR a.version<>p.balance_version THEN RAISE EXCEPTION 'Leave grant balance changed' USING ERRCODE='23514'; END IF;
    ELSIF NEW.carry_expiry_id IS NOT NULL THEN
        SELECT * INTO m FROM leave_carry_movements WHERE company_id=NEW.company_id AND id=NEW.carry_expiry_id;
        SELECT * INTO b FROM leave_carry_buckets WHERE company_id=NEW.company_id AND id=m.bucket_id;
        IF m.id IS NULL OR b.id IS NULL OR m.effect<>'EXPIRE' OR m.ledger_id<>NEW.id OR NEW.kind<>'EXPIRE'
           OR (NEW.employment_id,NEW.type_id,NEW.balance_year) IS DISTINCT FROM (b.employment_id,b.type_id,b.balance_year)
           OR NEW.source_id<>m.id OR NEW.available_delta<>-m.half_days OR NEW.reserved_delta<>0 OR NEW.consumed_delta<>0
           OR b.expires_at IS NULL OR NEW.recorded_at<b.expires_at
            THEN RAISE EXCEPTION 'Invalid carried credit expiry effect' USING ERRCODE='23514'; END IF;
    ELSIF NEW.kind IN ('CARRY_OUT','CARRY_IN','EXPIRE') THEN
        SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.source_id;
        IF c.id IS NULL OR (c.employment_id,c.type_id,c.actor_id) IS DISTINCT FROM (NEW.employment_id,NEW.type_id,NEW.actor_id) OR NEW.reserved_delta<>0 OR NEW.consumed_delta<>0 OR NOT
            ((NEW.kind='CARRY_OUT' AND NEW.balance_year=c.balance_year AND NEW.available_delta=-c.carry_half_days AND c.carry_half_days>0)
             OR (NEW.kind='CARRY_IN' AND NEW.balance_year=c.balance_year+1 AND NEW.available_delta=c.carry_half_days AND c.carry_half_days>0)
             OR (NEW.kind='EXPIRE' AND NEW.balance_year=c.balance_year AND NEW.available_delta=-c.expire_half_days AND c.expire_half_days>0))
            THEN RAISE EXCEPTION 'Invalid leave closing effect' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;

DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_carry_buckets','leave_carry_allocations','leave_carry_movements'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['leave_carry_allocations','leave_carry_movements'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
