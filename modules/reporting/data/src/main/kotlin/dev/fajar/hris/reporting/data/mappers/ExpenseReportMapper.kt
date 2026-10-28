package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.expenses.domain.policies.EXPENSE_MAXIMUM_AMOUNT
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.domain.entities.*
import java.math.BigDecimal
import java.math.RoundingMode

fun List<ExpenseReportAggregateRow>.toExpenseReportAggregate(): ExpenseReportAggregate {
    check(size in 1..7)
    val indexed = associateBy { it.status?.let(ExpenseClaimStatus::valueOf) }
    check(indexed.size == size)
    val total = requireNotNull(indexed[null]).toCounts()
    val statuses =
        ExpenseClaimStatus.entries.map {
            ExpenseClaimStatusCounts(it, indexed[it]?.toCounts() ?: ExpenseReportCounts())
        }
    fun sum(value: (ExpenseReportCounts) -> Long) =
        statuses.fold(0L) { count, row -> Math.addExact(count, value(row.counts)) }
    check(sum { it.claims } == total.claims && sum { it.lines } == total.lines)
    check(
        statuses
            .fold(BigDecimal.ZERO) { amount, row -> amount + row.counts.amount }
            .compareTo(total.amount) == 0
    )
    check(total.employments in statuses.maxOf { it.counts.employments }..sum { it.employments })
    return ExpenseReportAggregate(total, statuses)
}

private fun ExpenseReportAggregateRow.toCounts(): ExpenseReportCounts {
    check(claims >= 0 && employments in 0..claims && (claims == 0L) == (employments == 0L))
    check(lines in claims..Math.multiplyExact(claims, 20))
    val exact = amount.setScale(2, RoundingMode.UNNECESSARY)
    check(
        exact >= BigDecimal.valueOf(lines, 2) &&
            exact <= EXPENSE_MAXIMUM_AMOUNT.multiply(BigDecimal.valueOf(claims))
    )
    return ExpenseReportCounts(claims, employments, lines, exact)
}

fun ExpenseReportClaimRow.toExpenseReportClaim(): ExpenseReportClaim {
    check(version >= 0 && submissionNumber in 1..20 && lines in 1..20)
    check(employeeNumber.isNotBlank() && employeeNumber.length <= 32)
    check(employeeName.isNotBlank() && employeeName.length <= 200)
    check(title.isNotBlank() && title.length <= 160 && submittedAt.year in 1..9999)
    val exact = amount.setScale(2, RoundingMode.UNNECESSARY)
    val whole = submissionAmount.setScale(2, RoundingMode.UNNECESSARY)
    check(
        exact >= BigDecimal.valueOf(lines, 2) && exact <= whole && whole <= EXPENSE_MAXIMUM_AMOUNT
    )
    return ExpenseReportClaim(
        id,
        submissionId,
        submissionNumber,
        employeeId,
        employeeNumber,
        employeeName,
        title,
        ExpenseClaimStatus.valueOf(status),
        submittedAt.toInstant(),
        version,
        lines,
        exact,
        whole,
    )
}
