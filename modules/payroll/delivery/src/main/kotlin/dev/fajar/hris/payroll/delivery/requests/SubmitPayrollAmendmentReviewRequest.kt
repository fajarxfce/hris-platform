package dev.fajar.hris.payroll.delivery.requests

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class SubmitPayrollAmendmentReviewRequest(
    @field:Schema(minimum = "0", maximum = "987") val expectedVersion: Long,
    val calculationId: UUID,
    @field:Schema(minLength = 1, maxLength = 1000) val reason: String,
)
