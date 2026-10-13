package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReview

fun ExpenseClaimReview.toResponse() =
    ExpenseClaimReviewResponse(
        details.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
        Page(submissions.items.map { it.toResponse() }, submissions.nextCursor),
        payments.map {
            ExpensePaymentProgressResponse(
                it.batchId,
                it.itemId,
                it.status.name,
                it.createdAt,
                it.releasedAt,
                it.settledAt,
            )
        },
    )
