package dev.fajar.hris.people.data.models

import java.time.LocalDate
import java.util.UUID

data class SelfProfilePayload(
    val id: UUID,
    val companyId: UUID,
    val asOf: LocalDate,
    val timezone: String,
    val employeeNumber: String,
    val employmentVersion: Long,
    val appliedRevision: Long,
    val personId: UUID,
    val accountId: UUID,
    val ownerCompanyId: UUID,
    val profileVersion: Long,
    val legalName: String,
    val birthDate: LocalDate?,
    val nationality: String,
    val email: String?,
    val effectiveFrom: LocalDate,
    val contract: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: String,
    val branchId: UUID?,
    val departmentId: UUID?,
    val positionId: UUID?,
    val costCenterId: UUID?,
    val managerId: UUID?,
    val branch: SelfProfileUnitData?,
    val department: SelfProfileUnitData?,
    val position: SelfProfileUnitData?,
    val costCenter: SelfProfileUnitData?,
    val manager: SelfProfileManagerData?,
    val schema: Int = 1,
) {
    override fun toString() = "SelfProfilePayload(schema=$schema, profileVersion=$profileVersion)"
}
