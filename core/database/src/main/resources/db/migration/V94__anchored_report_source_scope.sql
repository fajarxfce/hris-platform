-- Report export transactions retain their anchor for owned staging/journal writes.
-- The explicit source selection still grants only SELECT on these five input tables.
-- Ordinary and transfer transactions clear the selection; mutation policies are unchanged.
DO $$ DECLARE t text; BEGIN
    FOREACH t IN ARRAY ARRAY['employments','employment_revisions','employment_revision_cancellations',
        'company_client_policy_heads','company_client_policy_revisions'] LOOP
        EXECUTE format('ALTER POLICY company_report_read ON %I USING(company_id=ANY(current_read_company_ids()))',t);
    END LOOP;
END $$;
