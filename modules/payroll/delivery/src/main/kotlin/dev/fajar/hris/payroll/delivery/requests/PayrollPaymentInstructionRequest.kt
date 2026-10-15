package dev.fajar.hris.payroll.delivery.requests

import java.math.BigDecimal
import java.util.UUID

data class PayrollPaymentInstructionRequest(
    val id: UUID,
    val assessmentId: UUID,
    val destination: PayrollPaymentDestinationRequest,
    val assessmentRevision: Long? = null,
    val paymentVersion: Long? = null,
    val amount: BigDecimal? = null,
)
