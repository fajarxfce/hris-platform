package dev.fajar.hris.leave.domain.entities

data class LeaveDecisionOutcome(val status: LeaveStatus, val effect: LeaveBalanceEffect?)
