package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class StartDocumentInventory(
    private val inventory: DocumentInventoryRepository,
    private val documents: DocumentRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    /**
     * A null expected version starts a new scan; an explicit version resumes the same checkpoint.
     */
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val access = validateDocumentInventoryCommand(actor, clock.instant(), security)
        if (access is Result.Failed) return access
        if (
            (expectedVersion != null && expectedVersion < 0) ||
                reason.isBlank() ||
                reason.length > 1000 ||
                reason.any { it.isISOControl() }
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_inventory"))
        val key =
            OperationKey(
                "documents.inventory_start",
                operationId,
                listOf(id.toString(), expectedVersion?.toString() ?: "new", reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val guard = documents.lock(company)
            if (guard is Result.Failed) return@run guard

            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val membershipLock = members.lock(company)
            if (membershipLock is Result.Failed) return@run membershipLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value

            val permission = validateDocumentInventoryCommand(live, clock.instant(), security)
            if (permission is Result.Failed) return@run permission
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val found = inventory.find(company, id)
            if (found is Result.Failed) return@run found
            val previous = (found as Result.Success).value
            if (expectedVersion == null && previous != null)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_inventory_exists"))
            if (expectedVersion != null) {
                if (previous == null)
                    return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_inventory_not_found")
                    )
                if (previous.version != expectedVersion)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                if (!canResumeDocumentInventory(previous))
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_inventory_not_resumable")
                    )
                val prior = jobs.find(company, previous.jobId, true)
                if (prior is Result.Failed) return@run prior
                if (
                    (prior as Result.Success).value?.status !in
                        setOf(JobStatus.FAILED, JobStatus.CANCELLED)
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_inventory_active")
                    )
            }
            val capacity = inventory.capacity(company)
            if (capacity is Result.Failed) return@run capacity
            val counts = (capacity as Result.Success).value
            if (counts.active > 0)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_inventory_active"))
            if (previous == null && counts.total >= 10000)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_inventory_capacity")
                )
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
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
                    JobKind.DOCUMENT_INVENTORY,
                    operationId,
                    mapOf("runId" to id.toString(), "progressUnit" to "pages"),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    DOCUMENT_INVENTORY_MAX_PAGES - (previous?.pages ?: 0),
                    JobProgressMode.UPPER_BOUND,
                )
            jobs
                .create(job)
                .flatMap {
                    if (previous == null)
                        inventory.create(actor, id, job, reason, now.minusSeconds(86400))
                    else inventory.resume(actor, previous, job, reason)
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "document_inventory",
                                    id,
                                    if (previous == null) "documents.inventory_started"
                                    else "documents.inventory_resumed",
                                    mapOf(
                                        "jobId" to job.id.toString(),
                                        "attempt" to ((previous?.attempts ?: 0) + 1).toString(),
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
