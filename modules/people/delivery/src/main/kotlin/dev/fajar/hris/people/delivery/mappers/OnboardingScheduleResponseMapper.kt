package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.*

fun OnboardingScheduleDetails.toResponse() =
    OnboardingScheduleResponse(
        schedule.id,
        schedule.caseId,
        schedule.employmentId,
        schedule.status.name,
        schedule.version,
        schedule.employmentVersion,
        schedule.appliedEmploymentRevision,
        schedule.template.toResponse(),
        schedule.assignees,
        schedule.selection.targetDate,
        schedule.selection.startsOn,
        schedule.companyTimezone,
        schedule.scheduledFor,
        schedule.validUntil,
        schedule.attempt,
        schedule.createdBy,
        schedule.createdAt,
        schedule.reason,
        schedule.finishedAt,
        schedule.failureCode,
        OnboardingScheduleJobResponse(
            job.id,
            job.status.name,
            job.version,
            job.attempts,
            job.cancellationRequested,
            job.completedItems,
            job.failureCode,
            job.finishedAt,
        ),
        actions,
    )

fun OnboardingScheduleReview.toResponse() =
    OnboardingScheduleReviewResponse(
        employee.toResponse(),
        employmentVersion,
        appliedEmploymentRevision,
        template.toResponse(),
        selection.targetDate,
        selection.startsOn,
        window.companyTimezone,
        window.today,
        window.scheduledFor,
        window.validUntil,
        basisRevision,
        attempts,
        blockers,
    )
