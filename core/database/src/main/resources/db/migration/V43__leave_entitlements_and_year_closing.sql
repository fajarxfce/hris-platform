-- The ledger remains the source of financial facts. Its projection supplies a
-- stable mobile identity, optimistic version, and closed-year write boundary.
CREATE TABLE leave_accounts (
    company_id uuid NOT NULL,id uuid NOT NULL DEFAULT gen_random_uuid(),employment_id uuid NOT NULL,type_id uuid NOT NULL,
    balance_year integer NOT NULL CHECK(balance_year BETWEEN 1900 AND 2200),
    available_half_days bigint NOT NULL DEFAULT 0 CHECK(available_half_days BETWEEN 0 AND 2147483647),
    reserved_half_days bigint NOT NULL DEFAULT 0 CHECK(reserved_half_days BETWEEN 0 AND 2147483647),
    consumed_half_days bigint NOT NULL DEFAULT 0 CHECK(consumed_half_days BETWEEN 0 AND 2147483647),
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 1000000000),closing_id uuid,
    PRIMARY KEY(company_id,employment_id,type_id,balance_year),UNIQUE(company_id,id),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,type_id) REFERENCES leave_types(company_id,id)
);
INSERT INTO leave_accounts(company_id,employment_id,type_id,balance_year,available_half_days,reserved_half_days,consumed_half_days,version)
    SELECT company_id,employment_id,type_id,balance_year,sum(available_delta),sum(reserved_delta),sum(consumed_delta),count(*)
    FROM leave_ledger GROUP BY company_id,employment_id,type_id,balance_year;

CREATE TABLE leave_accrual_years (
    company_id uuid NOT NULL,employment_id uuid NOT NULL,type_id uuid NOT NULL,balance_year integer NOT NULL CHECK(balance_year BETWEEN 1900 AND 2199),
    frequency varchar(12) NOT NULL CHECK(frequency IN ('MONTHLY','ANNUAL')),
    PRIMARY KEY(company_id,employment_id,type_id,balance_year),UNIQUE(company_id,employment_id,type_id,balance_year,frequency),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),FOREIGN KEY(company_id,type_id) REFERENCES leave_types(company_id,id)
);
CREATE TABLE leave_accrual_postings (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,type_id uuid NOT NULL,
    balance_year integer NOT NULL,period_key date NOT NULL,processed_month date NOT NULL,
    frequency varchar(12) NOT NULL CHECK(frequency IN ('MONTHLY','ANNUAL')),half_days integer NOT NULL CHECK(half_days BETWEEN 1 AND 732),
    eligible_from date NOT NULL,eligible_until date NOT NULL,
    policy_revision bigint NOT NULL,policy_snapshot jsonb NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object' AND pg_column_size(policy_snapshot)<=65536),
    employment_version bigint NOT NULL CHECK(employment_version>=0),balance_version bigint NOT NULL CHECK(balance_version BETWEEN 0 AND 999999999),
    actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,id,employment_id,type_id),UNIQUE(company_id,employment_id,type_id,period_key),
    FOREIGN KEY(company_id,employment_id,type_id,balance_year,frequency) REFERENCES leave_accrual_years(company_id,employment_id,type_id,balance_year,frequency),
    FOREIGN KEY(company_id,type_id,policy_revision) REFERENCES leave_type_revisions(company_id,type_id,revision),
    CHECK(extract(day FROM period_key)=1 AND extract(day FROM processed_month)=1 AND extract(year FROM processed_month)=balance_year AND extract(year FROM period_key)=balance_year),
    CHECK((frequency='ANNUAL' AND extract(month FROM period_key)=1 AND eligible_from=eligible_until) OR (frequency='MONTHLY' AND period_key=processed_month AND eligible_from=processed_month AND eligible_until=(processed_month+interval '1 month'-interval '1 day')::date)),
    CHECK(eligible_until>=eligible_from AND date_trunc('month',eligible_from)::date=processed_month AND date_trunc('month',eligible_until)::date=processed_month)
);
CREATE TABLE leave_year_closings (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,type_id uuid NOT NULL,balance_year integer NOT NULL CHECK(balance_year BETWEEN 1900 AND 2199),
    source_version bigint NOT NULL CHECK(source_version BETWEEN 0 AND 999999996),destination_version bigint NOT NULL CHECK(destination_version BETWEEN 0 AND 999999999),
    available_half_days integer NOT NULL CHECK(available_half_days>=0),consumed_half_days integer NOT NULL CHECK(consumed_half_days>=0),
    carry_half_days integer NOT NULL CHECK(carry_half_days BETWEEN 0 AND 732),expire_half_days integer NOT NULL CHECK(expire_half_days>=0),
    policy_revision bigint NOT NULL,policy_snapshot jsonb NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object' AND pg_column_size(policy_snapshot)<=65536),
    actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    PRIMARY KEY(company_id,id),UNIQUE(company_id,id,employment_id,type_id),UNIQUE(company_id,employment_id,type_id,balance_year),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),FOREIGN KEY(company_id,type_id,policy_revision) REFERENCES leave_type_revisions(company_id,type_id,revision),
    CHECK(carry_half_days::bigint+expire_half_days=available_half_days)
);
ALTER TABLE leave_accounts ADD CONSTRAINT leave_account_closing FOREIGN KEY(company_id,closing_id) REFERENCES leave_year_closings(company_id,id);
ALTER TABLE leave_ledger ADD COLUMN accrual_posting_id uuid GENERATED ALWAYS AS (CASE WHEN kind='GRANT' THEN source_id END) STORED;
ALTER TABLE leave_ledger ADD COLUMN year_closing_id uuid GENERATED ALWAYS AS (CASE WHEN kind IN ('CARRY_OUT','CARRY_IN','EXPIRE') THEN source_id END) STORED;
ALTER TABLE leave_ledger ADD CONSTRAINT leave_ledger_accrual FOREIGN KEY(company_id,accrual_posting_id,employment_id,type_id) REFERENCES leave_accrual_postings(company_id,id,employment_id,type_id);
ALTER TABLE leave_ledger ADD CONSTRAINT leave_ledger_closing FOREIGN KEY(company_id,year_closing_id,employment_id,type_id) REFERENCES leave_year_closings(company_id,id,employment_id,type_id);

