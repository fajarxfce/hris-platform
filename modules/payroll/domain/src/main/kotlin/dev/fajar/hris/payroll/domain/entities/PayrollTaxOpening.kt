package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.util.UUID

data class PayrollTaxOpening(
    val id: UUID,
    val employeeId: UUID,
    val year: Int,
    val version: Long,
    val terms: PayrollTaxOpeningTerms,
    val status: PayrollTaxOpeningStatus,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: Instant,
    val reason: String,
)
