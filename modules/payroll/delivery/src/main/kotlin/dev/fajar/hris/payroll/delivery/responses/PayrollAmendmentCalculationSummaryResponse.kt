package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollAmendmentCalculationSummaryResponse(
    val id: UUID,
    val amendmentId: UUID,
    val number: Int,
    val draftVersion: Long,
    val status: String,
    val assessmentCount: Int,
    val difference: PayrollAmendmentDifferenceResponse?,
    val failure: PayrollRunFailureResponse?,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
