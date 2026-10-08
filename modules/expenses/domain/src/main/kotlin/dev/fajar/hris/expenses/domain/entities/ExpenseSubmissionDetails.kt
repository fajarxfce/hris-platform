package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.approvals.domain.entities.ApprovalRequest

data class ExpenseSubmissionDetails(
    val submission: ExpenseSubmission,
    val claim: ExpenseClaim,
    val approval: ApprovalRequest,
    val reviews: List<ExpenseReview> = emptyList(),
    val duplicateReceiptDigests: Set<String> = emptySet(),
)
