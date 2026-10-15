package dev.fajar.hris.payroll.data.models

import java.math.BigDecimal
import java.util.UUID

data class PayrollPaymentHeadRow(
    val assessmentId: UUID,
    val version: Long,
    val assessmentRevision: Long,
    val entitlement: BigDecimal,
    val confirmedPaid: BigDecimal,
    val confirmedRecovered: BigDecimal,
)