CREATE FUNCTION protect_leave_account() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c leave_year_closings; BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Leave account history is retained' USING ERRCODE='23514'; END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 OR NEW.available_half_days<>0 OR NEW.reserved_half_days<>0 OR NEW.consumed_half_days<>0 OR NEW.closing_id IS NOT NULL
            THEN RAISE EXCEPTION 'Leave accounts start empty' USING ERRCODE='23514'; END IF;
        RETURN NEW;
    END IF;
    IF OLD.closing_id IS NOT NULL OR NEW.version<>OLD.version+1 OR (NEW.company_id,NEW.id,NEW.employment_id,NEW.type_id,NEW.balance_year) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.employment_id,OLD.type_id,OLD.balance_year)
        THEN RAISE EXCEPTION 'Leave account revision is invalid or closed' USING ERRCODE='23514'; END IF;
    IF NEW.closing_id IS NULL THEN
        IF pg_trigger_depth()<2 THEN RAISE EXCEPTION 'Leave balances are ledger owned' USING ERRCODE='23514'; END IF;
    ELSE
        SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.closing_id;
        IF c.id IS NULL OR (c.employment_id,c.type_id,c.balance_year) IS DISTINCT FROM (NEW.employment_id,NEW.type_id,NEW.balance_year)
            OR NEW.available_half_days<>0 OR NEW.reserved_half_days<>0 OR NEW.consumed_half_days<>c.consumed_half_days
            OR (NEW.available_half_days,NEW.reserved_half_days,NEW.consumed_half_days) IS DISTINCT FROM (OLD.available_half_days,OLD.reserved_half_days,OLD.consumed_half_days)
            OR NEW.version<>c.source_version+(CASE WHEN c.carry_half_days>0 THEN 1 ELSE 0 END)+(CASE WHEN c.expire_half_days>0 THEN 1 ELSE 0 END)+1
            THEN RAISE EXCEPTION 'Leave closing does not match its final account state' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_account_protected BEFORE INSERT OR UPDATE OR DELETE ON leave_accounts FOR EACH ROW EXECUTE FUNCTION protect_leave_account();
CREATE FUNCTION project_leave_ledger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE changed uuid; BEGIN
    INSERT INTO leave_accounts(company_id,employment_id,type_id,balance_year) VALUES(NEW.company_id,NEW.employment_id,NEW.type_id,NEW.balance_year) ON CONFLICT DO NOTHING;
    UPDATE leave_accounts SET available_half_days=available_half_days+NEW.available_delta,reserved_half_days=reserved_half_days+NEW.reserved_delta,consumed_half_days=consumed_half_days+NEW.consumed_delta,version=version+1
        WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year AND closing_id IS NULL RETURNING id INTO changed;
    IF changed IS NULL THEN RAISE EXCEPTION 'The leave year is closed' USING ERRCODE='23514'; END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER leave_ledger_projection AFTER INSERT ON leave_ledger FOR EACH ROW EXECUTE FUNCTION project_leave_ledger();

