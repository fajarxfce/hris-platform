package dev.fajar.hris.payroll.domain.entities

enum class PayrollPaymentItemStatus {
    PREPARED,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}
