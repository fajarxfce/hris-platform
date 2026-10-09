package dev.fajar.hris.leave.domain.entities

import java.time.*
import java.util.UUID

data class LeaveBatch(
    val id: UUID,
    val kind: LeaveBatchKind,
    val typeId: UUID,
    val period: YearMonth,
    val policy: LeavePolicySnapshot,
    val policyVersion: Long,
    val timezone: String,
    val actorId: UUID,
    val createdAt: Instant,
    val reason: String,
    val totalEmployees: Int,
    val jobId: UUID,
    val status: LeaveBatchStatus,
    val version: Long,
)
