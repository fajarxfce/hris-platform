package dev.fajar.hris.payroll.data.models

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.*
import java.util.UUID

data class PayrollCalculationFactsData(
    val month: String,
    val incomeDueDate: LocalDate,
    val plannedPaymentDate: LocalDate,
    val policy: PayrollPolicySnapshotData,
    val compensation: CompensationTermsData,
    val input: PayrollInputTermsData,
    val employment: List<PayrollEmploymentTermsData>,
    val workDays: List<PayrollWorkDayData>,
    val leaveDays: List<PayrollLeaveDayData>,
    val taxHistory: PayrollTaxOpeningTermsData,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val workAdjustmentId: UUID? = null,
)
