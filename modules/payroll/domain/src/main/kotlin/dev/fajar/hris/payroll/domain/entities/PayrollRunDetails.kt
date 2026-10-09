package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.BackgroundJob
import java.time.*

data class PayrollRunDetails(
    val run: PayrollRun,
    val attempts: List<PayrollRunAttempt>,
    val job: BackgroundJob,
    val results: Page<PayrollRunItem>,
)
