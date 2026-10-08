package dev.fajar.hris.expenses.delivery.requests

import java.util.UUID

data class ExpensePaymentInstructionRequest(
    val id: UUID,
    val submissionId: UUID,
    val destination: ExpensePaymentDestinationRequest,
)
