package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollPaymentProgressResponse(
    val assessmentId: UUID,
    val version: Long,
    val attempts: List<PayrollPaymentAttemptResponse>,
)
