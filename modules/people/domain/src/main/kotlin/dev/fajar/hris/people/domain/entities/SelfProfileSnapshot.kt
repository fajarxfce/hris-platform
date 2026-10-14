package dev.fajar.hris.people.domain.entities

import java.util.UUID

/** A coherent employment and private-person projection at its recorded company date. */
data class SelfProfileSnapshot(
    val details: EmploymentDetails,
    val ownerCompanyId: UUID,
    val profileVersion: Long,
    val timezone: String,
) {
    override fun toString() = "SelfProfileSnapshot(profileVersion=$profileVersion)"
}
