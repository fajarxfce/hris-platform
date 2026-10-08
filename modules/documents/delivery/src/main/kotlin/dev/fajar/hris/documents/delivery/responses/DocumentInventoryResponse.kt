package dev.fajar.hris.documents.delivery.responses

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.documents.domain.entities.DocumentInventoryRun
import dev.fajar.hris.documents.domain.policies.*
import java.util.UUID

data class DocumentInventoryResponse(
    val id: UUID,
    val version: Long,
    val status: String,
    val jobId: UUID,
    val attempts: Int,
    val startedAt: String,
    val cutoff: String,
    val finishedAt: String?,
    val pages: Int,
    val scanned: Int,
    val retained: Int,
    val unknown: Int,
    val anomalous: Int,
    val queued: Int,
    val recoveryExhausted: Int,
    val scheduled: Int,
    val failureCode: String?,
    val availableActions: List<String>,
)

fun DocumentInventoryRun.toResponse(actor: Actor) =
    DocumentInventoryResponse(
        id,
        version,
        documentInventoryStatus(this),
        jobId,
        attempts,
        startedAt.toString(),
        cutoff.toString(),
        finishedAt?.toString(),
        pages,
        counts.scanned,
        counts.retained,
        counts.unknown,
        counts.anomalous,
        counts.queued,
        counts.recoveryExhausted,
        counts.scheduled,
        failureCode,
        if (canResumeDocumentInventory(this) && "jobs.retry" in actor.permissions) listOf("resume")
        else emptyList(),
    )
