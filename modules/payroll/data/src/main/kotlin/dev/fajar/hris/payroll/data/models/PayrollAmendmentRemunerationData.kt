package dev.fajar.hris.payroll.data.models

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

data class PayrollAmendmentRemunerationData(
    val assessmentId: UUID,
    val compensation: CompensationTermsData?,
    val input: PayrollInputTermsData?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val workAdjustmentId: UUID? = null,
)
