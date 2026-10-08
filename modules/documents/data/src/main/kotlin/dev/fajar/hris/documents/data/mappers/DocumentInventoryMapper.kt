package dev.fajar.hris.documents.data.mappers

import dev.fajar.hris.documents.data.models.DocumentInventoryReferenceData
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.schema.tables.records.*

fun DocumentInventoryViewsRecord.toInventory() =
    DocumentInventoryRun(
        companyId,
        id,
        version,
        DocumentInventoryStatus.valueOf(status),
        jobId,
        JobStatus.valueOf(jobStatus),
        failureCode,
        attempts,
        basePages,
        startedAt.toInstant(),
        cutoff.toInstant(),
        createdBy,
        lastKey?.toString(Charsets.UTF_8),
        pages,
        DocumentInventoryCounts(retained, unknown, anomalous, queued, recoveryExhausted, scheduled),
        finishedAt?.toInstant(),
    )

fun DocumentInventoryAttemptViewsRecord.toInventoryAttempt() =
    DocumentInventoryAttempt(
        jobId,
        attempt,
        basePages,
        actorId,
        createdAt.toInstant(),
        reason,
        JobStatus.valueOf(jobStatus),
        failureCode,
    )

fun DocumentInventoryPagesRecord.toInventoryPage() =
    DocumentInventoryPage(
        pageNo,
        jobId,
        DocumentInventoryCounts(retained, unknown, anomalous, queued, recoveryExhausted, scheduled),
        hasMore,
        createdAt.toInstant(),
    )

fun DocumentInventoryReferenceData.toInventoryReference() =
    DocumentInventoryReference(
        key,
        attemptId,
        revisionId,
        size,
        currentAttemptId,
        DocumentRevisionStatus.valueOf(revisionStatus),
        expiresAt.toInstant(),
        cleanupRegistered,
        recoveryCount,
    )
