package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.jobs.domain.entities.JobKind
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.leave.data.models.LeaveCarryExpiryOverviewRow
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

fun LeaveCarryExpiriesRecord.toCarryExpiry(): LeaveCarryExpiry {
    require(balanceYear in 1901..2200 && version in 0..7 && totalEmployees in 1..5000)
    require(
        validThrough.year == balanceYear && validThrough.dayOfMonth == validThrough.lengthOfMonth()
    )
    require(expiresAt.isAfter(createdAt))
    ZoneId.of(companyTimezone)
    val source =
        when (sourceKind) {
            "BATCH" -> {
                require(batchId != null && closingId == null && employmentId == null)
                LeaveCarryExpirySource.Batch(batchId)
            }
            "CLOSING" -> {
                require(
                    batchId == null &&
                        closingId != null &&
                        employmentId != null &&
                        totalEmployees == 1
                )
                LeaveCarryExpirySource.Closing(closingId, employmentId)
            }
            else -> error("Unknown carry expiry source")
        }
    return LeaveCarryExpiry(
        id,
        source,
        typeId,
        balanceYear,
        companyTimezone,
        LeaveCarryValidity(validThrough, expiresAt.toInstant()),
        actorId,
        createdAt.toInstant(),
        reason,
        totalEmployees,
        jobId,
        version,
    )
}

fun LeaveCarryExpiry.toRow(company: UUID): LeaveCarryExpiriesRecord =
    LeaveCarryExpiriesRecord().also {
        it.companyId = company
        it.id = id
        when (val value = source) {
            is LeaveCarryExpirySource.Batch -> {
                it.sourceKind = "BATCH"
                it.batchId = value.batchId
            }
            is LeaveCarryExpirySource.Closing -> {
                it.sourceKind = "CLOSING"
                it.closingId = value.closingId
                it.employmentId = value.employeeId
            }
        }
        it.typeId = typeId
        it.balanceYear = year
        it.companyTimezone = timezone
        it.validThrough = validity.through
        it.expiresAt = validity.expiresAt.atOffset(ZoneOffset.UTC)
        it.actorId = actorId
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.totalEmployees = totalEmployees
        it.jobId = jobId
        it.version = version
    }

fun LeaveCarryExpiryAttemptsRecord.toExpiryAttempt(): LeaveCarryExpiryAttempt {
    require(attempt in 1..8 && baseCompleted in 0..5000)
    return LeaveCarryExpiryAttempt(jobId, attempt, baseCompleted, startedAt.toInstant(), reason)
}

fun LeaveCarryExpiryAttempt.toRow(company: UUID, expiry: UUID): LeaveCarryExpiryAttemptsRecord =
    LeaveCarryExpiryAttemptsRecord().also {
        it.companyId = company
        it.expiryId = expiry
        it.jobId = jobId
        it.attempt = attempt
        it.baseCompleted = baseCompleted
        it.startedAt = startedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun LeaveCarryExpiryOutcomesRecord.toExpiryOutcome(): LeaveCarryExpiryOutcome {
    require(ordinal in 1..5000 && halfDays in 0..732)
    val result = LeaveCarryExpiryOutcomeStatus.valueOf(status)
    require(
        if (result == LeaveCarryExpiryOutcomeStatus.EXPIRED) halfDays > 0 && ledgerId != null
        else halfDays == 0 && ledgerId == null
    )
    return LeaveCarryExpiryOutcome(
        ordinal,
        employmentId,
        jobId,
        result,
        halfDays,
        ledgerId,
        recordedAt.toInstant(),
    )
}

fun LeaveCarryExpiryOutcome.toRow(company: UUID, expiry: UUID): LeaveCarryExpiryOutcomesRecord =
    LeaveCarryExpiryOutcomesRecord().also {
        it.companyId = company
        it.expiryId = expiry
        it.ordinal = ordinal
        it.employmentId = employeeId
        it.jobId = jobId
        it.status = status.name
        it.halfDays = halfDays
        it.ledgerId = ledgerId
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
    }

fun LeaveCarryExpiryOverviewRow.toCarryExpiryOverview(): LeaveCarryExpiryOverview {
    require(
        jobCompanyId == expiry.companyId &&
            jobId == expiry.jobId &&
            jobKind == JobKind.LEAVE_CARRY_EXPIRY.name
    )
    val origin = expiry.toCarryExpiry()
    val state = JobStatus.valueOf(status)
    require(
        typeCode.isNotBlank() &&
            typeName.isNotBlank() &&
            completedEmployees in 0..origin.totalEmployees
    )
    require(state != JobStatus.SUCCEEDED || completedEmployees == origin.totalEmployees)
    return LeaveCarryExpiryOverview(
        origin,
        typeCode,
        typeName,
        state,
        completedEmployees,
        failureCode,
    )
}
