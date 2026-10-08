CREATE TABLE organization_units (
    company_id uuid NOT NULL REFERENCES companies(id),
    id uuid NOT NULL,
    code varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    kind varchar(32) NOT NULL CHECK (kind IN ('BRANCH','DEPARTMENT','POSITION','COST_CENTER')),
    parent_id uuid,
    timezone varchar(80),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,id),
    UNIQUE(company_id,kind,code),
    FOREIGN KEY(company_id,parent_id) REFERENCES organization_units(company_id,id),
    CHECK (id <> parent_id)
);
CREATE INDEX units_parent ON organization_units(company_id,parent_id);
ALTER TABLE organization_units ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_units FORCE ROW LEVEL SECURITY;
CREATE POLICY units_scope ON organization_units USING (company_id=current_company_id()) WITH CHECK(company_id=current_company_id());

CREATE TABLE persons (
    id uuid PRIMARY KEY,
    owner_company_id uuid NOT NULL REFERENCES companies(id),
    account_id uuid UNIQUE REFERENCES accounts(id),
    legal_name varchar(200) NOT NULL,
    birth_date date,
    nationality char(2) NOT NULL,
    email varchar(254),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE employments (
    company_id uuid NOT NULL REFERENCES companies(id),
    id uuid NOT NULL,
    person_id uuid NOT NULL REFERENCES persons(id),
    employee_number varchar(32) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,id),
    UNIQUE(company_id,employee_number)
);
CREATE INDEX employment_person ON employments(person_id,company_id);
ALTER TABLE employments ENABLE ROW LEVEL SECURITY;
ALTER TABLE employments FORCE ROW LEVEL SECURITY;
CREATE POLICY employment_scope ON employments USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
ALTER TABLE persons ENABLE ROW LEVEL SECURITY;
ALTER TABLE persons FORCE ROW LEVEL SECURITY;
CREATE POLICY person_scope ON persons USING(owner_company_id=current_company_id() OR EXISTS(
    SELECT 1 FROM employments e WHERE e.person_id=persons.id AND e.company_id=current_company_id()
)) WITH CHECK(owner_company_id=current_company_id());

CREATE TABLE employment_revisions (
    company_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    revision bigint NOT NULL,
    effective_from date NOT NULL,
    contract_kind varchar(32) NOT NULL CHECK(contract_kind IN ('PERMANENT','FIXED_TERM')),
    start_date date NOT NULL,
    end_date date,
    status varchar(32) NOT NULL CHECK(status IN ('PROBATION','ACTIVE','SUSPENDED','ENDED')),
    branch_id uuid,
    department_id uuid,
    position_id uuid,
    cost_center_id uuid,
    manager_id uuid,
    actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,employment_id,revision),
    FOREIGN KEY(company_id,employment_id) REFERENCES employments(company_id,id),
    FOREIGN KEY(company_id,branch_id) REFERENCES organization_units(company_id,id),
    FOREIGN KEY(company_id,department_id) REFERENCES organization_units(company_id,id),
    FOREIGN KEY(company_id,position_id) REFERENCES organization_units(company_id,id),
    FOREIGN KEY(company_id,cost_center_id) REFERENCES organization_units(company_id,id),
    FOREIGN KEY(company_id,manager_id) REFERENCES employments(company_id,id),
    CHECK(end_date IS NULL OR end_date>=start_date),
    CHECK(manager_id IS NULL OR manager_id<>employment_id)
);
CREATE INDEX employment_as_of ON employment_revisions(company_id,employment_id,effective_from DESC,revision DESC);
ALTER TABLE employment_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE employment_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY revision_scope ON employment_revisions USING(company_id=current_company_id()) WITH CHECK(company_id=current_company_id());
CREATE FUNCTION deny_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Historical records are immutable' USING ERRCODE='42501'; END $$;
CREATE TRIGGER immutable_employment_history BEFORE UPDATE OR DELETE ON employment_revisions FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE FUNCTION employees_at(p_company uuid,p_date date)
RETURNS TABLE(company_id uuid,id uuid,employee_number varchar,person_id uuid,account_id uuid,
    legal_name varchar,birth_date date,nationality char(2),email varchar,
    effective_from date,contract_kind varchar,start_date date,end_date date,status varchar,
    branch_id uuid,department_id uuid,position_id uuid,cost_center_id uuid,manager_id uuid,
    manager_account_id uuid,version bigint,applied_revision bigint)
LANGUAGE sql STABLE SECURITY INVOKER AS $$
    SELECT e.company_id,e.id,e.employee_number,p.id,p.account_id,p.legal_name,p.birth_date,p.nationality,p.email,
        r.effective_from,r.contract_kind,r.start_date,r.end_date,r.status,r.branch_id,r.department_id,r.position_id,
        r.cost_center_id,r.manager_id,mp.account_id,e.version,r.revision
    FROM employments e
    JOIN persons p ON p.id=e.person_id
    JOIN LATERAL (
        SELECT h.* FROM employment_revisions h WHERE h.company_id=e.company_id AND h.employment_id=e.id AND h.effective_from<=p_date
        ORDER BY h.effective_from DESC,h.revision DESC LIMIT 1
    ) r ON true
    LEFT JOIN employments m ON m.company_id=e.company_id AND m.id=r.manager_id
    LEFT JOIN persons mp ON mp.id=m.person_id
    WHERE e.company_id=p_company
$$;
