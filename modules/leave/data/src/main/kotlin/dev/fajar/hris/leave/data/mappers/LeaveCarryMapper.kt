package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.leave.data.models.LeaveCarryAllocationRow
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.leaveCarryValidity
import dev.fajar.hris.leave.domain.policies.validateLeaveCarryBucket
import dev.fajar.hris.schema.tables.records.*
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

fun LeaveCarryBucketsRecord.toCarryBucket(): LeaveCarryBucket {
    val validity =
        if (validThrough == null && expiresAt == null) null
        else LeaveCarryValidity(requireNotNull(validThrough), requireNotNull(expiresAt).toInstant())
    val months = validityMonths
    require((months == null) == (validity == null))
    ZoneId.of(companyTimezone)
    if (months != null) {
        require(months in 1..12 && balanceYear in 1901..2200)
        require(
            requireNotNull(validity).through == YearMonth.of(balanceYear, months).atEndOfMonth()
        )
        // UTC was validated when the award was created; later timezone data cannot rewrite it.
        require(requireNotNull(expiresAt).year in balanceYear..(balanceYear + 1))
    }
    val bucket =
        LeaveCarryBucket(
            id,
            balanceYear,
            initialHalfDays,
            availableHalfDays,
            reservedHalfDays,
            consumedHalfDays,
            expiredHalfDays,
            transferredHalfDays,
            deductedHalfDays,
            validity,
            version,
        )
    require(validateLeaveCarryBucket(bucket) is Result.Success)
    return bucket
}

fun LeaveCarryAward.toCarryRow(company: UUID): LeaveCarryBucketsRecord {
    require(bucket.version == 0L && bucket.availableHalfDays == bucket.initialHalfDays)
    require(validateLeaveCarryBucket(bucket) is Result.Success)
    val calculated = leaveCarryValidity(bucket.year, validityMonths, timezone)
    require(calculated is Result.Success && calculated.value == bucket.validity)
    return LeaveCarryBucketsRecord().also {
        it.companyId = company
        it.id = bucket.id
        it.employmentId = employeeId
        it.typeId = typeId
        it.balanceYear = bucket.year
        it.initialHalfDays = bucket.initialHalfDays
        it.availableHalfDays = bucket.availableHalfDays
        it.reservedHalfDays = 0
        it.consumedHalfDays = 0
        it.expiredHalfDays = 0
        it.transferredHalfDays = 0
        it.deductedHalfDays = 0
        it.version = 0L
        it.companyTimezone = timezone
        it.validityMonths = validityMonths
        it.validThrough = bucket.validity?.through
        it.expiresAt = bucket.validity?.expiresAt?.atOffset(ZoneOffset.UTC)
    }
}

fun LeaveCarryAllocationRow.toCarryAllocation(): LeaveCarryAllocation =
    LeaveCarryAllocation(allocation.bucketId, year, allocation.halfDays)

fun LeaveCarryAllocation.toCarryRow(company: UUID, request: UUID): LeaveCarryAllocationsRecord =
    LeaveCarryAllocationsRecord().also {
        it.companyId = company
        it.requestId = request
        it.bucketId = bucketId
        it.halfDays = halfDays
    }

fun LeaveCarryPosting.toCarryRow(company: UUID): LeaveCarryMovementsRecord {
    require(expectedVersion in 0 until 1000000000L && movement.halfDays in 1..732)
    return LeaveCarryMovementsRecord().also {
        it.companyId = company
        it.id = id
        it.bucketId = movement.bucketId
        it.version = expectedVersion + 1
        it.effect = movement.effect.name
        it.halfDays = movement.halfDays
        it.ledgerId = ledgerEntryId
        it.requestId = requestId
    }
}
