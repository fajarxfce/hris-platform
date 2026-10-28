package dev.fajar.hris.reporting.data.dto

import java.time.LocalDate

data class AttendanceExceptionAggregateRow(
    val date: LocalDate?,
    val observedDays: Long,
    val affectedDays: Long,
    val affectedEmployments: Long,
    val pendingDays: Long,
    val incompleteDays: Long,
    val pendingEvents: Long,
)
