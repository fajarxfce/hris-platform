package dev.fajar.hris.payroll.delivery.requests

import java.time.*

data class PayrollRunAbandonRequest(
    val expectedVersion: Long,
    val expectedPeriodVersion: Long,
    val reason: String,
)
