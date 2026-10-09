package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.WorkPeriodsRecord
import dev.fajar.hris.workforce.data.models.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.YearMonth

fun WorkPeriodsRecord.toPeriod() =
    WorkPeriod(
        requireNotNull(id),
        YearMonth.from(month),
        WorkPeriodStatus.valueOf(requireNotNull(status)),
        timezone,
        jobId,
        requireNotNull(version),
        startedAt?.toInstant(),
        closedAt?.toInstant(),
        failureCode,
    )

fun WorkPeriodSnapshot.toData() =
    WorkPeriodSnapshotData(
        employeeId,
        month.toString(),
        days.map {
            ClosedWorkDayData(
                it.workDate,
                it.fact.name,
                it.acceptedMinutes,
                it.schedule.toData(),
                it.evidenceIds,
                it.correctionId,
                it.overtime.map { item ->
                    ApprovedOvertimeData(
                        item.requestId,
                        item.revision,
                        OvertimeIntervalData(
                            item.actual.startsAt,
                            item.actual.endsAt,
                            item.actual.breakMinutes,
                        ),
                        item.approvedMinutes,
                        item.schedule.toData(),
                    )
                },
            )
        },
    )

fun WorkPeriodSnapshotData.toSnapshot() =
    WorkPeriodSnapshot(
        employeeId,
        YearMonth.parse(month),
        days.map {
            ClosedWorkDay(
                it.workDate,
                WorkDayFact.valueOf(it.fact),
                it.acceptedMinutes,
                it.schedule.toDay(),
                it.evidenceIds,
                it.correctionId,
                it.overtime.map { item ->
                    ApprovedOvertime(
                        item.requestId,
                        item.revision,
                        OvertimeInterval(
                            item.actual.startsAt,
                            item.actual.endsAt,
                            item.actual.breakMinutes,
                        ),
                        item.approvedMinutes,
                        item.schedule.toDay(),
                    )
                },
            )
        },
    )
