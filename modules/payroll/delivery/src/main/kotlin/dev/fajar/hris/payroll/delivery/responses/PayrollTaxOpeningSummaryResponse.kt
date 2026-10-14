package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollTaxOpeningSummaryResponse(
    val id: UUID,
    val employeeId: UUID,
    val year: Int,
    val version: Long,
    val status: String,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: Instant,
    val reason: String,
)
