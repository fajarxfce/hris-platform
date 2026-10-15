package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.data.models.LeaveBalanceRow
import dev.fajar.hris.leave.data.models.LeaveBalanceSummaryRow
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.validateLeaveCarryBalance
import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord
import dev.fajar.hris.schema.tables.records.LeaveCarryBucketsRecord

fun LeaveAccountsRecord.toBalance(carry: LeaveCarryBucketsRecord? = null): LeaveBalance {
    val balance =
        LeaveBalance(
            balanceYear,
            Math.toIntExact(availableHalfDays),
            Math.toIntExact(reservedHalfDays),
            Math.toIntExact(consumedHalfDays),
            version,
            closingId != null,
            id,
        )
    val award =
        carry?.let {
            require(
                it.companyId == companyId &&
                    it.employmentId == employmentId &&
                    it.typeId == typeId &&
                    it.balanceYear == balanceYear
            )
            val bucket = it.toCarryBucket()
            require(validateLeaveCarryBalance(balance, bucket) is Result.Success)
            require(
                !balance.closed || (bucket.availableHalfDays == 0 && bucket.reservedHalfDays == 0)
            )
            LeaveCarryAward(bucket, employmentId, typeId, it.companyTimezone, it.validityMonths)
        }
    return balance.copy(carry = award)
}

fun LeaveBalanceRow.toBalance(): LeaveBalance = account.toBalance(carry)

fun LeaveBalanceSummaryRow.toAccount(): LeaveAccount =
    LeaveAccount(
        account.id,
        account.employmentId,
        account.typeId,
        account.toBalance(carry),
        typeCode,
        typeName,
    )
