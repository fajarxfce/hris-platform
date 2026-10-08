package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpenseSubmission(
    val id: UUID,
    val claimId: UUID,
    val number: Int,
    val draftRevision: Int,
    val approvalId: UUID,
    val requesterId: UUID?,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val description: String,
    val totalAmount: BigDecimal,
    val lines: List<ExpenseSubmittedLine>,
    val makerIds: Set<UUID>,
    val submittedBy: UUID,
    val submittedAt: Instant,
    val reason: String,
)
