package dev.fajar.hris.payroll.delivery.responses

import java.time.Instant
import java.util.UUID

data class PayrollPaymentRecoveryReviewResponse(
    val assessmentId: UUID,
    val employmentId: UUID,
    val employeeNumber: String,
    val employeeName: String,
    val taxMonth: String,
    val publishedAt: Instant,
    val payments: PayrollPaymentProgressResponse,
    val recoveries: List<PayrollPaymentRecoveryResponse>,
    val earliestConfirmationAt: Instant,
    val canConfirm: Boolean,
    val blockers: Set<String>,
)
