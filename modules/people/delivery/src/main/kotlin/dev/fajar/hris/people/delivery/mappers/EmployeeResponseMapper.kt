package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.entities.*

fun EmploymentTerms.toResponse(): TermsResponse =
    TermsResponse(
        effectiveFrom,
        contract.name,
        startDate,
        endDate,
        status.name,
        branchId,
        departmentId,
        positionId,
        costCenterId,
        managerId,
    )

fun Employee.toResponse(): EmployeeResponse =
    EmployeeResponse(
        id,
        companyId,
        employeeNumber,
        PersonSummaryResponse(person.id, person.accountId, person.legalName, person.email),
        terms.toResponse(),
        version,
        appliedRevision,
    )

fun EmploymentRevision.toResponse(): RevisionResponse =
    RevisionResponse(revision, terms.toResponse(), actorId, reason, recordedAt)
