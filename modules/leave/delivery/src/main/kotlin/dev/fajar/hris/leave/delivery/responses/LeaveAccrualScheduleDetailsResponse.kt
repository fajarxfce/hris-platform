package dev.fajar.hris.leave.delivery.responses

data class LeaveAccrualScheduleDetailsResponse(
    val schedule: LeaveAccrualScheduleResponse,
    val periods: List<LeaveAccrualSchedulePeriodResponse>,
)
