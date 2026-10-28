package dev.fajar.hris.reporting.data.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class ExpenseReportClaimRow(
    val id: UUID,
    val submissionId: UUID,
    val submissionNumber: Int,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val status: String,
    val submittedAt: OffsetDateTime,
    val version: Long,
    val lines: Long,
    val amount: BigDecimal,
    val submissionAmount: BigDecimal,
)