CREATE FUNCTION validate_leave_accrual_posting() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a leave_accounts; p leave_type_revisions; BEGIN
    SELECT * INTO a FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year;
    SELECT * INTO p FROM leave_type_revisions WHERE company_id=NEW.company_id AND type_id=NEW.type_id AND revision=NEW.policy_revision;
    IF (NEW.policy_snapshot->>'typeId') IS DISTINCT FROM NEW.type_id::text OR (NEW.policy_snapshot->>'revision') IS DISTINCT FROM NEW.policy_revision::text
        OR (NEW.policy_snapshot->'policy') IS DISTINCT FROM (p.details || jsonb_build_object('attachmentRequired',coalesce(p.details->'attachmentRequired','false'::jsonb),'accrual',p.details->'accrual'))
        OR (NEW.policy_snapshot->>'code') IS DISTINCT FROM (SELECT code FROM leave_types WHERE company_id=NEW.company_id AND id=NEW.type_id)
        THEN RAISE EXCEPTION 'Leave policy evidence is inconsistent' USING ERRCODE='23514'; END IF;
    IF coalesce(a.version,0)<>NEW.balance_version OR a.closing_id IS NOT NULL OR p.type_id IS NULL
        OR NEW.frequency IS DISTINCT FROM (p.details->'accrual'->>'frequency') OR NEW.half_days IS DISTINCT FROM (p.details->'accrual'->>'halfDaysPerPeriod')::integer
        THEN RAISE EXCEPTION 'Leave accrual snapshot is inconsistent' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_accrual_snapshot BEFORE INSERT ON leave_accrual_postings FOR EACH ROW EXECUTE FUNCTION validate_leave_accrual_posting();
CREATE FUNCTION validate_leave_year_closing() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a leave_accounts; d leave_accounts; p leave_type_revisions; BEGIN
    SELECT * INTO a FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year;
    SELECT * INTO d FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year+1;
    SELECT * INTO p FROM leave_type_revisions WHERE company_id=NEW.company_id AND type_id=NEW.type_id AND revision=NEW.policy_revision;
    IF (NEW.policy_snapshot->>'typeId') IS DISTINCT FROM NEW.type_id::text OR (NEW.policy_snapshot->>'revision') IS DISTINCT FROM NEW.policy_revision::text
        OR (NEW.policy_snapshot->'policy') IS DISTINCT FROM (p.details || jsonb_build_object('attachmentRequired',coalesce(p.details->'attachmentRequired','false'::jsonb),'accrual',p.details->'accrual'))
        OR (NEW.policy_snapshot->>'code') IS DISTINCT FROM (SELECT code FROM leave_types WHERE company_id=NEW.company_id AND id=NEW.type_id)
        THEN RAISE EXCEPTION 'Leave policy evidence is inconsistent' USING ERRCODE='23514'; END IF;
    IF p.type_id IS NULL OR a.closing_id IS NOT NULL OR coalesce(a.version,0)<>NEW.source_version OR coalesce(a.available_half_days,0)<>NEW.available_half_days OR coalesce(a.reserved_half_days,0)<>0 OR coalesce(a.consumed_half_days,0)<>NEW.consumed_half_days
        OR NEW.carry_half_days<>least(NEW.available_half_days,coalesce((p.details->'accrual'->>'carryLimitHalfDays')::integer,0))
        OR (NEW.carry_half_days>0 AND (d.closing_id IS NOT NULL OR coalesce(d.version,0)<>NEW.destination_version))
        THEN RAISE EXCEPTION 'Leave year closing snapshot is inconsistent' USING ERRCODE='23514'; END IF;
    IF EXISTS(SELECT 1 FROM leave_requests WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id
        AND status IN ('PENDING','CANCELLATION_PENDING') AND starts_on<=make_date(NEW.balance_year,12,31) AND ends_on>=make_date(NEW.balance_year,1,1))
        THEN RAISE EXCEPTION 'Unresolved leave requests prevent year closing' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER leave_year_closing_snapshot BEFORE INSERT ON leave_year_closings FOR EACH ROW EXECUTE FUNCTION validate_leave_year_closing();

