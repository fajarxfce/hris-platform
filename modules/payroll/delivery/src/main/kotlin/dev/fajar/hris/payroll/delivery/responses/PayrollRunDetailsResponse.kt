package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.*

data class PayrollRunDetailsResponse(
    val run: PayrollRunResponse,
    val attempts: List<PayrollRunAttemptResponse>,
    val job: PayrollRunProgressResponse,
    val results: Page<PayrollRunItemResponse>,
)
