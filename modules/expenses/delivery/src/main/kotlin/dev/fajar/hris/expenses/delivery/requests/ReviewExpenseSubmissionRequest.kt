package dev.fajar.hris.expenses.delivery.requests

import dev.fajar.hris.expenses.domain.entities.ExpenseDecision

data class ReviewExpenseSubmissionRequest(
    val expectedVersion: Long,
    val expectedApprovalVersion: Long,
    val decision: ExpenseDecision,
    val reason: String = "",
    val acknowledgeDuplicates: Boolean = false,
)
