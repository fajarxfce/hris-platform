package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpensePayable(
    val claimId: UUID,
    val submissionId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val amount: BigDecimal,
    val approvedAt: Instant,
    val makerIds: Set<UUID>,
    val requesterId: UUID?,
    val currentAccountId: UUID?,
)
