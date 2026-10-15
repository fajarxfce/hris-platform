package dev.fajar.hris.payroll.data.models

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class PayrollAmendmentCandidateRow(
    val companyId: UUID,
    val assessmentId: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: LocalDate,
    val revision: Long,
    val publishedAt: OffsetDateTime,
)
