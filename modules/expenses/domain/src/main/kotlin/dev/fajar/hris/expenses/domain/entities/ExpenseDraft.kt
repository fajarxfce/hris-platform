package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpenseDraft(
    val claimId: UUID,
    val revision: Int,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val description: String,
    val totalAmount: BigDecimal,
    val lines: List<ExpenseLine>,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
