-- Match the descending timestamp/UUID continuation used by the company audit explorer.
CREATE INDEX audit_company_recorded ON audit_entries(company_id,created_at DESC,id DESC);
DROP INDEX audit_company_created;
