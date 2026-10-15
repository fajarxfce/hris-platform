package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal
import java.util.UUID

data class PayrollPaymentInstruction(
    val id: UUID,
    val assessmentId: UUID,
    val destination: PayrollPaymentDestination,
    val assessmentRevision: Long? = null,
    val paymentVersion: Long? = null,
    val amount: BigDecimal? = null,
)
