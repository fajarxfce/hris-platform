package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page

data class PayrollPaymentReview(
    val batch: PayrollPaymentBatch,
    val history: Page<PayrollPaymentAction>,
    val results: List<PayrollPaymentReconciliation>,
    val availableActions: Set<PayrollPaymentAvailableAction>,
)
