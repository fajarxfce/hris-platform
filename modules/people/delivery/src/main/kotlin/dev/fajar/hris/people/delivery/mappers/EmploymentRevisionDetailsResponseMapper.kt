package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.EmploymentRevisionDetailsResponse
import dev.fajar.hris.people.domain.entities.EmploymentRevisionDetails

fun EmploymentRevisionDetails.toResponse() =
    EmploymentRevisionDetailsResponse(
        employeeId,
        version,
        companyDate,
        revision.toResponse(),
        canCancel,
    )
