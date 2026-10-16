package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollAmendmentRemunerationResponse(
    val assessmentId: UUID,
    val compensation: CompensationTermsResponse?,
    val input: PayrollInputTermsResponse?,
    val workAdjustmentId: UUID?,
)
