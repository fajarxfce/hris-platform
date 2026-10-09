package dev.fajar.hris.payroll.domain.entities

import java.time.LocalDate

/** Payroll's read projection of an immutable, closed workforce day. */
data class PayrollWorkDay(
    val date: LocalDate,
    val schedule: PayrollScheduleKind,
    val attendance: PayrollAttendanceKind,
    val officialHoliday: Boolean,
    val overtime: List<PayrollOvertimeEvidence>,
)
