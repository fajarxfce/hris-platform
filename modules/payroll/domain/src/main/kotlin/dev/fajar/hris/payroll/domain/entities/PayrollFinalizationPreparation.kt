package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.jobs.domain.entities.JobStatus

data class PayrollFinalizationPreparation(
    val run: PayrollRun,
    val totals: PayrollRunTotals,
    val latestReview: PayrollReview?,
    val approvalVersion: Long?,
    val latestFinalization: PayrollFinalization?,
    val latestJobStatus: JobStatus?,
    val blockingIssues: List<Failure>,
    val availableActions: Set<PayrollFinalizationAvailableAction>,
)
