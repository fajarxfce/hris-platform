package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.payroll.domain.repositories.*

class AbortPayrollFinalization(
    private val finalizations: PayrollFinalizationRepository,
    private val policies: PayrollPolicyRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind != JobKind.PAYROLL_FINALIZE)
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
            val found = finalizations.forJob(request.companyId, request.id)
            if (found is Result.Failed) return@run found
            val finalization = (found as Result.Success).value
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
                                "payroll_finalization",
                                finalization?.id ?: request.id,
                                "payroll.finalization_stopped",
                                mapOf("jobId" to request.id.toString(), "code" to code),
                            ),
                        )
                }
        }
    }
}
