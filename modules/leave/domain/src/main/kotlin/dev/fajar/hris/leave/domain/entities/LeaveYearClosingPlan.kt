package dev.fajar.hris.leave.domain.entities

/** Source expiry is posted before the closing snapshot; other movements belong to that closing. */
data class LeaveYearClosingPlan(
    val source: LeaveBalance,
    val rollover: LeaveYearRollover,
    val validity: LeaveCarryValidity?,
    val settlement: LeaveCarryMovement?,
    val movements: List<LeaveCarryMovement>,
)
