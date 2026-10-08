package dev.fajar.hris.core.database.datasources

import java.util.UUID

data class JournalRow(
    val id: UUID,
    val companyId: UUID?,
    val actorId: UUID,
    val resourceType: String,
    val resourceId: UUID,
    val action: String,
    val details: Map<String, String>,
    val reason: String?,
    val correlationId: UUID,
)
