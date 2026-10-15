package dev.fajar.hris.payroll.domain.entities

enum class PayrollAmendmentAction {
    SAVED,
    CALCULATED,
    CALCULATION_FAILED,
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    CANCELLED,
    PUBLICATION_STARTED,
    PUBLISHED,
    PUBLICATION_STOPPED,
}
