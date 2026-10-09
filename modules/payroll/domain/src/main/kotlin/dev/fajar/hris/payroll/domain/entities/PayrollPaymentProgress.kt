package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

data class PayrollPaymentProgress(
    val assessmentId: UUID,
    val version: Long,
    val attempts: List<PayrollPaymentAttempt>,
)
