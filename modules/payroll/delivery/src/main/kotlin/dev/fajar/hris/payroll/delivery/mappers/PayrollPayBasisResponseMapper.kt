package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.PayrollPayBasisResponse
import dev.fajar.hris.payroll.domain.entities.PayrollPayBasis

fun PayrollPayBasis.toResponse() =
    PayrollPayBasisResponse(
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
