package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

data class ShiftSnapshot(val id: UUID, val revision: Long, val details: ShiftDetails)
