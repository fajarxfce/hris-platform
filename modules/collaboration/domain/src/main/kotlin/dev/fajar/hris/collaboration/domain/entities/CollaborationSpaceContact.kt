package dev.fajar.hris.collaboration.domain.entities

import java.util.UUID

/** Public contact labels grant no HR company or document authority. */
data class CollaborationSpaceContact(
    val accountId: UUID,
    val displayName: String,
    val companies: List<CollaborationContactCompany>,
    val hasMoreCompanies: Boolean,
)

data class CollaborationContactCompany(val id: UUID, val name: String)
