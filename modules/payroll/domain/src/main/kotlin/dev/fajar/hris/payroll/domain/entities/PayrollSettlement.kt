package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

/** Coherent financial projection for one assessment revision, independent of payslip sync. */
data class PayrollSettlement(
    val assessmentRevision: Long,
    val entitlement: BigDecimal,
    val confirmedPaid: BigDecimal,
    val confirmedRecovered: BigDecimal,
) {
    val balance: BigDecimal
        get() = entitlement - confirmedPaid + confirmedRecovered

    val payable: BigDecimal
        get() = balance.max(BigDecimal.ZERO)

    val recoverable: BigDecimal
        get() = balance.negate().max(BigDecimal.ZERO)
}
