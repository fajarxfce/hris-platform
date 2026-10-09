package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page

data class PayrollPeriodDetails(
    val period: PayrollPeriod,
    val members: Page<PayrollPeriodMember>,
    val history: Page<PayrollPeriodChange>,
)
