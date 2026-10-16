package dev.fajar.hris.workforce.delivery.responses

data class AttendanceAdjustmentReviewResponse(
    val employee: WorkforceEmployeeResponse,
    val period: WorkPeriodResponse,
    val original: ClosedWorkDayResponse,
    val current: ClosedWorkDayResponse,
    val attendance: AttendanceDayResponse,
    val adjustmentRevision: Long,
    val revision: String,
    val canCorrect: Boolean,
    val canReviewEvidence: Boolean,
)
