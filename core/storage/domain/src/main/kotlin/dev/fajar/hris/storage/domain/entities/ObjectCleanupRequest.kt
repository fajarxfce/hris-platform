package dev.fajar.hris.storage.domain.entities

import java.time.Instant
import java.util.UUID

data class ObjectCleanupRequest(
    val id: UUID,
    val companyId: UUID,
    val resourceId: UUID,
    val key: String,
    val bytes: Long,
    val createdBy: UUID,
    val deleteAfter: Instant,
)
