package dev.fajar.hris.workforce.domain.entities

data class RosterBulkEmployee(
    val employee: WorkforceEmployeeReference,
    val days: List<RosterBulkDay>,
)
