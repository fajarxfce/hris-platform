package dev.fajar.hris.payroll.domain.entities

enum class PayrollPaymentRecoveryBlocker {
    NOT_OVERPAID,
    PENDING_PAYMENT,
    RECOVERY_LIMIT,
    BENEFICIARY,
}
