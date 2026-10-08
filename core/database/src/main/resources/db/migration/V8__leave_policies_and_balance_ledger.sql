CREATE TABLE leave_types (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,code varchar(32) NOT NULL,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,id),UNIQUE(company_id,code)
);
CREATE TABLE leave_type_revisions (
    company_id uuid NOT NULL,type_id uuid NOT NULL,revision bigint NOT NULL,effective_from date NOT NULL,details jsonb NOT NULL,active boolean NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,type_id,revision),FOREIGN KEY(company_id,type_id) REFERENCES leave_types(company_id,id)
);
CREATE INDEX leave_type_effective ON leave_type_revisions(company_id,type_id,effective_from DESC,revision DESC);
CREATE TABLE leave_ledger (
    company_id uuid NOT NULL,id uuid NOT NULL,employment_id uuid NOT NULL,type_id uuid NOT NULL,balance_year integer NOT NULL,
    kind varchar(24) NOT NULL CHECK(kind IN ('ADJUSTMENT','GRANT','RESERVE','CONSUME','RELEASE','REFUND','EXPIRE','CARRY_OUT','CARRY_IN')),
    source_id uuid NOT NULL,request_id uuid,available_delta integer NOT NULL,reserved_delta integer NOT NULL,consumed_delta integer NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),recorded_at timestamptz NOT NULL,reason varchar(1000) NOT NULL,
    PRIMARY KEY(company_id,id),FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,type_id) REFERENCES leave_types(company_id,id),
    UNIQUE(company_id,employment_id,type_id,balance_year,source_id,kind),
    CHECK(balance_year BETWEEN 1900 AND 2200),CHECK(available_delta<>0 OR reserved_delta<>0 OR consumed_delta<>0)
);
CREATE INDEX leave_balance ON leave_ledger(company_id,employment_id,type_id,balance_year,recorded_at DESC,id DESC);
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['leave_types','leave_type_revisions','leave_ledger'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['leave_type_revisions','leave_ledger'] LOOP
        EXECUTE format('CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION deny_history_mutation()',t);
    END LOOP;
END $$;
