package dev.fajar.hris.expenses.delivery.requests

import dev.fajar.hris.people.domain.entities.ContractKind
import java.time.LocalDate

data class ExpenseCategoryRequest(
    val code: String,
    val name: String,
    val effectiveFrom: LocalDate,
    val maximumLineAmount: String,
    val maximumClaimAmount: String,
    val receiptRequired: Boolean = true,
    val costCenterRequired: Boolean = true,
    val maximumAgeDays: Int = 30,
    val allowedContracts: Set<ContractKind> = ContractKind.entries.toSet(),
    val active: Boolean = true,
    val expectedVersion: Long? = null,
    val reason: String,
)
