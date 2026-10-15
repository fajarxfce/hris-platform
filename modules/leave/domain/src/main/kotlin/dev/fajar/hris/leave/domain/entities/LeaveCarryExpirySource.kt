package dev.fajar.hris.leave.domain.entities

import java.util.UUID

sealed interface LeaveCarryExpirySource {
    data class Batch(val batchId: UUID) : LeaveCarryExpirySource

    data class Closing(val closingId: UUID, val employeeId: UUID) : LeaveCarryExpirySource
}
