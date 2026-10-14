package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.data.models.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.OrganizationUnitsRecord
import java.time.ZoneId

fun SelfProfileCandidateRow.toSelfProfileCandidate(): SelfProfileCandidate =
    SelfProfileCandidate(
        id,
        employee?.toEmployee(),
        ownerCompanyId,
        profileVersion,
        branch?.toSelfProfileUnit(),
        department?.toSelfProfileUnit(),
        position?.toSelfProfileUnit(),
        costCenter?.toSelfProfileUnit(),
        manager?.let {
            SelfProfileManagerCandidate(
                it.id,
                it.employeeNumber,
                it.legalName,
                EmploymentStatus.valueOf(it.status),
                it.startDate,
                it.endDate,
            )
        },
        nextRevision,
        nextManagerRevision,
    )

fun OrganizationUnitsRecord.toSelfProfileUnit() =
    EmploymentUnitReference(
        requireNotNull(id),
        requireNotNull(code),
        requireNotNull(name),
        requireNotNull(active),
    )

fun EmploymentUnitReference.toSelfProfileData() = SelfProfileUnitData(id, code, name, active)

fun SelfProfileUnitData.toSelfProfileUnit() = EmploymentUnitReference(id, code, name, active)

fun SelfProfileSnapshot.toSelfProfilePayload(): SelfProfilePayload {
    val employee = details.employee
    val person = employee.person
    val terms = employee.terms
    return SelfProfilePayload(
        employee.id,
        employee.companyId,
        details.asOf,
        timezone,
        employee.employeeNumber,
        employee.version,
        employee.appliedRevision,
        person.id,
        requireNotNull(person.accountId),
        ownerCompanyId,
        profileVersion,
        person.legalName,
        person.birthDate,
        person.nationality,
        person.email,
        terms.effectiveFrom,
        terms.contract.name,
        terms.startDate,
        terms.endDate,
        terms.status.name,
        terms.branchId,
        terms.departmentId,
        terms.positionId,
        terms.costCenterId,
        terms.managerId,
        details.branch?.toSelfProfileData(),
        details.department?.toSelfProfileData(),
        details.position?.toSelfProfileData(),
        details.costCenter?.toSelfProfileData(),
        details.manager?.let {
            SelfProfileManagerData(it.id, it.employeeNumber, it.legalName, it.working)
        },
    )
}

fun SelfProfilePayload.toSelfProfileSnapshot(): SelfProfileSnapshot {
    require(schema == 1 && employmentVersion >= 0 && profileVersion >= 0)
    require(appliedRevision in 0..employmentVersion)
    ZoneId.of(timezone)
    require(legalName.isNotBlank() && employeeNumber.isNotBlank() && nationality.length == 2)
    require(branch == null || branch.id == branchId)
    require(department == null || department.id == departmentId)
    require(position == null || position.id == positionId)
    require(costCenter == null || costCenter.id == costCenterId)
    require(manager == null || manager.id == managerId)
    return SelfProfileSnapshot(
        EmploymentDetails(
            asOf,
            Employee(
                id,
                companyId,
                employeeNumber,
                PersonProfile(personId, accountId, legalName, birthDate, nationality, email),
                EmploymentTerms(
                    effectiveFrom,
                    ContractKind.valueOf(contract),
                    startDate,
                    endDate,
                    EmploymentStatus.valueOf(status),
                    branchId,
                    departmentId,
                    positionId,
                    costCenterId,
                    managerId,
                ),
                null,
                employmentVersion,
                appliedRevision,
            ),
            branch?.toSelfProfileUnit(),
            department?.toSelfProfileUnit(),
            position?.toSelfProfileUnit(),
            costCenter?.toSelfProfileUnit(),
            manager?.let {
                EmploymentManagerReference(it.id, it.employeeNumber, it.legalName, it.working)
            },
        ),
        ownerCompanyId,
        profileVersion,
        timezone,
    )
}
