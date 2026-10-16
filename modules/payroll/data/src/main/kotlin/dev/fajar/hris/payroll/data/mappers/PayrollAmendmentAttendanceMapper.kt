package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentAttendance
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

fun PayrollAmendmentAttendanceRow.toAmendmentAttendance(
    json: ObjectMapper
): PayrollAmendmentAttendance {
    val selectedMonth = YearMonth.from(month)
    require(month.dayOfMonth == 1)
    require(
        adjustment.companyId == companyId &&
            adjustment.employmentId == employeeId &&
            adjustment.jobId == workJobId
    )
    val metadata =
        PayrollWorkAdjustmentRow(
                adjustment.id,
                adjustment.revision,
                adjustment.workDate,
                adjustment.recordedAt,
                adjustment.reason,
            )
            .toWorkAdjustment(selectedMonth)
    val days =
        json
            .readValue(adjustment.payload.data(), PayrollClosedWorkData::class.java)
            .toPayrollWorkDays(employeeId, selectedMonth)
    return PayrollAmendmentAttendance(
        companyId,
        assessmentId,
        employeeId,
        workJobId,
        selectedMonth,
        metadata,
        latestAdjustmentId,
        days,
    )
}
