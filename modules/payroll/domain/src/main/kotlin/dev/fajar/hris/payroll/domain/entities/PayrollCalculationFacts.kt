package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentTerms
import java.time.LocalDate
import java.time.YearMonth

/** Acquired and versioned by the use case; this value performs no I/O. */
data class PayrollCalculationFacts(
    val month: YearMonth,
    val incomeDueDate: LocalDate,
    val plannedPaymentDate: LocalDate,
    val policy: PayrollPolicy,
    val compensation: CompensationTerms,
    val input: PayrollInputTerms,
    val employment: List<EmploymentTerms>,
    val workDays: List<PayrollWorkDay>,
    val leaveDays: List<PayrollLeaveDay>,
    val taxHistory: PayrollTaxOpeningTerms,
)
