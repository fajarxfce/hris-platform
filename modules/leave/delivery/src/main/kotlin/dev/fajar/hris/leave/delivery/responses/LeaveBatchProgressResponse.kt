package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveBatchProgressResponse(
    val jobId: UUID,
    val status: String,
    val completedItems: Int,
    val totalItems: Int,
    val attempts: Int,
    val version: Long,
    val cancellationRequested: Boolean,
    val failureCode: String?,
)
