package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

data class PayrollAmendmentSourceReference(val assessmentId: UUID, val revision: Long)
