package dev.fajar.hris.documents.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

data class DocumentInventoryRun(
    val companyId: UUID,
    val id: UUID,
    val version: Long,
    val status: DocumentInventoryStatus,
    val jobId: UUID,
    val jobStatus: JobStatus,
    val failureCode: String?,
    val attempts: Int,
    val attemptBasePages: Int,
    val startedAt: Instant,
    val cutoff: Instant,
    val createdBy: UUID,
    val lastKey: String?,
    val pages: Int,
    val counts: DocumentInventoryCounts,
    val finishedAt: Instant?,
)
