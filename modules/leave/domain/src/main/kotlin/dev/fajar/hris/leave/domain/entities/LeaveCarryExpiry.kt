package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.util.UUID

/** Fixed award deadline and cohort; execution status belongs to the current durable job. */
data class LeaveCarryExpiry(
    val id: UUID,
    val source: LeaveCarryExpirySource,
    val typeId: UUID,
    val year: Int,
    val timezone: String,
    val validity: LeaveCarryValidity,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
    val totalEmployees: Int,
    val jobId: UUID,
    val version: Long = 0,
)
