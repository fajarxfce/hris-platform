package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class ExpenseClaimPayments(
    val claimId: UUID,
    val version: Long,
    val attempts: List<ExpensePaymentProgress>,
)
