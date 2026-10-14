package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollCalculationSourceResponse(
    val id: UUID,
    val month: String,
    val version: Long,
    val closed: Boolean,
    val jobId: UUID?,
    val timezone: String?,
)
