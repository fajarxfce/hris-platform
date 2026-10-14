package dev.fajar.hris.people.data.models

import dev.fajar.hris.schema.tables.records.EmployeesAtRecord
import dev.fajar.hris.schema.tables.records.OrganizationUnitsRecord
import java.time.LocalDate
import java.util.UUID

data class SelfProfileCandidateRow(
    val id: UUID,
    val employee: EmployeesAtRecord?,
    val ownerCompanyId: UUID,
    val profileVersion: Long,
    val branch: OrganizationUnitsRecord?,
    val department: OrganizationUnitsRecord?,
    val position: OrganizationUnitsRecord?,
    val costCenter: OrganizationUnitsRecord?,
    val manager: SelfProfileManagerRow?,
    val nextRevision: LocalDate?,
    val nextManagerRevision: LocalDate?,
) {
    override fun toString() = "SelfProfileCandidateRow(profileVersion=$profileVersion)"
}
