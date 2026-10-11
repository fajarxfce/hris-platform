package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.EmploymentTransferReview

fun EmploymentTransferReview.toResponse() =
    EmploymentTransferReviewResponse(
        employee.toResponse(),
        TransferCompanyResponse(
            sourceCompany.id,
            sourceCompany.code,
            sourceCompany.name,
            sourceCompany.timezone,
        ),
        TransferCompanyResponse(
            targetCompany.id,
            targetCompany.code,
            targetCompany.name,
            targetCompany.timezone,
        ),
        sourceDate,
        targetDate,
        evaluatedAt,
        targetPermissions.sorted(),
    )
