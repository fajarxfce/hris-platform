package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.models.ScheduledDayData
import dev.fajar.hris.workforce.domain.entities.*
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun OvertimeRequestsRecord.toOvertime(json: ObjectMapper): OvertimeRequest =
    OvertimeRequest(
        id,
        employmentId,
        employeeNumber,
        employeeName,
        requesterAccountId,
        authorId,
        createdAt.toInstant(),
        workDate,
        timezone,
        json.readValue(schedule.data(), ScheduledDayData::class.java).toDay(),
        OvertimeInterval(requestedStart.toInstant(), requestedEnd.toInstant(), requestedBreak),
        reason,
        OvertimeStatus.valueOf(status),
        actualStart?.let {
            OvertimeInterval(
                it.toInstant(),
                requireNotNull(actualEnd).toInstant(),
                requireNotNull(actualBreak),
            )
        },
        submittedBy,
        submittedAt?.toInstant(),
        approvalId,
        approvedMinutes,
        decidedAt?.toInstant(),
        version,
    )

fun OvertimeRequest.toRow(company: UUID, json: ObjectMapper): OvertimeRequestsRecord =
    OvertimeRequestsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.employeeNumber = employeeNumber
        it.employeeName = employeeName
        it.requesterAccountId = requesterAccountId
        it.authorId = authorId
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
        it.workDate = workDate
        it.timezone = timezone
        it.schedule = JSONB.valueOf(json.writeValueAsString(schedule.toData()))
        it.requestedStart = requested.startsAt.atOffset(ZoneOffset.UTC)
        it.requestedEnd = requested.endsAt.atOffset(ZoneOffset.UTC)
        it.requestedBreak = requested.breakMinutes
        it.reason = reason
        it.status = status.name
        it.actualStart = actual?.startsAt?.atOffset(ZoneOffset.UTC)
        it.actualEnd = actual?.endsAt?.atOffset(ZoneOffset.UTC)
        it.actualBreak = actual?.breakMinutes
        it.submittedBy = submittedBy
        it.submittedAt = submittedAt?.atOffset(ZoneOffset.UTC)
        it.approvalId = approvalId
        it.approvedMinutes = approvedMinutes
        it.decidedAt = decidedAt?.atOffset(ZoneOffset.UTC)
        it.version = version
    }

fun OvertimeRequest.toChange(
    company: UUID,
    revision: Long,
    kind: OvertimeChangeKind,
    actor: UUID,
    at: Instant,
    changeReason: String,
): OvertimeChangesRecord =
    OvertimeChangesRecord().also {
        it.companyId = company
        it.requestId = id
        it.revision = revision
        it.kind = kind.name
        it.status = status.name
        it.actualStart = actual?.startsAt?.atOffset(ZoneOffset.UTC)
        it.actualEnd = actual?.endsAt?.atOffset(ZoneOffset.UTC)
        it.actualBreak = actual?.breakMinutes
        it.approvedMinutes = approvedMinutes
        it.approvalId = approvalId
        it.actorId = actor
        it.recordedAt = at.atOffset(ZoneOffset.UTC)
        it.reason = changeReason
    }

fun OvertimeChangesRecord.toChange(): OvertimeChange =
    OvertimeChange(
        revision,
        OvertimeChangeKind.valueOf(kind),
        OvertimeStatus.valueOf(status),
        actualStart?.let {
            OvertimeInterval(
                it.toInstant(),
                requireNotNull(actualEnd).toInstant(),
                requireNotNull(actualBreak),
            )
        },
        approvedMinutes,
        approvalId,
        actorId,
        recordedAt.toInstant(),
        reason,
    )
