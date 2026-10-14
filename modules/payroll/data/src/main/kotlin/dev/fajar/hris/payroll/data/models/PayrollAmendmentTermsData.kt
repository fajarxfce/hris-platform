package dev.fajar.hris.payroll.data.models

data class PayrollAmendmentTermsData(
    val sourceVersions: List<PayrollAmendmentSourceReferenceData>,
    val corrections: List<PayrollAmendmentRemunerationData>,
)
