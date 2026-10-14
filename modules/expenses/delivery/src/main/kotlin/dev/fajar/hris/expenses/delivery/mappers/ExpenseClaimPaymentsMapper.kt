package dev.fajar.hris.expenses.delivery.mappers

import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.*

fun ExpensePaymentProgress.toResponse() =
    ExpensePaymentProgressResponse(batchId, itemId, status.name, createdAt, releasedAt, settledAt)

fun ExpenseClaimPayments.toResponse() =
    ExpenseClaimPaymentsResponse(claimId, version, attempts.map { it.toResponse() })
