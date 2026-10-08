package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.documents.domain.entities.DocumentInventoryPage
import java.util.UUID

data class DocumentInventoryPageResponse(
    val number: Int,
    val jobId: UUID,
    val scanned: Int,
    val retained: Int,
    val unknown: Int,
    val anomalous: Int,
    val queued: Int,
    val recoveryExhausted: Int,
    val scheduled: Int,
    val hasMore: Boolean,
    val createdAt: String,
)

fun DocumentInventoryPage.toResponse() =
    DocumentInventoryPageResponse(
        number,
        jobId,
        counts.scanned,
        counts.retained,
        counts.unknown,
        counts.anomalous,
        counts.queued,
        counts.recoveryExhausted,
        counts.scheduled,
        hasMore,
        at.toString(),
    )
