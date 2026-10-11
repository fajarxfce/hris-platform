package dev.fajar.hris.communications.domain.entities

import java.util.UUID

data class AudienceReferenceSearch(
    val kind: AudienceReferenceKind,
    val query: String = "",
    val ids: List<UUID> = emptyList(),
    val after: UUID? = null,
    val limit: Int = 50,
)
