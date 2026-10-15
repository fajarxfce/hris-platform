package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.Clock

class AbortPayrollAmendmentPublication(
    private val publications: PayrollAmendmentPublicationRepository,
    private val amendments: PayrollAmendmentRepository,
    private val policies: PayrollPolicyRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.PAYROLL_AMENDMENT_PUBLISH)
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
            val leased = jobs.lockLease(lease)
            if (leased is Result.Failed) return@run leased
            val job = (leased as Result.Success).value ?: return@run Result.Success(Unit)
            val guard = policies.lock(request.companyId)
            if (guard is Result.Failed) return@run guard
            val found = publications.forJob(request.companyId, request.id)
            if (found is Result.Failed) return@run found
            val publication =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                    )
            val foundAmendment = amendments.find(request.companyId, publication.amendmentId)
            if (foundAmendment is Result.Failed) return@run foundAmendment
            val amendment = (foundAmendment as Result.Success).value
            if (
                publication.status != PayrollAmendmentPublicationStatus.PENDING ||
                    amendment == null ||
                    amendment.summary.latestPublicationId != publication.id ||
                    amendment.summary.version != publication.amendmentVersion + 1 ||
                    amendment.summary.status != PayrollAmendmentStatus.PUBLISHING
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                )
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            val now = clock.instant()
            val restored =
                amendment.copy(
                    summary =
                        amendment.summary.copy(
                            version = amendment.summary.version + 1,
                            status = PayrollAmendmentStatus.APPROVED,
                            action = PayrollAmendmentAction.PUBLICATION_STOPPED,
                            actorId = actor.accountId,
                            recordedAt = now,
                            reason = publication.reason,
                        )
                )
            publications
                .stop(request.companyId, publication, now, code, actor.accountId)
                .flatMap { amendments.save(request.companyId, restored, amendment.summary.version) }
                .flatMap {
                    jobs.complete(
                        lease,
                        if (job.cancellationRequested) JobStatus.CANCELLED else JobStatus.FAILED,
                        code,
                    )
                }
                .flatMap { changed ->
                    if (!changed) Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                    else
                        journal.record(
                            actor,
                            ChangeRecord(
                                "payroll_amendment",
                                publication.amendmentId,
                                "payroll.amendment_publication_stopped",
                                mapOf(
                                    "publicationId" to publication.id.toString(),
                                    "jobId" to request.id.toString(),
                                    "code" to code,
                                ),
                            ),
                        )
                }
        }
    }
}
