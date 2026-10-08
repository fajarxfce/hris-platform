package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.domain.entities.EmploymentTransfer
import dev.fajar.hris.schema.tables.records.EmploymentTransfersRecord
import java.time.ZoneOffset.UTC

fun EmploymentTransfersRecord.toTransfer() =
    EmploymentTransfer(
        id,
        sourceCompanyId,
        sourceEmploymentId,
        targetCompanyId,
        targetEmploymentId,
        personId,
        sourceVersion,
        effectiveDate,
        actorId,
        reason,
        recordedAt.toInstant(),
    )

fun EmploymentTransfer.toRecord() =
    EmploymentTransfersRecord().also {
        it.id = id
        it.sourceCompanyId = sourceCompanyId
        it.sourceEmploymentId = sourceEmploymentId
        it.targetCompanyId = targetCompanyId
        it.targetEmploymentId = targetEmploymentId
        it.personId = personId
        it.sourceVersion = sourceVersion
        it.effectiveDate = effectiveDate
        it.actorId = actorId
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(UTC)
    }
