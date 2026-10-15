package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.people.data.datasources.OnboardingScheduleRow
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.OnboardingSchedulesRecord
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

fun OnboardingScheduleRow.toOnboardingSchedule(json: ObjectMapper): OnboardingSchedule {
    val row = schedule
    val assignees =
        json.readValue(row.assignees.data(), object : TypeReference<Map<String, UUID>>() {})
    check(assignees.size <= 64)
    return OnboardingSchedule(
        row.id,
        row.caseId,
        row.employmentId,
        row.employmentVersion,
        row.appliedEmploymentRevision,
        OnboardingScheduleSelection(row.templateId, row.targetDate, row.startsOn),
        template.toLifecycleTemplate(json),
        assignees.toMap(),
        row.companyTimezone,
        row.scheduledFor.toInstant(),
        row.validUntil.toInstant(),
        row.attempt,
        row.jobId,
        row.createdBy,
        row.createdAt.toInstant(),
        row.reason,
        OnboardingScheduleStatus.valueOf(row.status),
        row.version,
        row.finishedAt?.toInstant(),
        row.failureCode,
    )
}

fun OnboardingScheduleRow.toOnboardingScheduleDetails(json: ObjectMapper) =
    OnboardingScheduleDetails(
        toOnboardingSchedule(json),
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

fun OnboardingSchedule.toRow(companyId: UUID, json: ObjectMapper) =
    OnboardingSchedulesRecord().also {
        it.companyId = companyId
        it.id = id
        it.caseId = caseId
        it.employmentId = employmentId
        it.employmentVersion = employmentVersion
        it.appliedEmploymentRevision = appliedEmploymentRevision
        it.templateId = selection.templateId
        it.templateVersion = template.version
        it.targetDate = selection.targetDate
        it.startsOn = selection.startsOn
        it.assignees = JSONB.valueOf(json.writeValueAsString(assignees))
        it.companyTimezone = companyTimezone
        it.scheduledFor = scheduledFor.atOffset(ZoneOffset.UTC)
        it.validUntil = validUntil.atOffset(ZoneOffset.UTC)
        it.attempt = attempt
        it.jobId = jobId
        it.createdBy = createdBy
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.status = status.name
        it.version = version
        it.finishedAt = finishedAt?.atOffset(ZoneOffset.UTC)
        it.failureCode = failureCode
    }
