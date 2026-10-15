package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveAccrualSchedule.toResponse() =
    LeaveAccrualScheduleResponse(
        id,
        typeId,
        typeCode,
        typeName,
        policyVersion,
        frequency.name,
        firstPeriod,
        periodCount,
        totalEmployees,
        timezone,
        actorId,
        createdAt,
        reason,
    )

fun LeaveAccrualScheduleDetails.toResponse() =
    LeaveAccrualScheduleDetailsResponse(
        schedule.toResponse(),
        periods.map {
            LeaveAccrualSchedulePeriodResponse(
                it.scheduledFor,
                it.batch.toResponse(),
                it.jobStatus.name,
                it.completedEmployees,
            )
        },
    )

fun LeaveAccrualScheduleReview.toResponse() =
    LeaveAccrualScheduleReviewResponse(
        companyDate,
        timezone,
        frequency.name,
        policyVersion,
        periods.map { LeaveAccrualPeriodReviewResponse(it.period, it.dueAt, it.type.toResponse()) },
        employeeIds,
    )
