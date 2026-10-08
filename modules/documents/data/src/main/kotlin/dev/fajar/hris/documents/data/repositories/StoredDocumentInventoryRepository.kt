package dev.fajar.hris.documents.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.data.datasources.DocumentInventoryDataSource
import dev.fajar.hris.documents.data.mappers.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentInventoryRepository
import dev.fajar.hris.jobs.domain.entities.JobRequest
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.storage.domain.entities.ObjectInventoryEntry
import java.time.*
import java.util.UUID

class StoredDocumentInventoryRepository(private val source: DocumentInventoryDataSource) :
    DocumentInventoryRepository {
    override fun capacity(companyId: UUID) = safeDatabaseCall {
        val counts = source.capacity(companyId)
        DocumentInventoryCapacity(counts.total, counts.active)
    }

    override fun find(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.find(companyId, id)?.toInventory()
    }

    override fun list(companyId: UUID, after: UUID?, limit: Int) = safeDatabaseCall {
        val rows = source.list(companyId, after, limit + 1)
        val items = rows.take(limit).map { it.toInventory() }
        Page(items, if (rows.size > limit) items.last().id.toString() else null)
    }

    override fun create(actor: Actor, id: UUID, job: JobRequest, reason: String, cutoff: Instant) =
        safeDatabaseCall {
            source.insert(
                DocumentInventoryRunsRecord().also {
                    it.companyId = job.companyId
                    it.id = id
                    it.jobId = job.id
                    it.attempts = 1
                    it.startedAt = job.createdAt.atOffset(ZoneOffset.UTC)
                    it.cutoff = cutoff.atOffset(ZoneOffset.UTC)
                    it.createdBy = actor.accountId
                }
            )
            source.insertAttempt(
                DocumentInventoryAttemptsRecord().also {
                    it.companyId = job.companyId
                    it.runId = id
                    it.jobId = job.id
                    it.attempt = 1
                    it.basePages = 0
                    it.actorId = actor.accountId
                    it.createdAt = job.createdAt.atOffset(ZoneOffset.UTC)
                    it.reason = reason
                }
            )
            MutationReceipt(id, 0)
        }

    override fun resume(
        actor: Actor,
        run: DocumentInventoryRun,
        job: JobRequest,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.insertAttempt(
                    DocumentInventoryAttemptsRecord().also {
                        it.companyId = run.companyId
                        it.runId = run.id
                        it.jobId = job.id
                        it.attempt = run.attempts + 1
                        it.basePages = run.pages
                        it.actorId = actor.accountId
                        it.createdAt = job.createdAt.atOffset(ZoneOffset.UTC)
                        it.reason = reason
                    }
                )
                source.resume(run.companyId, run.id, run.version, job.id, run.attempts + 1)
            }
            .flatMap { changed ->
                if (changed) Result.Success(MutationReceipt(run.id, run.version + 1))
                else Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun references(companyId: UUID, keys: Set<String>) = safeDatabaseCall {
        source.references(companyId, keys).map { it.toInventoryReference() }
    }

    override fun recordRecovery(
        actor: Actor,
        run: DocumentInventoryRun,
        reference: DocumentInventoryReference,
        entry: ObjectInventoryEntry,
        at: Instant,
    ) = safeDatabaseCall {
        source.insertRecovery(
            DocumentInventoryRecoveriesRecord().also {
                it.companyId = run.companyId
                it.runId = run.id
                it.pageNo = run.pages + 1
                it.attemptId = reference.attemptId
                it.recovery = reference.recoveryCount + 1
                it.actorId = actor.accountId
                it.createdAt = at.atOffset(ZoneOffset.UTC)
                it.modifiedAt = entry.modifiedAt.atOffset(ZoneOffset.UTC)
                it.observedEtag = entry.etag
            }
        )
    }

    override fun checkpoint(
        actor: Actor,
        run: DocumentInventoryRun,
        page: DocumentInventoryPage,
        lastKey: String?,
        status: DocumentInventoryStatus,
    ): Result<Unit> =
        safeDatabaseCall {
                val row =
                    DocumentInventoryPagesRecord().also {
                        it.companyId = run.companyId
                        it.runId = run.id
                        it.jobId = page.jobId
                        it.pageNo = page.number
                        it.actorId = actor.accountId
                        it.createdAt = page.at.atOffset(ZoneOffset.UTC)
                        it.hasMore = page.hasMore
                        it.lastKey = lastKey?.toByteArray(Charsets.UTF_8)
                        it.retained = page.counts.retained
                        it.unknown = page.counts.unknown
                        it.anomalous = page.counts.anomalous
                        it.queued = page.counts.queued
                        it.recoveryExhausted = page.counts.recoveryExhausted
                        it.scheduled = page.counts.scheduled
                    }
                source.checkpoint(run.companyId, run.id, run.version, row, status.name)
            }
            .flatMap { changed ->
                if (changed) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun attempts(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.attempts(companyId, id).map { it.toInventoryAttempt() }
    }

    override fun pages(companyId: UUID, id: UUID, after: Int, limit: Int) = safeDatabaseCall {
        source.pages(companyId, id, after, limit).map { it.toInventoryPage() }
    }
}
