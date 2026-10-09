package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page

data class PayrollPeriodDetailsResponse(
    val period: PayrollPeriodResponse,
    val members: Page<PayrollPeriodMemberResponse>,
    val history: Page<PayrollPeriodChangeResponse>,
)
