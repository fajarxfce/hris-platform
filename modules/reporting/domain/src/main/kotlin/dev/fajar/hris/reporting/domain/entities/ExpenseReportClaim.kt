package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ExpenseReportClaim(
    val id: UUID,
    val submissionId: UUID,
    val submissionNumber: Int,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val status: ExpenseClaimStatus,
    val submittedAt: Instant,
    val version: Long,
    val lines: Long,
    val amount: BigDecimal,
    val submissionAmount: BigDecimal,
)
