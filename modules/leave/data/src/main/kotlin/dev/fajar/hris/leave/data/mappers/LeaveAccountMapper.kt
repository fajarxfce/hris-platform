package dev.fajar.hris.leave.data.mappers

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

fun LeaveAccountsRecord.toAccount(): LeaveAccount =
    LeaveAccount(id, employmentId, typeId, toBalance())
