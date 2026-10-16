package dev.fajar.hris.workforce.domain.entities

data class AttendanceAdjustmentDetails(
    val employee: WorkforceEmployeeReference,
    val period: WorkPeriod,
    val adjustment: AttendanceAdjustment,
)
