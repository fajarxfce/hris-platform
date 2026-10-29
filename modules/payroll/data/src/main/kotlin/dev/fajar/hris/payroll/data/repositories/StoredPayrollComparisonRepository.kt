package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.data.datasources.PayrollComparisonDataSource
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollComparisonRepository
import java.math.BigDecimal
import java.util.UUID

class StoredPayrollComparisonRepository(private val source: PayrollComparisonDataSource) :
    PayrollComparisonRepository {
    override fun compare(
        company: UUID,
        baseline: UUID,
        current: UUID,
        after: UUID?,
        limit: Int,
        changedOnly: Boolean,
    ) = safeDatabaseCall {
        require(limit in 1..200)
        val row = source.compare(company, baseline, current, after, limit, changedOnly)
        require(
            row.invalid == 0 &&
                listOf(
                        row.baselineCount,
                        row.currentCount,
                        row.matched,
                        row.added,
                        row.removed,
                        row.changed,
                        row.unchanged,
                    )
                    .all { it in 0..5000 }
        )
        require(
            row.matched + row.removed == row.baselineCount &&
                row.matched + row.added == row.currentCount &&
                row.changed + row.unchanged == row.matched
        )
        val summary =
            PayrollComparisonSummary(
                row.baselineCount,
                row.currentCount,
                row.matched,
                row.added,
                row.removed,
                row.changed,
                row.unchanged,
                amounts(row.baseline, row.baselineCount),
                amounts(row.current, row.currentCount),
            )
        val filteredCount =
            if (changedOnly) row.added + row.removed + row.changed
            else row.added + row.removed + row.matched
        require(row.items.size <= limit + 1 && row.items.size <= filteredCount)
        require(after != null || row.items.size == minOf(filteredCount, limit + 1))
        require(row.items.map { it.employmentId }.distinct().size == row.items.size)
        require(
            row.items.zipWithNext().all { (a, b) ->
                a.employmentId.toString() < b.employmentId.toString()
            }
        )
        val items =
            row.items.map { value ->
                require(after == null || value.employmentId.toString() > after.toString())
                fun employee(r: PayrollComparisonEmployeeRow?): PayrollComparisonEmployee? =
                    r?.let {
                        require(
                            it.name.isNotBlank() &&
                                it.name.length <= 200 &&
                                it.number.isNotBlank() &&
                                it.number.length <= 40
                        )
                        PayrollComparisonEmployee(it.number, it.name, amounts(it.amounts, 1))
                    }
                val b = employee(value.baseline)
                val c = employee(value.current)
                require(b != null || c != null)
                val change =
                    when {
                        b == null -> PayrollComparisonChange.ADDED
                        c == null -> PayrollComparisonChange.REMOVED
                        (c.amounts - b.amounts).let {
                            it.taxableGross.signum() != 0 ||
                                it.withheld.signum() != 0 ||
                                it.takeHome.signum() != 0
                        } -> PayrollComparisonChange.CHANGED
                        else -> PayrollComparisonChange.UNCHANGED
                    }
                require(
                    value.change == change.name &&
                        (!changedOnly || change != PayrollComparisonChange.UNCHANGED)
                )
                PayrollComparisonItem(value.employmentId, b, c, change)
            }
        PayrollComparisonData(
            summary,
            Page(
                items.take(limit),
                if (items.size > limit) items[limit - 1].employmentId.toString() else null,
            ),
        )
    }

    private fun amounts(row: PayrollComparisonAmountsRow, count: Int): PayrollComparisonAmounts {
        val factor = BigDecimal(count)
        require(
            listOf(row.gross, row.withheld, row.takeHome).all {
                it.stripTrailingZeros().scale() <= 2
            }
        )
        require(row.gross >= BigDecimal.ZERO && row.gross <= BigDecimal("50000000000") * factor)
        require(
            row.withheld >= BigDecimal("-600000000000") * factor &&
                row.withheld <= BigDecimal("50000000000") * factor
        )
        require(
            row.takeHome >= BigDecimal.ZERO && row.takeHome <= BigDecimal("650000000000") * factor
        )
        return PayrollComparisonAmounts(row.gross, row.withheld, row.takeHome)
    }
}
