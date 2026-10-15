package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollAmendmentCandidateResponse(
    val assessmentId: UUID,
    val employeeId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: String,
    val revision: Long,
    val publishedAt: Instant,
)
