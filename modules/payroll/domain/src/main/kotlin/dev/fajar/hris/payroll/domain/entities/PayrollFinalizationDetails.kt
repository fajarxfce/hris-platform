package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.jobs.domain.entities.BackgroundJob

data class PayrollFinalizationDetails(val finalization: PayrollFinalization, val job: BackgroundJob)
