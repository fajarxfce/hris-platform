package dev.fajar.hris.payroll.domain.entities

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class PayrollAmendmentSummary(
    val id: UUID,
    val firstAssessmentId: UUID,
    val firstMonth: YearMonth,
    val personId: UUID,
    val employee: PayrollEmployee,
    val version: Long,
    val status: PayrollAmendmentStatus,
    val action: PayrollAmendmentAction,
    val latestCalculationId: UUID?,
    val createdAt: Instant,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
