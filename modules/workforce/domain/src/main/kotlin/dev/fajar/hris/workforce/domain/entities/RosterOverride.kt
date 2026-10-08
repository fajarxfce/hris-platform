package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate

data class RosterOverride(val workDate: LocalDate, val shift: ShiftSnapshot?, val version: Long)
