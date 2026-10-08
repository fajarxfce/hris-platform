package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate
import java.util.UUID

data class ClosedWorkDay(
    val workDate: LocalDate,
    val fact: WorkDayFact,
    val acceptedMinutes: Long,
    val schedule: ScheduledDay,
    val evidenceIds: List<UUID>,
    val correctionId: UUID?,
)
