package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.data.models.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.domain.entities.CalendarOrigin
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun LeavePolicySnapshot.toData(): LeavePolicySnapshotData =
    LeavePolicySnapshotData(typeId, code, revision, policy.toData())

fun LeavePolicySnapshotData.toSnapshot(): LeavePolicySnapshot =
    LeavePolicySnapshot(typeId, code, revision, policy.toPolicy())

fun LeaveDay.toData(): LeaveDayData =
    LeaveDayData(
        workDate,
        portion.name,
        startsAt,
        endsAt,
        plannedMinutes,
        chargedMinutes,
        shiftId,
        shiftRevision,
        scheduleOrigin.name,
        scheduleRevision,
    )

fun LeaveDayData.toDay(): LeaveDay =
    LeaveDay(
        workDate,
        LeavePortion.valueOf(portion),
        startsAt,
        endsAt,
        plannedMinutes,
        chargedMinutes,
        shiftId,
        shiftRevision,
        CalendarOrigin.valueOf(scheduleOrigin),
        scheduleRevision,
    )

fun LeaveRequestsRecord.toRequest(json: ObjectMapper): LeaveRequest =
    LeaveRequest(
        id,
        employmentId,
        employeeNumber,
        employeeName,
        ownerAccountId,
        authorId,
        submittedAt.toInstant(),
        json.readValue(typeSnapshot.data(), LeavePolicySnapshotData::class.java).toSnapshot(),
        json.readValue(days.data(), Array<LeaveDayData>::class.java).map { it.toDay() },
        reason,
        LeaveStatus.valueOf(status),
        approvalId,
        cancellationApprovalId,
        version,
    )

fun LeaveRequest.toRow(company: UUID, json: ObjectMapper): LeaveRequestsRecord =
    LeaveRequestsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.ownerAccountId = ownerAccountId
        it.authorId = authorId
        it.submittedAt = submittedAt.atOffset(ZoneOffset.UTC)
        it.typeId = policy.typeId
        it.typeRevision = policy.revision
        it.typeSnapshot = JSONB.valueOf(json.writeValueAsString(policy.toData()))
        it.days = JSONB.valueOf(json.writeValueAsString(days.map { day -> day.toData() }))
        it.startsOn = days.minOf { day -> day.workDate }
        it.endsOn = days.maxOf { day -> day.workDate }
        it.halfDays = days.sumOf { day -> day.portion.halfDays }
        it.reason = reason
        it.status = status.name
        it.approvalId = approvalId
        it.cancellationApprovalId = cancellationApprovalId
        it.version = version
    }

fun LeaveRequest.toAllocations(company: UUID): List<LeaveAllocationsRecord> =
    days.flatMap { day ->
        listOf(1, 2)
            .filter { (day.portion.mask and it) != 0 }
            .map { slot ->
                LeaveAllocationsRecord().also {
                    it.companyId = company
                    it.employmentId = employeeId
                    it.workDate = day.workDate
                    it.slot = slot.toShort()
                    it.requestId = id
                }
            }
    }

fun LeaveRequestSummaryRow.toSummary(): LeaveRequestSummary =
    LeaveRequestSummary(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        typeCode,
        typeName,
        from,
        until,
        halfDays,
        LeaveStatus.valueOf(status),
        submittedAt.toInstant(),
        version,
    )

fun LeaveRequestChangesRecord.toChange(): LeaveRequestChange =
    LeaveRequestChange(
        revision,
        LeaveChangeKind.valueOf(kind),
        LeaveStatus.valueOf(status),
        cancellationApprovalId,
        actorId,
        recordedAt.toInstant(),
        reason,
    )
