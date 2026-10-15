package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.*

fun OffboardingScheduleDetails.toResponse() =
    OffboardingScheduleResponse(
        schedule.id,
        schedule.caseId,
        schedule.employmentId,
        schedule.status.name,
        schedule.version,
        schedule.caseVersion,
        schedule.employmentVersion,
        schedule.appliedEmploymentRevision,
        schedule.lastWorkingDate,
        schedule.companyTimezone,
        schedule.scheduledFor,
        schedule.validUntil,
        schedule.attempt,
        schedule.createdBy,
        schedule.createdAt,
        schedule.reason,
        schedule.finishedAt,
        schedule.failureCode,
        OffboardingScheduleJobResponse(
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

fun OffboardingScheduleReview.toResponse() =
    OffboardingScheduleReviewResponse(
        case.toResponse(),
        employmentVersion,
        appliedEmploymentRevision,
        window.companyTimezone,
        window.today,
        window.scheduledFor,
        window.validUntil,
        basisRevision,
        attempts,
        blockers,
    )
