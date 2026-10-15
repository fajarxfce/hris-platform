package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** A retained employee/month reference for selecting an amendment's first assessment. */
data class PayrollAmendmentCandidate(
    val assessmentId: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: YearMonth,
    val revision: Long,
    val publishedAt: Instant,
)
