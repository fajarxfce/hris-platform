package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.YearMonth

data class PayrollPolicyReviewResponse(
    val asOf: YearMonth,
    val current: PayrollPolicyResponse?,
    val effective: PayrollPolicyResponse?,
    val history: Page<PayrollPolicyRevisionResponse>,
)
