package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth

fun validatePayrollInput(month: YearMonth, terms: PayrollInputTerms, reason: String): Result<Unit> {
    if (month.year !in 2024..2100)
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_payroll_input",
                fields = mapOf("earningsMonth" to "out_of_range"),
            )
        )
    val fields = linkedMapOf<String, String>()
    if (reason.isBlank() || reason.length > 1000) fields["reason"] = "invalid"
    if (terms.earnings.size > 40) fields["terms.earnings"] = "too_many"
    if (terms.deductions.size > 20) fields["terms.deductions"] = "too_many"
    if (terms.dayResolutions.size > 62) fields["terms.dayResolutions"] = "too_many"
    if (
        terms.reviewReference.isBlank() ||
            terms.reviewReference.length > 200 ||
            terms.reviewReference.any(Char::isISOControl)
    )
        fields["terms.reviewReference"] = "invalid"
    if (!validPayrollAmount(terms.nonCashTaxable)) fields["terms.nonCashTaxable"] = "out_of_range"
    val units = terms.scheduledMonthUnits
    if (
        units != null &&
            (units.scale() !in 0..1 ||
                units.precision() > 3 ||
                units < BigDecimal.ONE ||
                units > BigDecimal(31) ||
                units.remainder(BigDecimal("0.5")).signum() != 0)
    )
        fields["terms.scheduledMonthUnits"] = "out_of_range"
    val code = Regex("[A-Z][A-Z0-9_]{0,31}")
    val earnings = terms.earnings.take(40)
    val deductions = terms.deductions.take(20)
    if (earnings.map { it.code }.distinct().size != earnings.size)
        fields["terms.earnings"] = "duplicate"
    if (deductions.map { it.code }.distinct().size != deductions.size)
        fields["terms.deductions"] = "duplicate"
    for ((i, e) in earnings.withIndex()) {
        if (!e.code.matches(code)) fields["terms.earnings[$i].code"] = "invalid"
        if (e.name.isBlank() || e.name.length > 100 || e.name.any(Char::isISOControl))
            fields["terms.earnings[$i].name"] = "invalid"
        if (!validPayrollAmount(e.amount) || e.amount.signum() <= 0)
            fields["terms.earnings[$i].amount"] = "positive_required"
    }
    for ((i, e) in deductions.withIndex()) {
        if (!e.code.matches(code)) fields["terms.deductions[$i].code"] = "invalid"
        if (e.name.isBlank() || e.name.length > 100 || e.name.any(Char::isISOControl))
            fields["terms.deductions[$i].name"] = "invalid"
        if (!validPayrollAmount(e.amount) || e.amount.signum() <= 0)
            fields["terms.deductions[$i].amount"] = "positive_required"
    }
    val slots = mutableSetOf<Pair<java.time.LocalDate, Int>>()
    for ((i, d) in terms.dayResolutions.take(62).withIndex()) {
        if (YearMonth.from(d.workDate) != month)
            fields["terms.dayResolutions[$i].workDate"] = "outside_period"
        if (
            d.reference.isBlank() || d.reference.length > 200 || d.reference.any(Char::isISOControl)
        )
            fields["terms.dayResolutions[$i].reference"] = "invalid"
        val portions =
            when (d.portion) {
                PayrollDayPortion.FULL -> listOf(1, 2)
                PayrollDayPortion.FIRST_HALF -> listOf(1)
                PayrollDayPortion.SECOND_HALF -> listOf(2)
            }
        for (portion in portions) if (!slots.add(d.workDate to portion))
            fields["terms.dayResolutions"] = "overlap"
    }
    terms.holidayAllowance?.let { h ->
        if (
            h.holidayDate.year !in 2024..2100 ||
                h.holidayDate < month.atDay(1).minusDays(62) ||
                h.holidayDate > month.atEndOfMonth().plusDays(62)
        )
            fields["terms.holidayAllowance.holidayDate"] = "out_of_range"
        if (h.continuousServiceFrom.year !in 1900..2100 || h.continuousServiceFrom >= h.holidayDate)
            fields["terms.holidayAllowance.continuousServiceFrom"] = "invalid"
        if (
            h.reviewReference.isBlank() ||
                h.reviewReference.length > 200 ||
                h.reviewReference.any(Char::isISOControl)
        )
            fields["terms.holidayAllowance.reviewReference"] = "invalid"
        if (
            h.promisedAmount != null &&
                (!validPayrollAmount(h.promisedAmount) ||
                    h.promisedAmount.stripTrailingZeros().scale() > 0)
        )
            fields["terms.holidayAllowance.promisedAmount"] = "out_of_range"
    }
    if (fields.isNotEmpty())
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_input", fields = fields)
        )
    if (
        earnings.fold(BigDecimal.ZERO) { sum, e -> sum + e.amount } + terms.nonCashTaxable >
            PAYROLL_MAXIMUM_MONTHLY_AMOUNT ||
            deductions.fold(BigDecimal.ZERO) { sum, e -> sum + e.amount } >
                PAYROLL_MAXIMUM_MONTHLY_AMOUNT
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_amount_limit"))
    return Result.Success(Unit)
}

fun payrollInputParts(terms: PayrollInputTerms): List<String?> =
    listOf(
        terms.nonCashTaxable.stripTrailingZeros().toPlainString(),
        terms.scheduledMonthUnits?.stripTrailingZeros()?.toPlainString(),
        terms.reviewReference,
        "earnings",
    ) +
        terms.earnings
            .sortedBy { it.code }
            .flatMap {
                listOf(
                    it.code,
                    it.name,
                    it.amount.stripTrailingZeros().toPlainString(),
                    it.taxable.toString(),
                )
            } +
        listOf("deductions") +
        terms.deductions
            .sortedBy { it.code }
            .flatMap { listOf(it.code, it.name, it.amount.stripTrailingZeros().toPlainString()) } +
        listOf("days") +
        terms.dayResolutions.sortedWith(compareBy({ it.workDate }, { it.portion.name })).flatMap {
            listOf(it.workDate.toString(), it.portion.name, it.disposition.name, it.reference)
        } +
        listOf("holiday") +
        (terms.holidayAllowance?.let {
            listOf(
                it.kind.name,
                it.holidayDate.toString(),
                it.continuousServiceFrom.toString(),
                it.priorPayment.name,
                it.reviewReference,
                it.promisedAmount?.stripTrailingZeros()?.toPlainString(),
            )
        } ?: emptyList())
