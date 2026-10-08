package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class ManagedPersonProfile(
    val profile: PersonProfile,
    val ownerCompanyId: UUID,
    val version: Long,
)
