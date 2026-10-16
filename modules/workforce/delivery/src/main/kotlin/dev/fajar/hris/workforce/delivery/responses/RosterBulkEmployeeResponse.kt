package dev.fajar.hris.workforce.delivery.responses

data class RosterBulkEmployeeResponse(
    val employee: WorkforceEmployeeResponse,
    val days: List<RosterBulkDayResponse>,
)
