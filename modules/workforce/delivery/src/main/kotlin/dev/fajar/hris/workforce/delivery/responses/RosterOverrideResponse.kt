package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class RosterOverrideResponse(
    val workDate: LocalDate,
    val version: Long,
    val active: Boolean,
    val shift: ShiftSnapshotResponse?,
)
