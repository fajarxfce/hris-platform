package dev.fajar.hris.storage.domain.entities
data class ObjectCleanupEntry(
    val request: ObjectCleanupRequest,
    val status: ObjectCleanupStatus,
    val attempts: Int,
    val failureCode: String?,
    val version: Long,
)
