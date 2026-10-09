-- Immutable assessments are the source of employee payslips. No recalculation or duplicate salary JSON.
CREATE INDEX payroll_payslip_month ON payroll_assessments(company_id,tax_month DESC,id);

ALTER TABLE mobile_sync_changes DROP CONSTRAINT mobile_sync_changes_collection_check;
ALTER TABLE mobile_sync_changes ADD CONSTRAINT mobile_sync_changes_collection_check
    CHECK(collection IN ('EXPENSE_CLAIMS','LEAVE_REQUESTS','OVERTIME_REQUESTS','LEAVE_BALANCES','PAYSLIPS'));

-- One bounded payroll statement captures invalidations in the same publication transaction.
-- Existing assessments are discovered by bootstrap when PAYSLIPS changes the client's scope fingerprint.
CREATE FUNCTION record_payroll_payslip_sync_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation)
    SELECT company_id,'PAYSLIPS',id,employment_id,0,'UPSERT' FROM published_assessments;
    RETURN NULL;
END $$;
CREATE TRIGGER payroll_payslip_sync_changes AFTER INSERT ON payroll_assessments
    REFERENCING NEW TABLE AS published_assessments FOR EACH STATEMENT EXECUTE FUNCTION record_payroll_payslip_sync_changes();
