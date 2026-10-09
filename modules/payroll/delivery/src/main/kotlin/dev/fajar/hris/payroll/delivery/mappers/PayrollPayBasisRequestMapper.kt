package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.payroll.delivery.requests.PayrollPayBasisRequest
import dev.fajar.hris.payroll.domain.entities.PayrollPayBasis

fun PayrollPayBasisRequest.toBasis() =
    PayrollPayBasis(
        overtimeRuleId,
        holidayAllowanceRuleId,
        proration,
        workWeek,
        shortestWorkDay,
        overtimeEligibility,
        overtimeExemptionReference,
        decimalAmount(regularNonFixedWage),
        serviceMonthConvention,
        rounding,
        reviewReference,
    )
