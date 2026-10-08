CREATE INDEX approval_template_kind_cursor ON approval_templates(company_id,kind,id);
CREATE INDEX approval_delegation_sender_cursor ON approval_delegations(company_id,from_account,id);
CREATE INDEX approval_delegation_recipient_cursor ON approval_delegations(company_id,to_account,id);
