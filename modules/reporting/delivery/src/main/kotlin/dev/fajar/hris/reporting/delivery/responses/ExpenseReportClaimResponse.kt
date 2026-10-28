package dev.fajar.hris.reporting.delivery.responses

import java.time.Instant
import java.util.UUID

data class ExpenseReportClaimResponse(
    val id: UUID,
    val submissionId: UUID,
    val submissionNumber: Int,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val title: String,
    val status: String,
    val submittedAt: Instant,
    val version: Long,
    val lines: Long,
    val amount: String,
    val submissionAmount: String,
)
