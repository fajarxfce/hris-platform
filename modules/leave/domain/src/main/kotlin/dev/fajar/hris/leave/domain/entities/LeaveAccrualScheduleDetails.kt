package dev.fajar.hris.leave.domain.entities

data class LeaveAccrualScheduleDetails(
    val schedule: LeaveAccrualSchedule,
    val periods: List<LeaveAccrualSchedulePeriod>,
)
