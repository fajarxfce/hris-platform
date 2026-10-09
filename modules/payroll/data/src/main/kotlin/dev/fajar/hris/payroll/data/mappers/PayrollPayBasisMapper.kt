package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollPayBasisData
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.DayOfWeek

fun PayrollPayBasis.toData() =
    PayrollPayBasisData(
        overtimeRuleId,
        holidayAllowanceRuleId,
        proration.name,
        workWeek.name,
        shortestWorkDay?.name,
        overtimeEligibility.name,
        overtimeExemptionReference,
        regularNonFixedWage.toPlainString(),
        serviceMonthConvention.name,
        rounding.name,
        reviewReference,
    )

fun PayrollPayBasisData.toBasis() =
    PayrollPayBasis(
        overtimeRuleId,
        holidayAllowanceRuleId,
        PayrollProrationBasis.valueOf(proration),
        PayrollWorkWeek.valueOf(workWeek),
        shortestWorkDay?.let(DayOfWeek::valueOf),
        OvertimeEligibility.valueOf(overtimeEligibility),
        overtimeExemptionReference,
        BigDecimal(regularNonFixedWage),
        ServiceMonthConvention.valueOf(serviceMonthConvention),
        EarningsRounding.valueOf(rounding),
        reviewReference,
    )
