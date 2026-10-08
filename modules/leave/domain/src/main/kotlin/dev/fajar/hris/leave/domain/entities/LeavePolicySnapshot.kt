package dev.fajar.hris.leave.domain.entities

import java.util.UUID

data class LeavePolicySnapshot(
    val typeId: UUID,
    val code: String,
    val revision: Long,
    val policy: LeavePolicy,
)
