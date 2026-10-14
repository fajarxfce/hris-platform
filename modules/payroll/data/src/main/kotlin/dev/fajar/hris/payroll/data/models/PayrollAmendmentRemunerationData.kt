package dev.fajar.hris.payroll.data.models

import java.util.UUID

data class PayrollAmendmentRemunerationData(
    val assessmentId: UUID,
    val compensation: CompensationTermsData?,
    val input: PayrollInputTermsData?,
)
