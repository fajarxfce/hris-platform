package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeavePolicyRevision(
    val revision: Long,
    val effectiveFrom: LocalDate,
    val policy: LeavePolicy,
    val active: Boolean,
    val actorId: UUID,
    val reason: String,
    val recordedAt: Instant,
)
