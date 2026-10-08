package dev.fajar.hris.people.domain.entities

enum class EmployeeImportStatus {
    PREVIEWING,
    REVIEW,
    IMPORTING,
    COMPLETED,
    STOPPED,
    CANCELLED,
}
