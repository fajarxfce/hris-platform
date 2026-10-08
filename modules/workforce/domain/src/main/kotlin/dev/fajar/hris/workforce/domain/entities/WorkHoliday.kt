package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate
import java.util.UUID

data class WorkHoliday(
    val id: UUID,
    val workDate: LocalDate,
    val name: String,
    val active: Boolean,
    val version: Long,
)
