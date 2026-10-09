package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollEarningLineResponse(
    val kind: String,
    val code: String,
    val name: String?,
    val amount: String,
    val taxable: Boolean,
    val proration: ProratedPayResponse?,
)
