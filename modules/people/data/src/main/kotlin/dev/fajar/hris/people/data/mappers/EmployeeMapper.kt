package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

fun EmployeesAtRecord.toEmployee(): Employee =
    Employee(
        id,
        companyId,
        employeeNumber,
        PersonProfile(personId, accountId, legalName, birthDate, nationality, email),
        EmploymentTerms(
            effectiveFrom,
            ContractKind.valueOf(contractKind),
            startDate,
            endDate,
            EmploymentStatus.valueOf(status),
            branchId,
            departmentId,
            positionId,
            costCenterId,
            managerId,
        ),
        managerAccountId,
        version,
        appliedRevision,
    )

fun EmploymentRevisionsRecord.toRevision(): EmploymentRevision =
    EmploymentRevision(
        revision,
        EmploymentTerms(
            effectiveFrom,
            ContractKind.valueOf(contractKind),
            startDate,
            endDate,
            EmploymentStatus.valueOf(status),
            branchId,
            departmentId,
            positionId,
            costCenterId,
            managerId,
        ),
        actorId,
        reason,
        recordedAt.toInstant(),
    )

fun PersonProfile.toRow(companyId: UUID): PersonsRecord =
    PersonsRecord().also {
        it.id = id
        it.ownerCompanyId = companyId
        it.accountId = accountId
        it.legalName = legalName
        it.birthDate = birthDate
        it.nationality = nationality
        it.email = email
        it.version = 0
    }

fun EmploymentTerms.toRow(
    companyId: UUID,
    id: UUID,
    revision: Long,
    actorId: UUID,
    reason: String,
): EmploymentRevisionsRecord =
    EmploymentRevisionsRecord().also {
        it.companyId = companyId
        it.employmentId = id
        it.revision = revision
        it.effectiveFrom = effectiveFrom
        it.contractKind = contract.name
        it.startDate = startDate
        it.endDate = endDate
        it.status = status.name
        it.branchId = branchId
        it.departmentId = departmentId
        it.positionId = positionId
        it.costCenterId = costCenterId
        it.managerId = managerId
        it.actorId = actorId
        it.reason = reason
    }
