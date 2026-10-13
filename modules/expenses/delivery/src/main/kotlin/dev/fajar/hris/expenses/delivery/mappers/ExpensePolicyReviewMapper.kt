package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.expenses.delivery.responses.ExpensePolicyReviewResponse
import dev.fajar.hris.expenses.domain.entities.ExpensePolicyReview

fun ExpensePolicyReview.toResponse() =
    ExpensePolicyReviewResponse(
        current.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
    )
