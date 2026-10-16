package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/** Proposed payroll-owned terms, distinct from the retained source revisions. */
data class PayrollAmendmentRemuneration(
    val assessmentId: UUID,
    val compensation: CompensationTerms? = null,
    val input: PayrollInputTerms? = null,
    val workAdjustmentId: UUID? = null,
)
