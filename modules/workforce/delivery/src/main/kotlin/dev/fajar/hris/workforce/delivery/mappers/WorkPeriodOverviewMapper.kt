package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.WorkPeriodOverview

fun WorkPeriodOverview.toResponse() =
    WorkPeriodOverviewResponse(
        month.toString(),
        companyDate,
        companyTimezone,
        state?.period?.toResponse(),
        state?.attempt?.let {
            WorkPeriodAttemptResponse(
                it.id,
                it.status.name,
                it.totalEmployees,
                it.completedEmployees,
                it.cancellationRequested,
                it.failureCode,
                it.createdAt,
                it.finishedAt,
            )
        },
        availableActions,
    )
