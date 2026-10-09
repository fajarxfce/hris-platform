package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.ContractKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.temporal.ChronoUnit

const val INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1 = "ID-PERMENAKER6-2016-v1"

fun assessHolidayService(
    ruleId: String,
    continuousFrom: LocalDate,
    employmentEnd: LocalDate?,
    contract: ContractKind,
    holiday: LocalDate,
    convention: ServiceMonthConvention,
): Result<HolidayService> {
    if (ruleId != INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1)
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_thr_rule_unavailable"))
    if (
        continuousFrom.year !in 1900..2100 ||
            holiday.year !in 2016..2100 ||
            continuousFrom >= holiday ||
            (employmentEnd != null &&
                (employmentEnd.year !in 1900..2100 || employmentEnd < continuousFrom)) ||
            contract !in setOf(ContractKind.PERMANENT, ContractKind.FIXED_TERM)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_thr_service"))
    if (
        employmentEnd != null &&
            employmentEnd < holiday &&
            (contract != ContractKind.PERMANENT ||
                employmentEnd < holiday.minusDays(30) ||
                employmentEnd.year != holiday.year)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_thr_ineligible"))
    val until =
        if (employmentEnd != null && employmentEnd < holiday) employmentEnd.plusDays(1) else holiday
    if (continuousFrom.plusMonths(1) > until)
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_thr_ineligible"))
    return measureContinuousService(continuousFrom, until, convention)
}

/** Calendar-anniversary fractions preserve an exact ratio until the payment rounding boundary. */
fun measureContinuousService(
    continuousFrom: LocalDate,
    until: LocalDate,
    convention: ServiceMonthConvention,
): Result<HolidayService> {
    if (
        continuousFrom.year !in 1900..2100 ||
            until.year !in 1900..2100 ||
            continuousFrom >= until ||
            continuousFrom.plusMonths(1) > until
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_thr_service"))
    var months =
        ChronoUnit.MONTHS.between(YearMonth.from(continuousFrom), YearMonth.from(until)).toInt()
    if (continuousFrom.plusMonths(months.toLong()) > until) months--
    if (months !in 1..2400)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_thr_service"))
    val anchor = continuousFrom.plusMonths(months.toLong())
    val next = continuousFrom.plusMonths(months.toLong() + 1)
    val remaining =
        if (convention == ServiceMonthConvention.COMPLETE_MONTHS) 0
        else ChronoUnit.DAYS.between(anchor, until).toInt()
    return Result.Success(
        HolidayService(
            continuousFrom,
            until,
            convention,
            months,
            remaining,
            ChronoUnit.DAYS.between(anchor, next).toInt(),
        )
    )
}

fun calculateHolidayAllowance(
    ruleId: String,
    service: HolidayService,
    holiday: LocalDate,
    monthlyWage: BigDecimal,
    promisedAmount: BigDecimal?,
    rounding: EarningsRounding,
): Result<HolidayAllowance> {
    if (ruleId != INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1)
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_thr_rule_unavailable"))
    if (
        holiday.year !in 2016..2100 ||
            !validPayrollAmount(monthlyWage) ||
            monthlyWage.signum() <= 0 ||
            (promisedAmount != null &&
                (!validPayrollAmount(promisedAmount) ||
                    promisedAmount.stripTrailingZeros().scale() > 0)) ||
            service.wholeMonths !in 1..2400 ||
            service.anniversaryDays !in 28..31 ||
            service.remainingDays !in 0 until service.anniversaryDays ||
            (service.convention == ServiceMonthConvention.COMPLETE_MONTHS &&
                service.remainingDays != 0) ||
            service.assessedUntil > holiday ||
            service.continuousFrom >= service.assessedUntil
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_thr_input"))
    val checked =
        measureContinuousService(service.continuousFrom, service.assessedUntil, service.convention)
    if (checked !is Result.Success || checked.value != service)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_thr_input"))
    val numerator =
        if (service.wholeMonths >= 12) 12 * service.anniversaryDays
        else service.wholeMonths * service.anniversaryDays + service.remainingDays
    val minimum =
        monthlyWage
            .multiply(BigDecimal(numerator))
            .divide(
                BigDecimal(12 * service.anniversaryDays),
                0,
                RoundingMode.valueOf(rounding.name),
            )
    if (promisedAmount != null && promisedAmount < minimum)
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_thr_below_minimum"))
    val amount = promisedAmount ?: minimum
    return Result.Success(
        HolidayAllowance(
            INDONESIAN_HOLIDAY_ALLOWANCE_2016_V1,
            holiday,
            holiday.minusDays(7),
            service,
            monthlyWage,
            minimum,
            amount - minimum,
            amount,
            rounding,
        )
    )
}
