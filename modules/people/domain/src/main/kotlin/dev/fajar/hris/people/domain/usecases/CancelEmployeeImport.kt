package dev.fajar.hris.people.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import dev.fajar.hris.people.domain.repositories.*
import java.time.Clock
import java.util.UUID

class CancelEmployeeImport(
    private val imports: EmployeeImportRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val access = requireEmployeeImportAccess(actor)
        if (access is Result.Failed) return access
        val assurance = requireEmployeeImportAssurance(actor, security, clock.instant())
        if (assurance is Result.Failed) return assurance
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_employee_import"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "people.employee_import_cancel",
                operationId,
                listOf(id.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val observed = imports.find(company, id)
            if (observed is Result.Failed) return@run observed
            val old =
                (observed as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                    )
            val currentJob = jobs.find(company, old.jobId, true)
            if (currentJob is Result.Failed) return@run currentJob
            val job =
                (currentJob as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "employee_import_job_missing")
                    )
            val found = imports.find(company, id, true)
            if (found is Result.Failed) return@run found
            val batch =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_import_not_found")
                    )
            if (batch.version != version || batch.jobId != old.jobId)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (
                batch.status in
                    setOf(EmployeeImportStatus.COMPLETED, EmployeeImportStatus.CANCELLED)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employee_import_is_terminal")
                )
            val active = job.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING)
            if (active && !job.cancellationRequested) {
                val cancelled = jobs.requestCancellation(company, job.request.id, job.version)
                if (cancelled is Result.Failed) return@run cancelled
                if ((cancelled as Result.Success).value == null)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_job_version"))
            }
            imports
                .transition(
                    actor,
                    batch,
                    if (active) batch.status else EmployeeImportStatus.CANCELLED,
                )
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "employee_import",
                                    id,
                                    "people.employee_import_cancellation_requested",
                                    reason = reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
