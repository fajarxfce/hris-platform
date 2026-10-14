package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/**
 * Corrected basis assembled by the use case; source acquisition never belongs to the calculator.
 */
data class PayrollAmendmentCorrection(val assessmentId: UUID, val facts: PayrollCalculationFacts)
