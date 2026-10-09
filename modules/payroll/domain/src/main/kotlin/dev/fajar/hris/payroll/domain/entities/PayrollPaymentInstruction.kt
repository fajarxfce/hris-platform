package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

data class PayrollPaymentInstruction(
    val id: UUID,
    val assessmentId: UUID,
    val destination: PayrollPaymentDestination,
)
