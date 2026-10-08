package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentInventoryAttempt
import java.util.UUID

data class DocumentInventoryAttemptResponse(
    val jobId: UUID,
    val number: Int,
    val basePages: Int,
    val actorId: UUID,
    val createdAt: String,
    val reason: String,
    val status: String,
    val failureCode: String?,
)

fun DocumentInventoryAttempt.toResponse() =
    DocumentInventoryAttemptResponse(
        jobId,
        number,
        basePages,
        actorId,
        at.toString(),
        reason,
        status.name,
        failureCode,
    )
