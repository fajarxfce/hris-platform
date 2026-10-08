CREATE TABLE employment_revision_cancellations (
    company_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    revision bigint NOT NULL CHECK(revision>0),
    actor_id uuid NOT NULL REFERENCES accounts(id),
    reason varchar(1000) NOT NULL CHECK(length(trim(reason))>0),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(company_id,employment_id,revision),
    FOREIGN KEY(company_id,employment_id,revision) REFERENCES employment_revisions(company_id,employment_id,revision)
);
ALTER TABLE employment_revision_cancellations ENABLE ROW LEVEL SECURITY;
ALTER TABLE employment_revision_cancellations FORCE ROW LEVEL SECURITY;
CREATE POLICY cancellation_scope ON employment_revision_cancellations USING(company_id=current_company_id())
    WITH CHECK(company_id=current_company_id() AND actor_id=current_actor_id());
CREATE TRIGGER immutable_employment_cancellation BEFORE UPDATE OR DELETE ON employment_revision_cancellations
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();

CREATE OR REPLACE FUNCTION employees_at(p_company uuid,p_date date)
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
            AND NOT EXISTS(SELECT 1 FROM employment_revision_cancellations c
                WHERE c.company_id=h.company_id AND c.employment_id=h.employment_id AND c.revision=h.revision)
        ORDER BY h.effective_from DESC,h.revision DESC LIMIT 1
    ) r ON true
    LEFT JOIN employments m ON m.company_id=e.company_id AND m.id=r.manager_id
    LEFT JOIN persons mp ON mp.id=m.person_id
    WHERE e.company_id=p_company
$$;
