package dev.fajar.hris.reporting.domain.entities

/** Stable identities shared by report results, discovery and retained filter definitions. */
enum class ReportKind(val definitionVersion: String) {
    HEADCOUNT("headcount.v1"),
    ATTENDANCE_EXCEPTIONS("attendance-exceptions.v1"),
    ATTENDANCE_CLOSINGS("attendance-closings.v1"),
    LEAVE("leave.v1"),
    OVERTIME("overtime.v1"),
    EXPENSES("expenses.v1"),
    PAYROLL_COSTS("payroll-costs.v1"),
}
