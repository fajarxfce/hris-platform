package dev.fajar.hris.storage.delivery.responses

import java.time.Instant
import java.util.UUID

data class ObjectCleanupResponse(
    val id: UUID,
    val resourceId: UUID,
    val status: String,
    val attempts: Int,
    val eligibleAt: Instant,
    val failureCode: String?,
    val version: Long,
)
