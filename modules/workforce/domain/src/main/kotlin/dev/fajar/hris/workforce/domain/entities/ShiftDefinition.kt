package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

data class ShiftDefinition(
    val id: UUID,
    val details: ShiftDetails,
    val active: Boolean,
    val version: Long,
)
