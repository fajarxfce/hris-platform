package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import java.util.UUID

class AbortDocumentInventory(
    private val documents: DocumentRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.DOCUMENT_INVENTORY)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val actor =
            Actor(
                request.actorId,
                request.companyId,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
            )
        val id =
            request.values["runId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: request.id
        return transactions.run(actor) {
            val guard = documents.lock(request.companyId)
            if (guard is Result.Failed) return@run guard
            val owned = jobs.lockLease(lease)
            if (owned is Result.Failed) return@run owned
            val job = (owned as Result.Success).value ?: return@run Result.Success(Unit)
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            jobs
                .complete(
                    lease,
                    if (job.cancellationRequested) JobStatus.CANCELLED else JobStatus.FAILED,
                    code,
                )
                .flatMap { changed ->
                    if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "document_inventory",
                                id,
                                "documents.inventory_stopped",
                                mapOf("jobId" to request.id.toString(), "failureCode" to code),
                            ),
                        )
                }
        }
    }
}
