package dev.fajar.hris.payroll.data.models

import java.time.LocalDate
import java.util.UUID

data class PayrollTaxAssessmentRow(
    val id: UUID,
    val employeeId: UUID,
    val month: LocalDate,
    val openingId: UUID,
    val openingRevision: Long,
    val registration: String,
    val input: String,
    val calculation: String,
)
