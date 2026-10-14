package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.core.domain.Page

/** Claim-owned facts read under the same aggregate and approval guards. */
data class ExpenseClaimRecord(
    val details: ExpenseClaimDetails,
    val submission: ExpenseSubmissionRecord?,
    val history: Page<ExpenseClaimChange>,
)
