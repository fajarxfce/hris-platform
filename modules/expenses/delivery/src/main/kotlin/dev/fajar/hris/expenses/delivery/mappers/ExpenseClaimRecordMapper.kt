package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.expenses.delivery.responses.ExpenseClaimRecordResponse
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimRecord

fun ExpenseClaimRecord.toResponse() =
    ExpenseClaimRecordResponse(
        details.toResponse(),
        submission?.toResponse(details.claim),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
    )
