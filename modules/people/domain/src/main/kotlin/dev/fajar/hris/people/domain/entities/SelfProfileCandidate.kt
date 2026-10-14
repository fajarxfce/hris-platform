package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

/** All values come from one source snapshot; effective-date policy runs in domain. */
data class SelfProfileCandidate(
    val id: UUID,
    val employee: Employee?,
    val ownerCompanyId: UUID,
    val profileVersion: Long,
    val branch: EmploymentUnitReference?,
    val department: EmploymentUnitReference?,
    val position: EmploymentUnitReference?,
    val costCenter: EmploymentUnitReference?,
    val manager: SelfProfileManagerCandidate?,
    val nextRevision: LocalDate?,
    val nextManagerRevision: LocalDate?,
) {
    override fun toString() = "SelfProfileCandidate(profileVersion=$profileVersion)"
}
