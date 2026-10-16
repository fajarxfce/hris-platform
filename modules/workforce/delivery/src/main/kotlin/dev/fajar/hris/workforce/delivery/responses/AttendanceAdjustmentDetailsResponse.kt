package dev.fajar.hris.workforce.delivery.responses

data class AttendanceAdjustmentDetailsResponse(
    val summary: AttendanceAdjustmentSummaryResponse,
    val snapshot: WorkPeriodSnapshotResponse,
)
