package dev.fajar.hris.expenses.delivery.responses

import dev.fajar.hris.core.domain.Page

data class ExpenseClaimRecordResponse(
    val claim: ExpenseClaimResponse,
    val submission: ExpenseSubmissionRecordResponse?,
    val history: Page<ExpenseClaimChangeResponse>,
)
