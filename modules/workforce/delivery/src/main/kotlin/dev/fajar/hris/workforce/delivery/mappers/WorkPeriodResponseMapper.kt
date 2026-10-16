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
        snapshot.days.map { it.toResponse() },
        employee.toResponse(),
        period.toResponse(),
    )

fun ClosedWorkDay.toResponse() =
    ClosedWorkDayResponse(
        workDate,
        fact.name,
        acceptedMinutes,
        schedule.toResponse(),
        evidenceIds,
        correctionId,
        overtime.map { it.toResponse() },
    )
