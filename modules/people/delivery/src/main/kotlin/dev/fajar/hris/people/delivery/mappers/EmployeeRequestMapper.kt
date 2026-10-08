package dev.fajar.hris.people.delivery.mappers

import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.domain.entities.*

fun PersonRequest.toProfile(): PersonProfile =
    PersonProfile(id, accountId, legalName, birthDate, nationality, email)

fun TermsRequest.toTerms(): EmploymentTerms =
    EmploymentTerms(
        effectiveFrom,
        contract,
        startDate,
        endDate,
        status,
        branchId,
        departmentId,
        positionId,
        costCenterId,
        managerId,
    )
