package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository

class AbortDocumentValidation(
    private val documents: DocumentRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.DOCUMENT_VALIDATE)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val actor =
            Actor(
                request.actorId,
                request.companyId,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
            )
        return transactions.run(actor) {
            val lock = documents.lock(request.companyId)
            if (lock is Result.Failed) return@run lock
            val current = jobs.lockLease(lease)
            if (current is Result.Failed) return@run current
            val job = (current as Result.Success).value ?: return@run Result.Success(Unit)
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            val found = documents.validationRevision(request.companyId, request.id)
            if (found is Result.Failed) return@run found
            val revision = (found as Result.Success).value
            if (revision?.status == DocumentRevisionStatus.VALIDATING) {
                val stopped = documents.failValidation(request.companyId, revision, code)
                if (stopped is Result.Failed) return@run stopped
            }
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
                                "document_revision",
                                revision?.id ?: request.id,
                                "documents.validation_stopped",
                                mapOf("jobId" to request.id.toString(), "failureCode" to code),
                            ),
                        )
                }
        }
    }
}
