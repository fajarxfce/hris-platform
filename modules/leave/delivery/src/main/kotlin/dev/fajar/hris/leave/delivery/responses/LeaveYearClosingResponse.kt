package dev.fajar.hris.leave.delivery.responses

import java.time.*
import java.util.UUID

data class LeaveYearClosingResponse(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val year: Int,
    val sourceVersion: Long,
    val destinationVersion: Long,
    val availableDays: String,
    val consumedDays: String,
    val carriedDays: String,
    val expiredDays: String,
    val policy: LeavePolicySnapshotResponse,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
