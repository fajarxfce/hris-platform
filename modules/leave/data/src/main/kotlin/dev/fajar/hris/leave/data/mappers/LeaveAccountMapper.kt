package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.LeaveBalanceSummaryRow
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord

fun LeaveAccountsRecord.toBalance(): LeaveBalance =
    LeaveBalance(
        balanceYear,
        Math.toIntExact(availableHalfDays),
        Math.toIntExact(reservedHalfDays),
        Math.toIntExact(consumedHalfDays),
        version,
        closingId != null,
        id,
    )

fun LeaveBalanceSummaryRow.toAccount(): LeaveAccount =
    LeaveAccount(
        account.id,
        account.employmentId,
        account.typeId,
        account.toBalance(),
        typeCode,
        typeName,
    )
