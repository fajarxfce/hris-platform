package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.EmploymentDetails
import dev.fajar.hris.people.domain.entities.EmploymentUnitReference

fun EmploymentUnitReference.toEmploymentReference(): EmploymentUnitReferenceResponse =
    EmploymentUnitReferenceResponse(id, code, name, active)

fun EmploymentDetails.toResponse(): EmploymentDetailsResponse =
    EmploymentDetailsResponse(
        asOf,
        employee.toResponse(),
        branch?.toEmploymentReference(),
        department?.toEmploymentReference(),
        position?.toEmploymentReference(),
        costCenter?.toEmploymentReference(),
        manager?.let {
            EmploymentManagerReferenceResponse(it.id, it.employeeNumber, it.legalName, it.working)
        },
    )
