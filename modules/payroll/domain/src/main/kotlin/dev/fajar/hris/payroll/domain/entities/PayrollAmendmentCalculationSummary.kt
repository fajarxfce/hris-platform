package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Failure
import java.time.Instant
import java.util.UUID

data class PayrollAmendmentCalculationSummary(
    val id: UUID,
    val amendmentId: UUID,
    val number: Int,
    val draftVersion: Long,
    val status: PayrollAmendmentCalculationStatus,
    val assessmentCount: Int,
    val difference: PayrollAmendmentDifference?,
    val failure: Failure?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
