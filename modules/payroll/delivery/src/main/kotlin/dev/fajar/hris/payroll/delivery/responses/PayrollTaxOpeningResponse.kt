package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollTaxOpeningResponse(
    val id: UUID,
    val employeeId: UUID,
    val year: Int,
    val version: Long,
    val terms: PayrollTaxOpeningTermsResponse,
    val status: String,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: Instant,
    val reason: String,
)
