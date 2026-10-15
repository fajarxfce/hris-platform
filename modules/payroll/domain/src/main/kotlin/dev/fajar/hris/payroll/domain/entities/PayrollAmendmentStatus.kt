package dev.fajar.hris.payroll.domain.entities

enum class PayrollAmendmentStatus {
    DRAFT,
    CALCULATED,
    IN_REVIEW,
    APPROVED,
    PUBLISHING,
    PUBLISHED,
    REJECTED,
    CANCELLED,
}
