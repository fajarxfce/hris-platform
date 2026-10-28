package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.reporting.domain.entities.*
import java.time.LocalDate
import java.util.UUID

interface ExpenseReportRepository {
    fun aggregate(company: UUID, from: LocalDate, until: LocalDate): Result<ExpenseReportAggregate>

    fun claims(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: ExpenseClaimStatus?,
        after: ExpenseReportCursor?,
        limit: Int,
    ): Result<Page<ExpenseReportClaim>>
}
