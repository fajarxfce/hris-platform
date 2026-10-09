package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*

fun LeaveType.toResponse(): LeaveTypeResponse =
    LeaveTypeResponse(
        id,
        code,
        policy.name,
        effectiveFrom,
        policy.paid,
        policy.allowPartialDays,
        policy.minServiceMonths,
        policy.allowedContracts.map { it.name }.toSet(),
        policy.maxRequestDays,
        active,
        version,
        appliedRevision,
        policy.attachmentRequired,
    )

fun LeaveLedger.toResponse(): LeaveLedgerResponse =
    LeaveLedgerResponse(
        LeaveBalanceResponse(
            balance.year,
            balance.availableHalfDays.toLeaveDays(),
            balance.reservedHalfDays.toLeaveDays(),
            balance.consumedHalfDays.toLeaveDays(),
        ),
        Page(
            entries.items.map {
                LeaveLedgerEntryResponse(
                    it.id,
                    it.kind.name,
                    it.sourceId,
                    it.requestId,
                    it.availableDelta.toLeaveDays(),
                    it.reservedDelta.toLeaveDays(),
                    it.consumedDelta.toLeaveDays(),
                    it.actorId,
                    it.recordedAt,
                    it.reason,
                )
            },
            entries.nextCursor,
        ),
    )
