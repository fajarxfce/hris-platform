package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollWorkSourceResponse(
    val periodId: UUID,
    val earningsMonth: String,
    val version: Long,
    val closed: Boolean,
    val jobId: UUID?,
    val includesEmployee: Boolean,
)
