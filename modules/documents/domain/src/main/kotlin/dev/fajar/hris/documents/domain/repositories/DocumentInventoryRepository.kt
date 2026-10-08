package dev.fajar.hris.documents.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.jobs.domain.entities.JobRequest
import dev.fajar.hris.storage.domain.entities.ObjectInventoryEntry
import java.time.Instant
import java.util.UUID

interface DocumentInventoryRepository {
    fun capacity(companyId: UUID): Result<DocumentInventoryCapacity>

    fun find(companyId: UUID, id: UUID): Result<DocumentInventoryRun?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<DocumentInventoryRun>>

    fun create(
        actor: Actor,
        id: UUID,
        job: JobRequest,
        reason: String,
        cutoff: Instant,
    ): Result<MutationReceipt>

    fun resume(
        actor: Actor,
        run: DocumentInventoryRun,
        job: JobRequest,
        reason: String,
    ): Result<MutationReceipt>

    fun references(companyId: UUID, keys: Set<String>): Result<List<DocumentInventoryReference>>

    fun recordRecovery(
        actor: Actor,
        run: DocumentInventoryRun,
        reference: DocumentInventoryReference,
        entry: ObjectInventoryEntry,
        at: Instant,
    ): Result<Unit>

    fun checkpoint(
        actor: Actor,
        run: DocumentInventoryRun,
        page: DocumentInventoryPage,
        lastKey: String?,
        status: DocumentInventoryStatus,
    ): Result<Unit>

    fun attempts(companyId: UUID, id: UUID): Result<List<DocumentInventoryAttempt>>

    fun pages(
        companyId: UUID,
        id: UUID,
        after: Int,
        limit: Int,
    ): Result<List<DocumentInventoryPage>>
}
