package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollPeriodMemberRow
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.YearMonth

fun PayrollPeriodsRecord.toPeriod() =
    PayrollPeriod(
        id,
        YearMonth.from(earningsMonth),
        plannedPaymentDate,
        timezone,
        participantCount,
        authorId,
        createdAt.toInstant(),
        PayrollPeriodStatus.valueOf(status),
        version,
        currentRunId,
    )

fun PayrollPeriodChangesRecord.toChange() =
    PayrollPeriodChange(
        revision,
        PayrollPeriodStatus.valueOf(status),
        actorId,
        recordedAt.toInstant(),
        reason,
        runId,
    )

fun PayrollPeriodMemberRow.toMember() =
    PayrollPeriodMember(employeeId, inputVersion, inputStatus?.let(PayrollInputStatus::valueOf))
