package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollCalculationFactsResponse(
    val month: String,
    val incomeDueDate: LocalDate,
    val plannedPaymentDate: LocalDate,
    val policy: PayrollPolicySnapshotResponse,
    val compensation: CompensationTermsResponse,
    val input: PayrollInputTermsResponse,
    val employment: List<PayrollEmploymentTermsResponse>,
    val workDays: List<PayrollWorkDayResponse>,
    val leaveDays: List<PayrollLeaveDayResponse>,
    val taxHistory: PayrollTaxOpeningTermsResponse,
)
