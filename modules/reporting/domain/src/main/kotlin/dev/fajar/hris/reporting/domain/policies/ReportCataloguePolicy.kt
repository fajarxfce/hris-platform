package dev.fajar.hris.reporting.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.ReportKind

/** Discovery and stored selections confer no source authority; each report checks it again. */
fun validateReportKindAccess(actor: Actor, kind: ReportKind): Result<Unit> =
    when (kind) {
        ReportKind.HEADCOUNT -> validateHeadcountAccess(actor)
        ReportKind.ATTENDANCE_EXCEPTIONS,
        ReportKind.ATTENDANCE_CLOSINGS -> validateAttendanceReportAccess(actor)
        ReportKind.LEAVE -> validateLeaveReportAccess(actor)
        ReportKind.OVERTIME -> validateOvertimeReportAccess(actor)
        ReportKind.EXPENSES -> validateExpenseReportAccess(actor)
        ReportKind.PAYROLL_COSTS -> validatePayrollCostReportAccess(actor)
    }

fun availableReportKinds(actor: Actor): List<ReportKind> =
    ReportKind.entries.filter { validateReportKindAccess(actor, it) is Result.Success }
