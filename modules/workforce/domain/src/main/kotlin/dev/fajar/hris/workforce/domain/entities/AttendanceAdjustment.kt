package dev.fajar.hris.workforce.domain.entities

import java.util.UUID

/** A new employee snapshot; the original closing and prior revisions stay immutable. */
data class AttendanceAdjustment(
    val summary: AttendanceAdjustmentSummary,
    val closingJobId: UUID,
    val snapshot: WorkPeriodSnapshot,
)
