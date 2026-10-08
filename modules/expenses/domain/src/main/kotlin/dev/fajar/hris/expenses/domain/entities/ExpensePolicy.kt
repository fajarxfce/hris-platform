package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.people.domain.entities.ContractKind
import java.math.BigDecimal

data class ExpensePolicy(
    val name: String,
    val maximumLineAmount: BigDecimal,
    val maximumClaimAmount: BigDecimal,
    val receiptRequired: Boolean,
    val costCenterRequired: Boolean,
    val maximumAgeDays: Int,
    val allowedContracts: Set<ContractKind>,
)
