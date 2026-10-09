package dev.fajar.hris.payroll.domain.entities

import java.time.*
import java.util.UUID

data class PayrollInputSummary(
    val id: UUID,
    val employeeId: UUID,
    val earningsMonth: YearMonth,
    val version: Long,
    val status: PayrollInputStatus,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: Instant,
    val reason: String,
)
