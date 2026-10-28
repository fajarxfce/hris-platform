package dev.fajar.hris.reporting.delivery.responses

data class AttendanceExceptionCountsResponse(
    val observedDays: Long,
    val affectedDays: Long,
    val affectedEmployments: Long,
    val pendingDays: Long,
    val incompleteDays: Long,
    val pendingEvents: Long,
)
