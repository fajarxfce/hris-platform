package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus

data class ExpenseClaimStatusCounts(val status: ExpenseClaimStatus, val counts: ExpenseReportCounts)
