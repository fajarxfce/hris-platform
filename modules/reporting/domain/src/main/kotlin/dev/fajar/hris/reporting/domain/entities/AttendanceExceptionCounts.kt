package dev.fajar.hris.reporting.domain.entities

data class AttendanceExceptionCounts(
    val observedDays: Long = 0,
    val affectedDays: Long = 0,
    val affectedEmployments: Long = 0,
    val pendingDays: Long = 0,
    val incompleteDays: Long = 0,
    val pendingEvents: Long = 0,
)
