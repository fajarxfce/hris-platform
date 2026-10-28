package dev.fajar.hris.reporting.data.datasources

// Only the latest submitted revision contributes; subsequent unsent draft edits cannot
// replace its evidence.
internal val matchingExpenseReportClaimsSql =
    """
    with matching_claims as (
        select c.id,c.employment_id,c.status,c.version,s.id as submission_id,s.number as submission_number,
            s.submitted_at,s.employee_number,s.employee_name,d.title,d.total_amount as submission_amount,
            count(*) as lines,sum(l.amount) as amount
        from expense_draft_lines l
        join expense_submitted_lines sl on sl.company_id=l.company_id and sl.claim_id=l.claim_id
            and sl.draft_revision=l.draft_revision and sl.line_id=l.id
        join expense_submissions s on s.company_id=sl.company_id and s.id=sl.submission_id
            and s.claim_id=sl.claim_id and s.draft_revision=sl.draft_revision
        join expense_claims c on c.company_id=s.company_id and c.id=s.claim_id and c.latest_submission_id=s.id
        join expense_drafts d on d.company_id=s.company_id and d.claim_id=s.claim_id and d.revision=s.draft_revision
        where l.company_id=? and l.occurred_on between ? and ?
        group by c.id,c.employment_id,c.status,c.version,s.id,s.number,s.submitted_at,s.employee_number,s.employee_name,d.title,d.total_amount
    )
    """
        .trimIndent()
