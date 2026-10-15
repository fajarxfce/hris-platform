package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.payroll.domain.entities.*

/** Advisory current facts; confirming a return independently rechecks its observed balance. */
fun payrollPaymentRecoveryReview(
    beneficiary: Boolean,
    assessment: PayrollPayable,
    payments: PayrollPaymentProgress,
    recoveries: List<PayrollPaymentRecovery>,
): PayrollPaymentRecoveryReview {
    val latestPayment =
        payments.attempts
            .filter { it.status == PayrollPaymentItemStatus.SUCCEEDED }
            .mapNotNull { it.resolvedAt }
            .maxOrNull()
    val blockers = buildSet {
        if (payments.settlement.recoverable.signum() <= 0)
            add(PayrollPaymentRecoveryBlocker.NOT_OVERPAID)
        if (
            payments.attempts.any {
                it.status in
                    setOf(PayrollPaymentItemStatus.PREPARED, PayrollPaymentItemStatus.PENDING)
            }
        )
            add(PayrollPaymentRecoveryBlocker.PENDING_PAYMENT)
        if (recoveries.size >= 20) add(PayrollPaymentRecoveryBlocker.RECOVERY_LIMIT)
        if (beneficiary) add(PayrollPaymentRecoveryBlocker.BENEFICIARY)
    }
    return PayrollPaymentRecoveryReview(
        assessment,
        payments,
        java.util.List.copyOf(recoveries),
        maxOf(assessment.publishedAt, latestPayment ?: assessment.publishedAt),
        blockers,
    )
}
