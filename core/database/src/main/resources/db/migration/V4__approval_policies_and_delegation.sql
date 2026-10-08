CREATE TABLE approval_templates (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,name varchar(200) NOT NULL,
    kind varchar(32) NOT NULL,active boolean NOT NULL DEFAULT true,version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,id)
);
CREATE TABLE approval_template_revisions (
    company_id uuid NOT NULL,template_id uuid NOT NULL,revision bigint NOT NULL,effective_from date NOT NULL,
    category varchar(80),minimum_amount numeric(20,2) NOT NULL DEFAULT 0 CHECK(minimum_amount>=0),
    stages jsonb NOT NULL CHECK(jsonb_typeof(stages)='array'),actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,template_id,revision),
    FOREIGN KEY(company_id,template_id) REFERENCES approval_templates(company_id,id)
);
CREATE INDEX approval_template_date ON approval_template_revisions(company_id,template_id,effective_from DESC,revision DESC);
CREATE TRIGGER immutable_approval_template BEFORE UPDATE OR DELETE ON approval_template_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TABLE approval_requests (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,kind varchar(32) NOT NULL,resource_id uuid NOT NULL,
    author_id uuid NOT NULL REFERENCES accounts(id),requester_id uuid REFERENCES accounts(id),
    template_id uuid NOT NULL,template_revision bigint NOT NULL,stages jsonb NOT NULL CHECK(jsonb_array_length(stages) BETWEEN 1 AND 8),
    current_step integer NOT NULL DEFAULT 0 CHECK(current_step>=0),status varchar(32) NOT NULL CHECK(status IN ('PENDING','BLOCKED','APPROVED','REJECTED','CANCELLED')),
    version bigint NOT NULL DEFAULT 0,submitted_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,id),
    FOREIGN KEY(company_id,template_id,template_revision) REFERENCES approval_template_revisions(company_id,template_id,revision)
);
CREATE INDEX approval_pending ON approval_requests(company_id,status,id);
CREATE TABLE approval_decisions (
    company_id uuid NOT NULL,request_id uuid NOT NULL,step integer NOT NULL,actor_id uuid NOT NULL REFERENCES accounts(id),
    deciding_for uuid NOT NULL REFERENCES accounts(id),decision varchar(32) NOT NULL CHECK(decision IN ('APPROVE','REJECT')),
    reason varchar(1000) NOT NULL,decided_at timestamptz NOT NULL,
    PRIMARY KEY(company_id,request_id,step),FOREIGN KEY(company_id,request_id) REFERENCES approval_requests(company_id,id)
);
CREATE TRIGGER immutable_approval_decision BEFORE UPDATE OR DELETE ON approval_decisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TABLE approval_assignment_overrides (
    company_id uuid NOT NULL,request_id uuid NOT NULL,step integer NOT NULL,revision bigint NOT NULL,assignees uuid[] NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),reason varchar(1000) NOT NULL,recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,request_id,revision),FOREIGN KEY(company_id,request_id) REFERENCES approval_requests(company_id,id)
);
CREATE TRIGGER immutable_approval_override BEFORE UPDATE OR DELETE ON approval_assignment_overrides FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
CREATE TABLE approval_delegations (
    company_id uuid NOT NULL REFERENCES companies(id),id uuid NOT NULL,kind varchar(32) NOT NULL,
    from_account uuid NOT NULL REFERENCES accounts(id),to_account uuid NOT NULL REFERENCES accounts(id),
    valid_from timestamptz NOT NULL,valid_until timestamptz NOT NULL,active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,CHECK(valid_until>valid_from),CHECK(from_account<>to_account),PRIMARY KEY(company_id,id)
);
CREATE INDEX approval_delegation_recipient ON approval_delegations(company_id,to_account,kind) WHERE active;
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['approval_templates','approval_template_revisions','approval_requests','approval_decisions','approval_assignment_overrides','approval_delegations'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
        EXECUTE format('CREATE POLICY company_scope ON %I USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id())',t);
    END LOOP;
END $$;
CREATE FUNCTION protect_approval_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
    IF (NEW.company_id,NEW.id,NEW.kind,NEW.resource_id,NEW.author_id,NEW.requester_id,NEW.template_id,NEW.template_revision,NEW.stages,NEW.submitted_at)
        IS DISTINCT FROM (OLD.company_id,OLD.id,OLD.kind,OLD.resource_id,OLD.author_id,OLD.requester_id,OLD.template_id,OLD.template_revision,OLD.stages,OLD.submitted_at)
    THEN RAISE EXCEPTION 'Approval snapshots are immutable' USING ERRCODE='42501'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER immutable_approval_snapshot BEFORE UPDATE ON approval_requests FOR EACH ROW EXECUTE FUNCTION protect_approval_snapshot();
CREATE FUNCTION approval_assignees(p_company uuid,p_request uuid,p_step integer) RETURNS uuid[]
LANGUAGE sql STABLE SECURITY INVOKER AS $$
    SELECT coalesce(
        (SELECT a.assignees FROM approval_assignment_overrides a WHERE a.company_id=p_company AND a.request_id=p_request AND a.step=p_step ORDER BY a.revision DESC LIMIT 1),
        (SELECT array_agg(x::uuid) FROM approval_requests r,LATERAL jsonb_array_elements_text(r.stages->p_step->'assignees') x WHERE r.company_id=p_company AND r.id=p_request),
        '{}'::uuid[])
$$;

CREATE TRIGGER deny_approval_delete BEFORE DELETE ON approval_requests FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
