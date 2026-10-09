package dev.fajar.hris.payroll.domain

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.time.DayOfWeek
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayUnitsPolicyTest : MonthlyPayrollFixture() {
    @Test
    fun `approved paid and unpaid halves resolve missing attendance without losing paid leave`() {
        val base = facts()
        val date = base.month.atDay(1)
        val value =
            base.copy(
                workDays =
                    base.workDays.map {
                        if (it.date == date) it.copy(attendance = PayrollAttendanceKind.UNRECORDED)
                        else it
                    },
                leaveDays =
                    listOf(
                        PayrollLeaveDay(
                            UUID.randomUUID(),
                            1,
                            date,
                            PayrollDayPortion.FIRST_HALF,
                            false,
                        ),
                        PayrollLeaveDay(
                            UUID.randomUUID(),
                            1,
                            date,
                            PayrollDayPortion.SECOND_HALF,
                            true,
                        ),
                    ),
            )
        val result = calculate(value)
        money("29.5", result.units.payable)
        money("0.5", result.units.unpaid)
        money("2950000", result.tax.takeHome)
        failure(
            value.copy(leaveDays = value.leaveDays + value.leaveDays.first()),
            "payroll_leave_overlap",
        )
    }

    @Test
    fun `missing attendance reports a translatable date and explicit halves complete the review`() {
        val base = facts()
        val date = base.month.atDay(2)
        val missing =
            base.copy(
                workDays =
                    base.workDays.map {
                        if (it.date == date) it.copy(attendance = PayrollAttendanceKind.UNRECORDED)
                        else it
                    }
            )
        val problem = failure(missing, "payroll_day_review_required")
        assertEquals("2026-09-02", problem.parameters["workDate"])
        assertEquals("1", problem.parameters["half"])
        val half =
            PayrollDayResolution(
                date,
                PayrollDayPortion.FIRST_HALF,
                PayrollDayDisposition.PAID,
                "Reviewed absence",
            )
        failure(
            missing.copy(input = missing.input.copy(dayResolutions = listOf(half))),
            "payroll_day_review_required",
        )
        val resolved =
            missing.copy(
                input =
                    missing.input.copy(
                        dayResolutions =
                            listOf(
                                half,
                                half.copy(
                                    portion = PayrollDayPortion.SECOND_HALF,
                                    disposition = PayrollDayDisposition.UNPAID,
                                ),
                            )
                    )
            )
        money("2950000", calculate(resolved).tax.takeHome)
    }

    @Test
    fun `a resolution cannot override worked time paid leave or a day outside employment`() {
        val base = facts()
        val day =
            PayrollDayResolution(
                base.month.atDay(1),
                PayrollDayPortion.FULL,
                PayrollDayDisposition.UNPAID,
                "Review",
            )
        val value = base.copy(input = base.input.copy(dayResolutions = listOf(day)))
        failure(value, "payroll_resolution_not_applicable")
        failure(
            value.copy(
                workDays =
                    base.workDays.map {
                        if (it.date == day.workDate)
                            it.copy(attendance = PayrollAttendanceKind.UNRECORDED)
                        else it
                    },
                leaveDays =
                    listOf(
                        PayrollLeaveDay(
                            UUID.randomUUID(),
                            1,
                            day.workDate,
                            PayrollDayPortion.FULL,
                            true,
                        )
                    ),
            ),
            "payroll_resolution_not_applicable",
        )
        failure(
            value.copy(
                employment =
                    listOf(
                        base.employment
                            .single()
                            .copy(
                                effectiveFrom = base.month.atDay(16),
                                startDate = base.month.atDay(16),
                            )
                    )
            ),
            "payroll_resolution_not_applicable",
        )
    }

    @Test
    fun `scheduled proration requires a complete reviewed denominator and never guesses it`() {
        val base = facts(salary = "2200000")
        assertEquals(22, base.workDays.count { it.schedule == PayrollScheduleKind.WORK })
        val value =
            base.copy(
                compensation =
                    base.compensation.copy(
                        payBasis =
                            base.compensation.payBasis!!.copy(
                                proration = PayrollProrationBasis.SCHEDULED_DAYS
                            )
                    ),
                input = base.input.copy(scheduledMonthUnits = amount("22")),
                leaveDays =
                    listOf(
                        PayrollLeaveDay(
                            UUID.randomUUID(),
                            1,
                            base.month.atDay(1),
                            PayrollDayPortion.FIRST_HALF,
                            false,
                        )
                    ),
            )
        val result = calculate(value)
        money("22", result.units.total)
        money("21.5", result.units.payable)
        money("2150000", result.taxInput.cashEarnings)
        failure(
            value.copy(input = value.input.copy(scheduledMonthUnits = null)),
            "payroll_scheduled_units_required",
        )
        failure(
            value.copy(input = value.input.copy(scheduledMonthUnits = amount("23"))),
            "payroll_scheduled_units_mismatch",
        )
        failure(
            value.copy(
                workDays =
                    value.workDays.map {
                        if (it.date == base.month.atDay(2))
                            it.copy(
                                schedule = PayrollScheduleKind.UNASSIGNED,
                                attendance = PayrollAttendanceKind.UNASSIGNED,
                            )
                        else it
                    }
            ),
            "payroll_schedule_incomplete",
        )
    }

    @Test
    fun `suspension and unsupported contracts remain reviewable instead of erasing wages`() {
        val base = facts()
        failure(
            base.copy(
                employment =
                    listOf(base.employment.single().copy(status = EmploymentStatus.SUSPENDED))
            ),
            "payroll_employment_review_required",
        )
        failure(
            base.copy(
                employment =
                    listOf(
                        base.employment.single().copy(endDate = base.month.atDay(1).minusDays(1))
                    )
            ),
            "payroll_employment_not_in_period",
        )
    }

    @Test
    fun `official shortest six day holiday selects its own overtime band with complete evidence`() {
        val base = facts(salary = "1730000")
        val date = base.month.atDay(5)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
        val item = PayrollOvertimeEvidence(UUID.randomUUID(), 2, 360)
        val value =
            base.copy(
                compensation =
                    base.compensation.copy(
                        payBasis =
                            base.compensation.payBasis!!.copy(
                                workWeek = PayrollWorkWeek.SIX_DAYS,
                                shortestWorkDay = DayOfWeek.SATURDAY,
                            )
                    ),
                workDays =
                    base.workDays.map {
                        if (it.date == date)
                            it.copy(officialHoliday = true, overtime = listOf(item))
                        else it
                    },
            )
        val result = calculate(value)
        assertEquals(
            OvertimeDayKind.HOLIDAY_SHORT_SIX_DAYS,
            result.overtime.single().calculation.dayKind,
        )
        money("130000", result.overtime.single().calculation.amount)
        failure(
            value.copy(
                workDays =
                    value.workDays.map {
                        if (it.date == date) it.copy(overtime = listOf(item, item)) else it
                    }
            ),
            "payroll_overtime_evidence_invalid",
        )
        failure(
            value.copy(
                workDays =
                    value.workDays.map {
                        if (it.date == date) it.copy(overtime = listOf(item.copy(minutes = 600)))
                        else it
                    }
            ),
            "payroll_overtime_review_required",
        )
    }
}
