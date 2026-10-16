package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class PayrollAmendmentRemunerationRequest(
    val assessmentId: UUID,
    val compensation: CompensationTermsRequest? = null,
    val input: PayrollInputTermsRequest? = null,
    val workAdjustmentId: UUID? = null,
)
