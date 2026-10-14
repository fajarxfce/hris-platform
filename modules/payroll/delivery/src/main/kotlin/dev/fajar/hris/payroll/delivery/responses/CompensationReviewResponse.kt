package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.YearMonth

data class CompensationReviewResponse(
    val asOf: YearMonth,
    val employee: CompensationEmployeeResponse,
    val current: CompensationResponse?,
    val effective: CompensationResponse?,
    val history: Page<CompensationRevisionResponse>,
    val canChange: Boolean,
)
