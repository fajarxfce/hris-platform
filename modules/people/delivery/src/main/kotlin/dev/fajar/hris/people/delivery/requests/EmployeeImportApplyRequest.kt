package dev.fajar.hris.people.delivery.requests

data class EmployeeImportApplyRequest(
    val expectedVersion: Long,
    val allowPartial: Boolean = false,
    val reason: String,
)
