package dev.fajar.hris.workforce.delivery.requests
data class RosterDayRequest(
    val shift: ShiftReferenceRequest? = null,
    val expectedVersion: Long? = null,
    val reason: String,
)
