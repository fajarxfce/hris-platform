package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page

data class PayrollPaymentReviewResponse(
    val batch: PayrollPaymentBatchResponse,
    val history: Page<PayrollPaymentActionResponse>,
    val results: List<PayrollPaymentReconciliationResponse>,
    val availableActions: Set<String>,
)
