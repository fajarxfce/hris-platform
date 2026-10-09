package dev.fajar.hris.payroll.data.models

import java.time.*

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
)
