package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentUploadContinuation
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

data class DocumentUploadContinuationResponse(
    val operationId: UUID,
    @get:Schema(minimum = "0", maximum = "104857600") val offset: Long,
    @get:Schema(minimum = "1", maximum = "1048576") val size: Int,
    @get:Schema(pattern = "[0-9a-f]{64}") val sha256: String,
    @get:Schema(minimum = "0", maximum = "7") val attempts: Int,
    val retryAt: Instant?,
)

fun DocumentUploadContinuation.toResponse() =
    DocumentUploadContinuationResponse(operationId, offset, size, sha256, attempts, retryAt)
