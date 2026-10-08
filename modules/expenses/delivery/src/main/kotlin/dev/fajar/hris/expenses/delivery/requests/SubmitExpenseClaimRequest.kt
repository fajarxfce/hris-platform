package dev.fajar.hris.expenses.delivery.requests

import java.util.UUID

data class SubmitExpenseClaimRequest(
    val submissionId: UUID,
    val expectedVersion: Long,
    val reason: String,
)