CREATE FUNCTION check_leave_accounting_effects() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p leave_accrual_postings; c leave_year_closings; a leave_accounts; effects integer; BEGIN
    IF TG_TABLE_NAME='leave_accrual_postings' THEN
        SELECT * INTO p FROM leave_accrual_postings WHERE company_id=NEW.company_id AND id=NEW.id;
        SELECT count(*) INTO effects FROM leave_ledger WHERE company_id=p.company_id AND source_id=p.id AND kind='GRANT'
            AND employment_id=p.employment_id AND type_id=p.type_id AND balance_year=p.balance_year AND available_delta=p.half_days AND reserved_delta=0 AND consumed_delta=0 AND actor_id=p.actor_id;
        IF effects<>1 THEN RAISE EXCEPTION 'Leave accrual ledger effect is missing' USING ERRCODE='23514'; END IF;
    ELSE
        SELECT * INTO c FROM leave_year_closings WHERE company_id=NEW.company_id AND id=NEW.id;
        SELECT * INTO a FROM leave_accounts WHERE company_id=c.company_id AND employment_id=c.employment_id AND type_id=c.type_id AND balance_year=c.balance_year;
        IF a.closing_id IS DISTINCT FROM c.id THEN RAISE EXCEPTION 'Leave year account was not closed' USING ERRCODE='23514'; END IF;
        SELECT count(*) INTO effects FROM leave_ledger WHERE company_id=c.company_id AND source_id=c.id AND employment_id=c.employment_id AND type_id=c.type_id AND reserved_delta=0 AND consumed_delta=0 AND actor_id=c.actor_id AND
            ((kind='CARRY_OUT' AND balance_year=c.balance_year AND available_delta=-c.carry_half_days AND c.carry_half_days>0)
             OR (kind='CARRY_IN' AND balance_year=c.balance_year+1 AND available_delta=c.carry_half_days AND c.carry_half_days>0)
             OR (kind='EXPIRE' AND balance_year=c.balance_year AND available_delta=-c.expire_half_days AND c.expire_half_days>0));
        IF effects<>(CASE WHEN c.carry_half_days>0 THEN 2 ELSE 0 END)+(CASE WHEN c.expire_half_days>0 THEN 1 ELSE 0 END)
            THEN RAISE EXCEPTION 'Leave year closing ledger effects are incomplete' USING ERRCODE='23514'; END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER leave_accrual_complete AFTER INSERT ON leave_accrual_postings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_accounting_effects();
CREATE CONSTRAINT TRIGGER leave_year_closing_complete AFTER INSERT ON leave_year_closings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_leave_accounting_effects();
CREATE FUNCTION validate_leave_accounting_ledger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p leave_accrual_postings; c leave_year_closings; a leave_accounts; BEGIN
    IF NEW.kind='GRANT' THEN
        SELECT * INTO p FROM leave_accrual_postings WHERE company_id=NEW.company_id AND id=NEW.source_id;
        IF p.id IS NULL OR (p.employment_id,p.type_id,p.balance_year,p.half_days,p.actor_id) IS DISTINCT FROM (NEW.employment_id,NEW.type_id,NEW.balance_year,NEW.available_delta,NEW.actor_id) OR NEW.reserved_delta<>0 OR NEW.consumed_delta<>0
            THEN RAISE EXCEPTION 'Invalid leave grant effect' USING ERRCODE='23514'; END IF;
        INSERT INTO leave_accounts(company_id,employment_id,type_id,balance_year) VALUES(NEW.company_id,NEW.employment_id,NEW.type_id,NEW.balance_year) ON CONFLICT DO NOTHING;
        SELECT * INTO a FROM leave_accounts WHERE company_id=NEW.company_id AND employment_id=NEW.employment_id AND type_id=NEW.type_id AND balance_year=NEW.balance_year FOR UPDATE;
        IF a.closing_id IS NOT NULL OR a.version<>p.balance_version THEN RAISE EXCEPTION 'Leave grant balance changed' USING ERRCODE='23514'; END IF;
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
CREATE TRIGGER leave_accounting_ledger BEFORE INSERT ON leave_ledger FOR EACH ROW EXECUTE FUNCTION validate_leave_accounting_ledger();
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_accounts','leave_accrual_years','leave_accrual_postings','leave_year_closings'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['leave_accrual_years','leave_accrual_postings','leave_year_closings'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES'));
CREATE FUNCTION record_leave_balance_sync_change() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NEW.version>0 THEN
        INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
            VALUES(NEW.company_id,'LEAVE_BALANCES',NEW.id,NEW.employment_id,NEW.version,'UPSERT');
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER leave_balance_sync_change AFTER INSERT OR UPDATE ON leave_accounts FOR EACH ROW EXECUTE FUNCTION record_leave_balance_sync_change();
