-- Line transaction dates define reporting windows independently of claim creation or later draft edits.
CREATE INDEX expense_report_line_date ON expense_draft_lines(company_id,occurred_on,claim_id,draft_revision);
