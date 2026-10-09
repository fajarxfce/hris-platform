package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.LeavePolicySnapshotData
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun LeaveAccrualPostingsRecord.toPosting(json: ObjectMapper): LeaveAccrualPosting =
    LeaveAccrualPosting(
        id,
        employmentId,
        typeId,
        YearMonth.from(processedMonth),
        LeaveAccrualAward(YearMonth.from(periodKey), eligibleFrom, eligibleUntil, halfDays),
        LeaveAccrualFrequency.valueOf(frequency),
        json.readValue(policySnapshot.data(), LeavePolicySnapshotData::class.java).toSnapshot(),
        employmentVersion,
        balanceVersion,
        actorId,
        recordedAt.toInstant(),
        reason,
    )

fun LeaveAccrualPosting.toRow(company: UUID, json: ObjectMapper): LeaveAccrualPostingsRecord =
    LeaveAccrualPostingsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.typeId = typeId
        it.balanceYear = processedMonth.year
        it.periodKey = award.period.atDay(1)
        it.processedMonth = processedMonth.atDay(1)
        it.frequency = frequency.name
        it.halfDays = award.halfDays
        it.eligibleFrom = award.eligibleFrom
        it.eligibleUntil = award.eligibleUntil
        it.policyRevision = policy.revision
        it.policySnapshot = JSONB.valueOf(json.writeValueAsString(policy.toData()))
        it.employmentVersion = employmentVersion
        it.balanceVersion = balanceVersion
        it.actorId = actorId
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }

fun LeaveYearClosingsRecord.toClosing(json: ObjectMapper): LeaveYearClosing =
    LeaveYearClosing(
        id,
        employmentId,
        typeId,
        balanceYear,
        sourceVersion,
        availableHalfDays,
        consumedHalfDays,
        destinationVersion,
        LeaveYearRollover(carryHalfDays, expireHalfDays),
        json.readValue(policySnapshot.data(), LeavePolicySnapshotData::class.java).toSnapshot(),
        actorId,
        recordedAt.toInstant(),
        reason,
    )

fun LeaveYearClosing.toRow(company: UUID, json: ObjectMapper): LeaveYearClosingsRecord =
    LeaveYearClosingsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.typeId = typeId
        it.balanceYear = year
        it.sourceVersion = sourceVersion
        it.destinationVersion = destinationVersion
        it.availableHalfDays = availableHalfDays
        it.consumedHalfDays = consumedHalfDays
        it.carryHalfDays = rollover.carryHalfDays
        it.expireHalfDays = rollover.expireHalfDays
        it.policyRevision = policy.revision
        it.policySnapshot = JSONB.valueOf(json.writeValueAsString(policy.toData()))
        it.actorId = actorId
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }
