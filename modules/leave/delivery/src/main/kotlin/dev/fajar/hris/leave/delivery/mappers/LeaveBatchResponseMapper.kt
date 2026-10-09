package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveBatch.toResponse() =
    LeaveBatchResponse(
        id,
        kind.name,
        typeId,
        period,
        policy.toResponse(),
        policyVersion,
        timezone,
        actorId,
        createdAt,
        reason,
        totalEmployees,
        jobId,
        status.name,
        version,
    )

fun LeaveBatchResult.toResponse() =
    LeaveBatchResultResponse(
        ordinal,
        employeeId,
        jobId,
        status.name,
        resourceId,
        failureCode,
        parameters,
        completedAt,
    )

fun LeaveBatchAttempt.toResponse() =
    LeaveBatchAttemptResponse(jobId, number, baseCompleted, startedAt, reason)

fun LeaveBatchCounts.toResponse() =
    LeaveBatchCountsResponse(applied, unchanged, skipped, failed, completed)

fun LeaveBatchDetails.toResponse() =
    LeaveBatchDetailsResponse(
        batch.toResponse(),
        counts.toResponse(),
        attempts.map { it.toResponse() },
        LeaveBatchProgressResponse(
            job.request.id,
            job.status.name,
            job.completedItems,
            job.request.totalItems,
            job.attempts,
            job.version,
            job.cancellationRequested,
            job.failureCode,
        ),
        Page(results.items.map { it.toResponse() }, results.nextCursor),
    )
