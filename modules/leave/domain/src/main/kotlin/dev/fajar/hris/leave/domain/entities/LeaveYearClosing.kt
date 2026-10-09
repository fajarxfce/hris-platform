package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveYearClosing(
    val id: UUID,
    val employeeId: UUID,
    val typeId: UUID,
    val year: Int,
    val sourceVersion: Long,
    val availableHalfDays: Int,
    val consumedHalfDays: Int,
    val destinationVersion: Long,
    val rollover: LeaveYearRollover,
    val policy: LeavePolicySnapshot,
    val actorId: UUID,
    val recordedAt: Instant,
    val reason: String,
)
