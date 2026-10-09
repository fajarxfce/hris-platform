package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

/** Built-in kinds are translated by clients; names are optional employer-defined labels. */
data class PayrollEarningLine(
    val kind: PayrollEarningKind,
    val code: String,
    val name: String?,
    val amount: BigDecimal,
    val taxable: Boolean,
    val proration: ProratedPay? = null,
)
