package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.jobs.domain.entities.BackgroundJob

data class PayrollAmendmentPublicationDetails(
    val publication: PayrollAmendmentPublication,
    val job: BackgroundJob,
    val amendment: PayrollAmendmentSummary,
    val availableActions: Set<PayrollAmendmentPublicationAvailableAction> = emptySet(),
)
