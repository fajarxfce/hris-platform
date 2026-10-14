package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest

/** Submitted evidence and its recorded review outcomes, without live action authority. */
data class ExpenseSubmissionRecord(
    val submission: ExpenseSubmission,
    val approval: ApprovalRequest,
    val reviews: List<ExpenseReview>,
)
