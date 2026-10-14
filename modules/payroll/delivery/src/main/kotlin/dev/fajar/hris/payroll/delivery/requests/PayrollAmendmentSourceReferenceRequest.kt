package dev.fajar.hris.payroll.delivery.requests

import java.util.UUID

data class PayrollAmendmentSourceReferenceRequest(val assessmentId: UUID, val revision: Long)
