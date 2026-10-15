package dev.fajar.hris.leave.domain.entities

import java.util.UUID

/** Immutable carried units attributed to a submitted request. */
data class LeaveCarryAllocation(val bucketId: UUID, val year: Int, val halfDays: Int)
