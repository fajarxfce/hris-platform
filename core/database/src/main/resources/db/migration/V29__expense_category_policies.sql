CREATE TABLE expense_categories (
    company_id uuid NOT NULL REFERENCES companies(id), id uuid NOT NULL,
    code varchar(32) NOT NULL CHECK(code ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    version bigint NOT NULL DEFAULT 0 CHECK(version BETWEEN 0 AND 999),
    PRIMARY KEY(company_id,id), UNIQUE(company_id,code)
);
CREATE TABLE expense_category_revisions (
    company_id uuid NOT NULL, category_id uuid NOT NULL, revision bigint NOT NULL CHECK(revision BETWEEN 0 AND 999),
    effective_from date NOT NULL CHECK(effective_from BETWEEN DATE '1900-01-01' AND DATE '2200-12-31'),
    name varchar(120) NOT NULL CHECK(length(trim(name))>0),
    maximum_line_amount numeric(14,2) NOT NULL CHECK(maximum_line_amount>0),
    maximum_claim_amount numeric(14,2) NOT NULL CHECK(maximum_claim_amount>=maximum_line_amount),
    receipt_required boolean NOT NULL, cost_center_required boolean NOT NULL,
    maximum_age_days integer NOT NULL CHECK(maximum_age_days BETWEEN 1 AND 366),
    allowed_contracts text[] NOT NULL CHECK(cardinality(allowed_contracts) BETWEEN 1 AND 2 AND allowed_contracts <@ ARRAY['PERMANENT','FIXED_TERM']::text[]),
    active boolean NOT NULL, actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0), recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(company_id,category_id,revision), FOREIGN KEY(company_id,category_id) REFERENCES expense_categories(company_id,id)
);
ALTER TABLE expense_categories ADD FOREIGN KEY(company_id,id,version) REFERENCES expense_category_revisions(company_id,category_id,revision) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX expense_category_effective ON expense_category_revisions(company_id,category_id,effective_from DESC,revision DESC);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['expense_categories','expense_category_revisions'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;
CREATE POLICY expense_policy_actor ON expense_category_revisions AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK(actor_id=current_actor_id());
CREATE TRIGGER immutable_expense_policy BEFORE UPDATE OR DELETE ON expense_category_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TRIGGER preserve_expense_category BEFORE DELETE ON expense_categories FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE FUNCTION protect_expense_category() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.version<>0 THEN RAISE EXCEPTION 'Expense categories must start at revision zero' USING ERRCODE='23514'; END IF;
    ELSIF (NEW.company_id,NEW.id,NEW.code) IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.code) OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Expense category identity is immutable and changes must be versioned' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER protect_expense_category BEFORE INSERT OR UPDATE ON expense_categories FOR EACH ROW EXECUTE FUNCTION protect_expense_category();
CREATE FUNCTION sequence_expense_policy() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF NOT EXISTS(SELECT 1 FROM expense_categories c WHERE c.company_id=NEW.company_id AND c.id=NEW.category_id AND c.version=NEW.revision) THEN
        RAISE EXCEPTION 'Expense policy revision must match its category version' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER sequence_expense_policy BEFORE INSERT ON expense_category_revisions FOR EACH ROW EXECUTE FUNCTION sequence_expense_policy();
