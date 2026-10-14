package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.YearMonth

data class PayrollPolicyReview(
    val asOf: YearMonth,
    val current: PayrollPolicy?,
    val effective: PayrollPolicy?,
    val history: Page<PayrollPolicyRevision>,
)
