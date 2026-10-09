package dev.fajar.hris.jobs.domain.entities

enum class JobKind {
    WORKFORCE_CLOSE,
    LEAVE_ACCRUAL,
    LEAVE_YEAR_CLOSE,
    DOCUMENT_VALIDATE,
    DOCUMENT_INVENTORY,
    EMPLOYEE_IMPORT_PREVIEW,
    EMPLOYEE_IMPORT_APPLY,
}
