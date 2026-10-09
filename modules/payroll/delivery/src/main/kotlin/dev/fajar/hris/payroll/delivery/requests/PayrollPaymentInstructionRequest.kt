package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class PayrollPaymentInstructionRequest(
    val id: UUID,
    val assessmentId: UUID,
    val destination: PayrollPaymentDestinationRequest,
)
