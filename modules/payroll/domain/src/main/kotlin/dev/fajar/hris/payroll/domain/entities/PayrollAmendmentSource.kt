package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/** One retained assessment revision and the evidence used to calculate it. */
data class PayrollAmendmentSource(
    val companyId: UUID,
    val assessmentId: UUID,
    val revision: Long,
    val personId: UUID,
    val target: PayrollRunTarget,
    val opening: PayrollTaxOpening,
    val facts: PayrollCalculationFacts,
    val calculation: PayrollMonthlyCalculation,
    val workJobId: UUID,
    val availableWorkAdjustment: PayrollWorkAdjustment? = null,
)
