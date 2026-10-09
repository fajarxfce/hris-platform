package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollInputSummaryResponse(
    val id: UUID,
    val employeeId: UUID,
    val earningsMonth: String,
    val version: Long,
    val status: String,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: Instant,
    val reason: String,
)
