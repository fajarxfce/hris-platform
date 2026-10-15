package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveCarryExpiryOverview.toResponse(): LeaveCarryExpiryResponse {
    val source = expiry.source
    return LeaveCarryExpiryResponse(
        expiry.id,
        when (source) {
            is LeaveCarryExpirySource.Batch -> "BATCH"
            is LeaveCarryExpirySource.Closing -> "CLOSING"
        },
        when (source) {
            is LeaveCarryExpirySource.Batch -> source.batchId
            is LeaveCarryExpirySource.Closing -> source.closingId
        },
        (source as? LeaveCarryExpirySource.Closing)?.employeeId,
        expiry.typeId,
        typeCode,
        typeName,
        expiry.year,
        expiry.timezone,
        expiry.validity.through,
        expiry.validity.expiresAt,
        expiry.actorId,
        expiry.createdAt,
        expiry.reason,
        expiry.totalEmployees,
        expiry.jobId,
        expiry.version,
        status.name,
        completedEmployees,
        failureCode,
    )
}

fun LeaveCarryExpiryDetails.toResponse(): LeaveCarryExpiryDetailsResponse =
    LeaveCarryExpiryDetailsResponse(
        overview.toResponse(),
        attempts.map {
            LeaveCarryExpiryAttemptResponse(
                it.jobId,
                it.attempt,
                it.baseCompleted,
                it.startedAt,
                it.reason,
            )
        },
        LeaveCarryExpiryJobResponse(
            job.request.id,
            job.status.name,
            job.completedItems,
            job.request.totalItems,
            job.attempts,
            job.version,
            job.failureCode,
            job.request.scheduledFor,
            job.availableAt,
            job.finishedAt,
        ),
        Page(
            results.items.map {
                LeaveCarryExpiryOutcomeResponse(
                    it.ordinal,
                    it.employeeId,
                    it.jobId,
                    it.status.name,
                    it.halfDays.toLeaveDays(),
                    it.ledgerId,
                    it.recordedAt,
                )
            },
            results.nextCursor,
        ),
        employees.map { it.toResponse() },
        availableActions.map { it.name }.toSet(),
    )
