package dev.fajar.hris.payroll.domain.entities

/** Explicitly reviewed references and proposed payroll-owned values. */
data class PayrollAmendmentTerms(
    val sourceVersions: List<PayrollAmendmentSourceReference>,
    val corrections: List<PayrollAmendmentRemuneration>,
)
