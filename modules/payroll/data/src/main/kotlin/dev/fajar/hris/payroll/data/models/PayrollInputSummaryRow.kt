package dev.fajar.hris.payroll.data.models

import java.time.*
import java.util.UUID

data class PayrollInputSummaryRow(
    val id: UUID,
    val employeeId: UUID,
    val month: LocalDate,
    val version: Long,
    val status: String,
    val preparedBy: UUID,
    val verifiedBy: UUID?,
    val recordedAt: OffsetDateTime,
    val reason: String,
)
