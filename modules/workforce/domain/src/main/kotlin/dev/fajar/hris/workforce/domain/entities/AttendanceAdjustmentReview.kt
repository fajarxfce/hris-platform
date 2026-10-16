package dev.fajar.hris.workforce.domain.entities

data class AttendanceAdjustmentReview(
    val employee: WorkforceEmployeeReference,
    val period: WorkPeriod,
    val original: ClosedWorkDay,
    val current: ClosedWorkDay,
    val attendance: AttendanceDay,
    val adjustmentRevision: Long,
    val revision: String,
    val canCorrect: Boolean,
    val canReviewEvidence: Boolean,
)
