package dev.fajar.hris.payroll.domain.entities

enum class PayrollPeriodStatus {
    DRAFT,
    PROCESSING,
    CALCULATED,
    FINALIZED,
    CANCELLED,
}
