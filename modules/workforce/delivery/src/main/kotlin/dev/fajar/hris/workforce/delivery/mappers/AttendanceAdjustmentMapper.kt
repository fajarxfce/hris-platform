package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.*

fun AttendanceAdjustmentSummary.toResponse() =
    AttendanceAdjustmentSummaryResponse(
        id,
        revision,
        workDate,
        correctionId,
        actorId,
        recordedAt,
        reason,
    )

fun AttendanceAdjustmentReview.toResponse() =
    AttendanceAdjustmentReviewResponse(
        employee.toResponse(),
        period.toResponse(),
        original.toResponse(),
        current.toResponse(),
        attendance.toResponse(),
        adjustmentRevision,
        revision,
        canCorrect,
        canReviewEvidence,
    )

fun AttendanceAdjustmentDetails.toResponse() =
    AttendanceAdjustmentDetailsResponse(
        adjustment.summary.toResponse(),
        WorkPeriodEmployeeSnapshot(employee, period, adjustment.snapshot).toResponse(),
    )
