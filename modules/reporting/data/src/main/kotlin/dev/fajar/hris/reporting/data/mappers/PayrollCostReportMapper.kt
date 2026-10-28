package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.payroll.domain.policies.PAYROLL_MAXIMUM_MONTHLY_AMOUNT
import dev.fajar.hris.payroll.domain.policies.PAYROLL_MAXIMUM_YEARLY_AMOUNT
import dev.fajar.hris.reporting.data.dto.PayrollCostReportAggregateRow
import dev.fajar.hris.reporting.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun List<PayrollCostReportAggregateRow>.toPayrollCostReportAggregate(
    from: YearMonth,
    until: YearMonth,
): PayrollCostReportAggregate {
    val length = ChronoUnit.MONTHS.between(from, until)
    check(from.year in 2024..2100 && until.year in 2024..2100 && length in 0..11 && size in 1..13)
    val indexed = associateBy { it.month }
    check(
        indexed.size == size &&
            indexed.keys.all {
                it == null || (it.dayOfMonth == 1 && YearMonth.from(it) in from..until)
            }
    )
    val totals = requireNotNull(indexed[null]).toCounts()
    val months =
        (0L..length).map { offset ->
            val month = from.plusMonths(offset)
            PayrollCostReportMonth(
                month,
                indexed[month.atDay(1)]?.toCounts() ?: PayrollCostReportCounts(),
            )
        }
    check(months.all { it.counts.assessments == it.counts.employments })
    fun sum(value: (PayrollCostReportCounts) -> Long) =
        months.fold(0L) { count, row -> Math.addExact(count, value(row.counts)) }
    fun amount(value: (PayrollCostReportCounts) -> BigDecimal) =
        months.fold(BigDecimal.ZERO) { count, row -> count + value(row.counts) }
    check(
        sum { it.assessments } == totals.assessments &&
            sum { it.revisedAssessments } == totals.revisedAssessments
    )
    check(totals.employments in months.maxOf { it.counts.employments }..sum { it.employments })
    for (field in
        listOf<(PayrollCostReportCounts) -> BigDecimal>(
            { it.employerCost },
            { it.taxableGross },
            { it.employeeDeductions },
            { it.withheld },
            { it.takeHome },
        )) check(amount(field).compareTo(field(totals)) == 0)
    return PayrollCostReportAggregate(totals, java.util.List.copyOf(months))
}

private fun PayrollCostReportAggregateRow.toCounts(): PayrollCostReportCounts {
    check(
        missingValues == 0L &&
            assessments >= 0 &&
            employments in 0..assessments &&
            (assessments == 0L) == (employments == 0L)
    )
    check(revisedAssessments in 0..assessments)
    val maximum = PAYROLL_MAXIMUM_MONTHLY_AMOUNT * BigDecimal(assessments)
    // A final-period refund can include both retained and prior-employer withholding.
    val maximumRefund = PAYROLL_MAXIMUM_YEARLY_AMOUNT * BigDecimal(2) * BigDecimal(assessments)
    for (value in listOf(employerCost, taxableGross, employeeDeductions, withheld, takeHome)) check(
        value.scale() in 0..2 && value.precision() <= 34
    )
    check(employerCost >= BigDecimal.ZERO && employerCost <= maximum)
    check(taxableGross >= BigDecimal.ZERO && taxableGross <= employerCost)
    check(employeeDeductions >= BigDecimal.ZERO && employeeDeductions <= employerCost)
    check(withheld >= -maximumRefund && withheld <= employerCost)
    check(takeHome >= BigDecimal.ZERO && takeHome <= employerCost + maximumRefund)
    val paidBasis = takeHome + employeeDeductions + withheld
    check(paidBasis >= BigDecimal.ZERO && paidBasis <= employerCost)
    return PayrollCostReportCounts(
        assessments,
        employments,
        revisedAssessments,
        employerCost.setScale(2),
        taxableGross.setScale(2),
        employeeDeductions.setScale(2),
        withheld.setScale(2),
        takeHome.setScale(2),
    )
}
