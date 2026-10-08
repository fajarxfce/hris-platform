package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest

data class ExpenseSubmissionDetails(
    val submission: ExpenseSubmission,
    val claim: ExpenseClaim,
    val approval: ApprovalRequest,
)
