package dev.fajar.hris.payroll.data.models

import java.time.*

data class PayrollEarningLineData(
    val kind: String,
    val code: String,
    val name: String?,
    val amount: String,
    val taxable: Boolean,
    val proration: ProratedPayData?,
)
