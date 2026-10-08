package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.EmploymentTransferResponse
import dev.fajar.hris.people.domain.entities.EmploymentTransfer

fun EmploymentTransfer.toResponse() =
    EmploymentTransferResponse(
        id,
        sourceCompanyId,
        sourceEmploymentId,
        targetCompanyId,
        targetEmploymentId,
        sourceVersion,
        effectiveDate,
        actorId,
        reason,
        recordedAt,
    )
