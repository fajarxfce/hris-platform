package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.leave.data.models.LeaveAccrualSchedulePeriodRow
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

fun LeaveAccrualSchedulesRecord.toAccrualSchedule(): LeaveAccrualSchedule =
    LeaveAccrualSchedule(
        id,
        typeId,
        typeCode,
        typeName,
        policyVersion,
        LeaveAccrualFrequency.valueOf(frequency),
        YearMonth.from(firstPeriod),
        periodCount,
        totalEmployees,
        timezone,
        actorId,
        createdAt.toInstant(),
        reason,
    )

fun LeaveAccrualSchedule.toRow(company: UUID): LeaveAccrualSchedulesRecord =
    LeaveAccrualSchedulesRecord().also {
        it.companyId = company
        it.id = id
        it.typeId = typeId
        it.typeCode = typeCode
        it.typeName = typeName
        it.policyVersion = policyVersion
        it.frequency = frequency.name
        it.firstPeriod = firstPeriod.atDay(1)
        it.periodCount = periodCount
        it.totalEmployees = totalEmployees
        it.timezone = timezone
        it.actorId = actorId
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun LeaveAccrualScheduleEntry.toRow(
    company: UUID,
    schedule: UUID,
): LeaveAccrualScheduleEntriesRecord =
    LeaveAccrualScheduleEntriesRecord().also {
        it.companyId = company
        it.scheduleId = schedule
        it.ordinal = ordinal
        it.batchId = batchId
        it.initialJobId = initialJobId
        it.scheduledFor = scheduledFor.atOffset(ZoneOffset.UTC)
    }

fun LeaveAccrualSchedulePeriodRow.toAccrualPeriod(json: ObjectMapper): LeaveAccrualSchedulePeriod =
    LeaveAccrualSchedulePeriod(
        scheduledFor.toInstant(),
        batch.toBatch(json),
        JobStatus.valueOf(jobStatus),
        completedEmployees,
    )
