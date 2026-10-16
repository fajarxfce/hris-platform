ALTER TABLE attendance_corrections ADD CONSTRAINT attendance_correction_reference
    UNIQUE(company_id, id, employment_id, work_date);

CREATE TABLE attendance_adjustments (
    company_id uuid NOT NULL,
    id uuid NOT NULL,
    job_id uuid NOT NULL,
    employment_id uuid NOT NULL,
    revision bigint NOT NULL CHECK(revision >= 1),
    previous_revision bigint,
    work_date date NOT NULL,
    correction_id uuid NOT NULL,
    actor_id uuid NOT NULL REFERENCES accounts(id),
    recorded_at timestamptz NOT NULL,
    reason varchar(1000) NOT NULL CHECK(length(btrim(reason)) > 0),
    payload jsonb NOT NULL CHECK(octet_length(payload::text) <= 262144),
    PRIMARY KEY(company_id, id),
    UNIQUE(company_id, job_id, employment_id, revision),
    UNIQUE(company_id, correction_id),
    FOREIGN KEY(company_id, job_id, employment_id)
        REFERENCES work_period_snapshots(company_id, job_id, employment_id),
    FOREIGN KEY(company_id, correction_id, employment_id, work_date)
        REFERENCES attendance_corrections(company_id, id, employment_id, work_date),
    FOREIGN KEY(company_id, job_id, employment_id, previous_revision)
        REFERENCES attendance_adjustments(company_id, job_id, employment_id, revision),
    CHECK((revision = 1 AND previous_revision IS NULL) OR
          (revision > 1 AND previous_revision IS NOT NULL AND previous_revision = revision - 1))
);

ALTER TABLE attendance_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_adjustments FORCE ROW LEVEL SECURITY;
CREATE POLICY company_scope ON attendance_adjustments
    USING(company_id = current_company_id()) WITH CHECK(company_id = current_company_id());
CREATE TRIGGER immutable_history BEFORE UPDATE OR DELETE ON attendance_adjustments
    FOR EACH ROW EXECUTE FUNCTION deny_history_mutation();
