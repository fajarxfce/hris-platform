package dev.fajar.hris.payroll.domain.entities

import java.time.Instant

data class PayrollPaymentRecoveryReview(
    val assessment: PayrollPayable,
    val payments: PayrollPaymentProgress,
    val recoveries: List<PayrollPaymentRecovery>,
    val earliestConfirmationAt: Instant,
    val blockers: Set<PayrollPaymentRecoveryBlocker>,
) {
    val canConfirm: Boolean
        get() = blockers.isEmpty()
}
