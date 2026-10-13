package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

/** A null shift is an explicit day off and must remain in the local calendar. */
data class RosterRecord(val id: UUID, val employeeId: UUID, val day: RosterOverride)
