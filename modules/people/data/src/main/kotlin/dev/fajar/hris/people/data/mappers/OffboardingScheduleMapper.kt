package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.people.data.datasources.OffboardingScheduleRow
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.OffboardingSchedulesRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun OffboardingSchedulesRecord.toOffboardingSchedule() =
    OffboardingSchedule(
        id,
        caseId,
        employmentId,
        caseVersion,
        employmentVersion,
        appliedEmploymentRevision,
        employeeAccountId,
        lastWorkingDate,
        companyTimezone,
        scheduledFor.toInstant(),
        validUntil.toInstant(),
        attempt,
        jobId,
        createdBy,
        createdAt.toInstant(),
        reason,
        OffboardingScheduleStatus.valueOf(status),
        version,
        finishedAt?.toInstant(),
        failureCode,
    )

fun OffboardingScheduleRow.toOffboardingScheduleDetails() =
    OffboardingScheduleDetails(
        schedule.toOffboardingSchedule(),
        LifecycleScheduleJob(
            schedule.jobId,
            JobStatus.valueOf(jobStatus),
            jobVersion,
            attempts,
            cancellationRequested,
            completedItems,
            jobFailureCode,
            jobFinishedAt?.toInstant(),
        ),
    )

fun OffboardingSchedule.toRow(companyId: UUID) =
    OffboardingSchedulesRecord().also {
        it.companyId = companyId
        it.id = id
        it.caseId = caseId
        it.employmentId = employmentId
        it.caseVersion = caseVersion
        it.employmentVersion = employmentVersion
        it.appliedEmploymentRevision = appliedEmploymentRevision
        it.employeeAccountId = employeeAccountId
        it.lastWorkingDate = lastWorkingDate
        it.companyTimezone = companyTimezone
        it.scheduledFor = OffsetDateTime.ofInstant(scheduledFor, ZoneOffset.UTC)
        it.validUntil = OffsetDateTime.ofInstant(validUntil, ZoneOffset.UTC)
        it.attempt = attempt
        it.jobId = jobId
        it.createdBy = createdBy
        it.createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
        it.reason = reason
        it.status = status.name
        it.version = version
        it.finishedAt = finishedAt?.let { at -> OffsetDateTime.ofInstant(at, ZoneOffset.UTC) }
        it.failureCode = failureCode
    }
