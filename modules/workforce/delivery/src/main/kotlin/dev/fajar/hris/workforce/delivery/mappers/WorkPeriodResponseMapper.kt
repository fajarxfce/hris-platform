package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.*

fun WorkPeriod.toResponse() =
    WorkPeriodResponse(
        id,
        month.toString(),
        status.name,
        timezone,
        jobId,
        version,
        startedAt,
        closedAt,
        failureCode,
    )

fun WorkPeriodEmployeeSnapshot.toResponse() =
    WorkPeriodSnapshotResponse(
        snapshot.employeeId,
        snapshot.month.toString(),
        snapshot.days.map {
            ClosedWorkDayResponse(
                it.workDate,
                it.fact.name,
                it.acceptedMinutes,
                it.schedule.toResponse(),
                it.evidenceIds,
                it.correctionId,
                it.overtime.map { item -> item.toResponse() },
            )
        },
        employee.toResponse(),
        period.toResponse(),
    )
