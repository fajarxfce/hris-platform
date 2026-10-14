package dev.fajar.hris.payroll.delivery.responses

import java.util.UUID

data class PayrollAmendmentSourceReferenceResponse(val assessmentId: UUID, val revision: Long)
