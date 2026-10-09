package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class LeaveBatchResponse(
    val id: UUID,
    val kind: String,
    val typeId: UUID,
    val period: YearMonth,
    val policy: LeavePolicySnapshotResponse,
    val policyVersion: Long,
    val timezone: String,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
    val totalEmployees: Int,
    val jobId: UUID,
    val status: String,
    val version: Long,
)
