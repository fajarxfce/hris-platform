package dev.fajar.hris.expenses.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class ExpenseCategoryResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val currency: String,
    val effectiveFrom: LocalDate,
    val maximumLineAmount: String,
    val maximumClaimAmount: String,
    val receiptRequired: Boolean,
    val costCenterRequired: Boolean,
    val maximumAgeDays: Int,
    val allowedContracts: Set<String>,
    val active: Boolean,
    val version: Long,
    val appliedRevision: Long,
)
