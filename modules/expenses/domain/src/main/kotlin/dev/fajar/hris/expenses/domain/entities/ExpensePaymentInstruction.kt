package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class ExpensePaymentInstruction(
    val id: UUID,
    val submissionId: UUID,
    val destination: ExpensePaymentDestination,
)
