package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.math.RoundingMode

const val INDONESIAN_OVERTIME_PP35_V1 = "ID-PP35-2021-v1"

/** One work date; callers combine its approved intervals before applying the first-hour band. */
fun calculateOvertimePay(
    ruleId: String,
    basicSalary: BigDecimal,
    fixedWage: BigDecimal,
    nonFixedWage: BigDecimal,
    kind: OvertimeDayKind,
    minutes: Int,
    rounding: EarningsRounding,
): Result<OvertimePay> {
    if (ruleId != INDONESIAN_OVERTIME_PP35_V1)
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_overtime_rule_unavailable"))
    if (
        listOf(basicSalary, fixedWage, nonFixedWage).any { !validPayrollAmount(it) } ||
            basicSalary.signum() <= 0
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_wage"))
    val fixed = basicSalary + fixedWage
    val all = fixed + nonFixedWage
    if (all > PAYROLL_MAXIMUM_MONTHLY_AMOUNT)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_wage"))
    val bands =
        when (kind) {
            OvertimeDayKind.WORKDAY -> listOf(60 to BigDecimal("1.5"), 180 to BigDecimal("2"))
            OvertimeDayKind.REST_OR_HOLIDAY_FIVE_DAYS ->
                listOf(480 to BigDecimal("2"), 60 to BigDecimal("3"), 180 to BigDecimal("4"))
            OvertimeDayKind.REST_OR_HOLIDAY_SIX_DAYS ->
                listOf(420 to BigDecimal("2"), 60 to BigDecimal("3"), 180 to BigDecimal("4"))
            OvertimeDayKind.HOLIDAY_SHORT_SIX_DAYS ->
                listOf(300 to BigDecimal("2"), 60 to BigDecimal("3"), 180 to BigDecimal("4"))
        }
    if (minutes <= 0)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_overtime_minutes"))
    if (minutes > bands.sumOf { it.first })
        return Result.Failed(
            Failure(
                FailureKind.CONFLICT,
                "payroll_overtime_review_required",
                parameters = mapOf("minutes" to minutes.toString(), "dayKind" to kind.name),
            )
        )
    val base = fixed.max(all.multiply(BigDecimal("0.75")))
    var remaining = minutes
    val segments = buildList {
        for ((limit, multiplier) in bands) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val consumed = minOf(remaining, limit)
            if (consumed > 0) add(OvertimePaySegment(consumed, multiplier))
            remaining -= consumed
        }
    }
    val weighted =
        segments.fold(BigDecimal.ZERO) { total, segment ->
            total + BigDecimal(segment.minutes) * segment.multiplier
        }
    val amount =
        base.multiply(weighted).divide(BigDecimal(173 * 60), 0, RoundingMode.valueOf(rounding.name))
    return Result.Success(
        OvertimePay(
            INDONESIAN_OVERTIME_PP35_V1,
            kind,
            base,
            base.divide(BigDecimal(173), 8, RoundingMode.HALF_UP),
            segments,
            amount,
            rounding,
        )
    )
}
