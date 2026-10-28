package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.reporting.data.datasources.ExpenseReportDataSource
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.expenseReportCursor
import dev.fajar.hris.reporting.domain.repositories.ExpenseReportRepository
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class StoredExpenseReportRepository(private val source: ExpenseReportDataSource) :
    ExpenseReportRepository {
    override fun aggregate(company: UUID, from: LocalDate, until: LocalDate) = safeDatabaseCall {
        source.aggregate(company, from, until).toExpenseReportAggregate()
    }

    override fun claims(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: ExpenseClaimStatus?,
        after: ExpenseReportCursor?,
        limit: Int,
    ): Result<Page<ExpenseReportClaim>> = safeDatabaseCall {
        check(limit in 1..200)
        val rows =
            source.claims(
                company,
                from,
                until,
                status?.name,
                after?.submittedAt?.atOffset(ZoneOffset.UTC),
                after?.id,
                limit + 1,
            )
        check(rows.size <= limit + 1 && rows.map { it.id }.distinct().size == rows.size)
        val mapped = rows.map { it.toExpenseReportClaim() }
        check(status == null || mapped.all { it.status == status })
        val order = compareBy<ExpenseReportCursor>({ it.submittedAt }, { it.id.toString() })
        val keys = listOfNotNull(after) + mapped.map { ExpenseReportCursor(it.submittedAt, it.id) }
        check(keys.zipWithNext().all { (a, b) -> order.compare(a, b) > 0 })
        val items = mapped.take(limit)
        Page(
            items,
            if (mapped.size > limit) expenseReportCursor(company, from, until, status, items.last())
            else null,
        )
    }
}
