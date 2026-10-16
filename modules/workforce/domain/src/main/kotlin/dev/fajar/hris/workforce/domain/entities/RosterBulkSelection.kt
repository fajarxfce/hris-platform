package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate
import java.util.UUID

/** An explicit, bounded cross-product; a directory filter never selects hidden employees. */
data class RosterBulkSelection(
    val employeeIds: List<UUID>,
    val dates: List<LocalDate>,
    val mode: RosterBulkMode,
    val shift: ShiftReference? = null,
)
