package dev.fajar.hris.leave.domain.entities

import java.util.UUID

/** Its ID is the immutable closing that awarded this employee's carried credit. */
data class LeaveCarryAward(
    val bucket: LeaveCarryBucket,
    val employeeId: UUID,
    val typeId: UUID,
    val timezone: String,
    val validityMonths: Int?,
)
