package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*

class AbortEmployeeImport(
    private val imports: EmployeeImportRepository,
    private val jobs: JobRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(lease: JobLease, failure: Failure): Result<Unit> {
        val request = lease.job.request
        if (request.kind !in setOf(JobKind.EMPLOYEE_IMPORT_PREVIEW, JobKind.EMPLOYEE_IMPORT_APPLY))
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
            val current = jobs.lockLease(lease)
            if (current is Result.Failed) return@run current
            val job = (current as Result.Success).value ?: return@run Result.Success(Unit)
            val code = if (job.cancellationRequested) "job_cancelled" else failure.code
            val found = imports.forJob(request.companyId, request.id, true)
            if (found is Result.Failed) return@run found
            val batch = (found as Result.Success).value
            if (
                batch != null &&
                    batch.status in
                        setOf(EmployeeImportStatus.PREVIEWING, EmployeeImportStatus.IMPORTING)
            ) {
                val stopped = imports.transition(actor, batch, EmployeeImportStatus.STOPPED)
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
                                "employee_import",
                                batch?.id ?: request.id,
                                "people.employee_import_stopped",
                                mapOf("jobId" to request.id.toString(), "failureCode" to code),
                            ),
                        )
                }
        }
    }
}
