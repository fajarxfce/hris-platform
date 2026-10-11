package dev.fajar.hris.communications.data.models

import java.util.UUID

data class AudienceReferenceQuery(
    val companyId: UUID,
    val text: String,
    val ids: Set<UUID>,
    val after: UUID?,
    val limit: Int,
    val active: Boolean?,
)
