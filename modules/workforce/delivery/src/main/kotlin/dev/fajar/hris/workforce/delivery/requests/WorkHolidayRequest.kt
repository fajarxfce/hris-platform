package dev.fajar.hris.workforce.delivery.requests

import java.time.LocalDate

data class WorkHolidayRequest(
    val workDate: LocalDate,
    val name: String,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val reason: String,
)
