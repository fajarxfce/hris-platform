-- Transfers admit both companies without gaining policy mutation or group read access.
CREATE POLICY transfer_client_policy_head_read ON company_client_policy_heads FOR SELECT
    USING(current_company_id() IS NOT NULL AND company_id=current_secondary_company_id());
CREATE POLICY transfer_client_policy_revision_read ON company_client_policy_revisions FOR SELECT
    USING(current_company_id() IS NOT NULL AND company_id=current_secondary_company_id());
