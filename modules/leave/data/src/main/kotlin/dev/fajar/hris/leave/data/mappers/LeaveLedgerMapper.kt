package dev.fajar.hris.leave.data.mappers

import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.schema.tables.records.LeaveLedgerRecord
import java.time.ZoneOffset
import java.util.UUID

fun LeaveLedgerRecord.toEntry(): LeaveLedgerEntry =
    LeaveLedgerEntry(
        id,
        employmentId,
        typeId,
        balanceYear,
        LeaveLedgerKind.valueOf(kind),
        sourceId,
        requestId,
        availableDelta,
        reservedDelta,
        consumedDelta,
        actorId,
        recordedAt.toInstant(),
        reason,
    )

fun LeaveLedgerEntry.toRow(company: UUID): LeaveLedgerRecord =
    LeaveLedgerRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.typeId = typeId
        it.balanceYear = year
        it.kind = kind.name
        it.sourceId = sourceId
        it.requestId = requestId
        it.availableDelta = availableDelta
        it.reservedDelta = reservedDelta
        it.consumedDelta = consumedDelta
        it.actorId = actorId
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
    }
