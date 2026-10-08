package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class WorkHolidayResponse(
    val id: UUID,
    val workDate: LocalDate,
    val name: String,
    val active: Boolean,
    val version: Long,
)
