package dev.fajar.hris.leave.domain.entities

import java.util.UUID

/** One carried award in its destination year, separately accounted from ordinary credit. */
data class LeaveCarryBucket(
    val id: UUID,
    val year: Int,
    val initialHalfDays: Int,
    val availableHalfDays: Int,
    val reservedHalfDays: Int = 0,
    val consumedHalfDays: Int = 0,
    val expiredHalfDays: Int = 0,
    val transferredHalfDays: Int = 0,
    val deductedHalfDays: Int = 0,
    val validity: LeaveCarryValidity? = null,
    val version: Long = 0,
)
