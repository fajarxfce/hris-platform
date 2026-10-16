package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollClosedWorkData
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth
import java.util.UUID

fun PayrollClosedWorkData.toPayrollWorkDays(
    employee: UUID,
    selectedMonth: YearMonth,
): List<PayrollWorkDay> {
    require(employeeId == employee && month == selectedMonth.toString())
    require(days.size == selectedMonth.lengthOfMonth())
    return java.util.List.copyOf(
        days.mapIndexed { index, day ->
            require(
                day.workDate == selectedMonth.atDay(index + 1) &&
                    day.schedule.workDate == day.workDate
            )
            require(
                day.overtime.size <= 24 &&
                    day.overtime.map { it.requestId }.distinct().size == day.overtime.size
            )
            PayrollWorkDay(
                day.workDate,
                PayrollScheduleKind.valueOf(day.schedule.kind),
                PayrollAttendanceKind.valueOf(day.fact),
                day.schedule.holidayId != null,
                java.util.List.copyOf(
                    day.overtime.map {
                        require(it.revision >= 0 && it.approvedMinutes > 0)
                        PayrollOvertimeEvidence(it.requestId, it.revision, it.approvedMinutes)
                    }
                ),
            )
        }
    )
}
