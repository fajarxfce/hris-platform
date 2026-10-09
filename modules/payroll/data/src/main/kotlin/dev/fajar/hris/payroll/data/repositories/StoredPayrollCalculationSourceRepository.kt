package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.*
import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredPayrollCalculationSourceRepository(
    private val source: PayrollCalculationSourceDataSource,
    private val json: ObjectMapper,
) : PayrollCalculationSourceRepository {
    override fun workPeriod(
        company: UUID,
        month: YearMonth,
        lock: Boolean,
    ): Result<PayrollWorkPeriod?> = safeDatabaseCall {
        source.workPeriod(company, month.atDay(1), lock)?.let {
            PayrollWorkPeriod(
                it.id,
                YearMonth.from(it.month),
                it.version,
                it.status == "CLOSED",
                it.jobId,
                it.timezone,
            )
        }
    }

    override fun targets(
        company: UUID,
        period: UUID,
        month: YearMonth,
    ): Result<List<PayrollRunTarget>> = safeDatabaseCall {
        source.targets(company, period, month.atDay(1)).mapIndexed { i, r ->
            PayrollRunTarget(
                i + 1,
                r.employeeId,
                r.employeeNumber,
                r.employeeName,
                r.employmentVersion,
                r.compensationRevision,
                r.inputId,
                r.inputRevision,
                r.taxOpeningId,
                r.taxOpeningRevision,
            )
        }
    }

    override fun pendingLeave(company: UUID, period: UUID, month: YearMonth): Result<Boolean> =
        safeDatabaseCall {
            source.pendingLeave(company, period, month.atDay(1))
        }

    override fun workDays(
        company: UUID,
        job: UUID,
        employee: UUID,
        month: YearMonth,
    ): Result<List<PayrollWorkDay>?> = safeDatabaseCall {
        source.workSnapshot(company, job, employee)?.let { payload ->
            val raw = json.readValue(payload, PayrollClosedWorkData::class.java)
            require(
                raw.employeeId == employee &&
                    raw.month == month.toString() &&
                    raw.days.size == month.lengthOfMonth()
            )
            raw.days.map { day ->
                require(day.schedule.workDate == day.workDate && day.overtime.size <= 24)
                PayrollWorkDay(
                    day.workDate,
                    PayrollScheduleKind.valueOf(day.schedule.kind),
                    PayrollAttendanceKind.valueOf(day.fact),
                    day.schedule.holidayId != null,
                    day.overtime.map {
                        PayrollOvertimeEvidence(it.requestId, it.revision, it.approvedMinutes)
                    },
                )
            }
        }
    }

    override fun leaveDays(
        company: UUID,
        employee: UUID,
        month: YearMonth,
    ): Result<List<PayrollLeaveDay>> = safeDatabaseCall {
        source.leaveDays(company, employee, month.atDay(1), month.atEndOfMonth()).map {
            PayrollLeaveDay(
                it.requestId,
                it.revision,
                it.date,
                PayrollDayPortion.valueOf(it.portion),
                it.paid,
            )
        }
    }
}
