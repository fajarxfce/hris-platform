ALTER TABLE attendance_adjustments ADD CONSTRAINT attendance_adjustment_employee_reference
    UNIQUE(company_id, id, employment_id);
ALTER TABLE attendance_adjustments ADD CONSTRAINT attendance_adjustment_work_reference
    UNIQUE(company_id, id, job_id, employment_id);

ALTER TABLE payroll_input_revisions ADD COLUMN work_adjustment_id uuid;
ALTER TABLE payroll_input_revisions ADD CONSTRAINT payroll_input_attendance_evidence
    FOREIGN KEY(company_id, work_adjustment_id, work_job_id, employment_id)
    REFERENCES attendance_adjustments(company_id, id, job_id, employment_id);
ALTER TABLE payroll_run_targets ADD COLUMN work_adjustment_id uuid;
ALTER TABLE payroll_run_targets ADD CONSTRAINT payroll_target_attendance_evidence
    FOREIGN KEY(company_id, work_adjustment_id, employment_id)
    REFERENCES attendance_adjustments(company_id, id, employment_id);

CREATE FUNCTION validate_payroll_input_attendance_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE latest uuid; previous uuid; BEGIN
    PERFORM 1 FROM work_periods
        WHERE company_id=NEW.company_id AND job_id=NEW.work_job_id AND month=NEW.earnings_month
        FOR SHARE;
    SELECT id INTO latest FROM attendance_adjustments
        WHERE company_id=NEW.company_id AND job_id=NEW.work_job_id AND employment_id=NEW.employment_id
        ORDER BY revision DESC LIMIT 1;
    IF NEW.work_adjustment_id IS DISTINCT FROM latest THEN
        RAISE EXCEPTION 'Payroll input must select current attendance evidence' USING ERRCODE='23514';
    END IF;
    IF NEW.status='VERIFIED' THEN
        SELECT work_adjustment_id INTO previous FROM payroll_input_revisions
            WHERE company_id=NEW.company_id AND input_id=NEW.input_id AND revision=NEW.revision-1;
        IF NEW.work_adjustment_id IS DISTINCT FROM previous THEN
            RAISE EXCEPTION 'Verification must preserve selected attendance evidence' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_input_attendance_evidence BEFORE INSERT ON payroll_input_revisions
    FOR EACH ROW EXECUTE FUNCTION validate_payroll_input_attendance_evidence();

CREATE FUNCTION validate_payroll_target_attendance_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE work_job uuid; latest uuid; BEGIN
    SELECT work_job_id INTO work_job FROM payroll_runs WHERE company_id=NEW.company_id AND id=NEW.run_id;
    PERFORM 1 FROM work_periods WHERE company_id=NEW.company_id AND job_id=work_job FOR SHARE;
    SELECT id INTO latest FROM attendance_adjustments
        WHERE company_id=NEW.company_id AND job_id=work_job AND employment_id=NEW.employment_id
        ORDER BY revision DESC LIMIT 1;
    IF NEW.work_adjustment_id IS DISTINCT FROM latest THEN
        RAISE EXCEPTION 'Payroll cutoff must retain current attendance evidence' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_target_attendance_evidence BEFORE INSERT ON payroll_run_targets
    FOR EACH ROW EXECUTE FUNCTION validate_payroll_target_attendance_evidence();

CREATE FUNCTION validate_payroll_result_attendance_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE selected uuid; input_source uuid; BEGIN
    IF NEW.facts IS NOT NULL THEN
        SELECT t.work_adjustment_id,i.work_adjustment_id INTO selected,input_source
            FROM payroll_run_targets t LEFT JOIN payroll_input_revisions i
                ON i.company_id=t.company_id AND i.input_id=t.input_id AND i.revision=t.input_revision
            WHERE t.company_id=NEW.company_id AND t.run_id=NEW.run_id AND t.ordinal=NEW.ordinal;
        IF (NEW.facts->>'workAdjustmentId')::uuid IS DISTINCT FROM selected OR
            (NEW.status='SUCCEEDED' AND input_source IS DISTINCT FROM selected) THEN
            RAISE EXCEPTION 'Payroll calculation must preserve selected attendance evidence' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payroll_result_attendance_evidence BEFORE INSERT ON payroll_run_results
    FOR EACH ROW EXECUTE FUNCTION validate_payroll_result_attendance_evidence();
