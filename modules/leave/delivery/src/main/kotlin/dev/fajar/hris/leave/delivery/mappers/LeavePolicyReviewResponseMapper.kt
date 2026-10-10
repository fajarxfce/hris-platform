package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeavePolicyReview.toResponse(): LeavePolicyReviewResponse =
    LeavePolicyReviewResponse(
        current.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
    )

fun LeavePolicyRevision.toResponse(): LeavePolicyRevisionResponse =
    LeavePolicyRevisionResponse(
        revision,
        effectiveFrom,
        policy.name,
        policy.paid,
        policy.allowPartialDays,
        policy.minServiceMonths,
        policy.allowedContracts.map { it.name }.toSet(),
        policy.maxRequestDays,
        active,
        policy.attachmentRequired,
        policy.accrual?.toResponse(),
        actorId,
        reason,
        recordedAt,
    )
