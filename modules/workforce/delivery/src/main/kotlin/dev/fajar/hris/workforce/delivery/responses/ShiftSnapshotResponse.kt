package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class ShiftSnapshotResponse(
    val id: UUID,
    val revision: Long,
    val details: ShiftDetailsResponse,
)
