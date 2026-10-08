package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate
import java.util.UUID

data class LeaveType(
    val id: UUID,
    val code: String,
    val effectiveFrom: LocalDate,
    val policy: LeavePolicy,
    val active: Boolean,
    val version: Long,
    val appliedRevision: Long,
)
