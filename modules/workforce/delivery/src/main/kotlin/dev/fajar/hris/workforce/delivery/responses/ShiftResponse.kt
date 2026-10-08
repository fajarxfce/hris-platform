package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class ShiftResponse(
    val id: UUID,
    val details: ShiftDetailsResponse,
    val active: Boolean,
    val version: Long,
)
