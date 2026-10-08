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

class ResumeEmployeeImport(
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
                "people.employee_import_resume",
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
            val jobResult = jobs.find(company, old.jobId, true)
            if (jobResult is Result.Failed) return@run jobResult
            val stopped =
                (jobResult as Result.Success).value
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
                batch.status !in
                    setOf(
                        EmployeeImportStatus.STOPPED,
                        EmployeeImportStatus.PREVIEWING,
                        EmployeeImportStatus.IMPORTING,
                    ) || stopped.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "employee_import_job_not_stopped")
                )
            val phase =
                when (stopped.request.kind) {
                    JobKind.EMPLOYEE_IMPORT_PREVIEW -> EmployeeImportPhase.PREVIEW
                    JobKind.EMPLOYEE_IMPORT_APPLY -> EmployeeImportPhase.APPLY
                    else ->
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "employee_import_job_mismatch")
                        )
                }
            val counts = imports.counts(company, id)
            if (counts is Result.Failed) return@run counts
            val state =
                if (phase == EmployeeImportPhase.PREVIEW) EmployeeImportRowStatus.PENDING
                else EmployeeImportRowStatus.READY
            val remaining = (counts as Result.Success).value[state] ?: 0
            val lock = jobs.lockQueue(company)
            if (lock is Result.Failed) return@run lock
            val pending = jobs.pendingCount(company)
            if (pending is Result.Failed) return@run pending
            if ((pending as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val now = clock.instant()
            val job =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    stopped.request.kind,
                    operationId,
                    mapOf("importId" to id.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    remaining + 1,
                )
            val status =
                if (phase == EmployeeImportPhase.PREVIEW) EmployeeImportStatus.PREVIEWING
                else EmployeeImportStatus.IMPORTING
            jobs
                .create(job)
                .flatMap {
                    imports.transition(
                        actor,
                        batch,
                        status,
                        EmployeeImportAttempt(job.id, phase, actor.accountId, now),
                    )
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "employee_import",
                                    id,
                                    "people.employee_import_resumed",
                                    mapOf(
                                        "jobId" to job.id.toString(),
                                        "remainingRows" to remaining.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
