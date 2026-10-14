package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/** Retained evidence for an explicit first month; preparation reserves no publication. */
data class PayrollAmendmentPreparation(
    val firstAssessmentId: UUID,
    val sources: List<PayrollAmendmentSource>,
)
