package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import java.util.UUID

class AdvanceDocumentInventory(
    private val inventory: DocumentInventoryRepository,
    private val documents: DocumentRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val storage: ObjectStorageRepository,
    private val cleanup: ObjectCleanupRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        if (
            request.kind != JobKind.DOCUMENT_INVENTORY ||
                request.progressMode != JobProgressMode.UPPER_BOUND ||
                request.companyId != actor.companyId ||
                request.actorId != actor.accountId
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val id =
            request.values["runId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_document_inventory")
                )
        val company = request.companyId
        val prepared =
            transactions.run(actor) {
                val guard = documents.lock(company)
                if (guard is Result.Failed) return@run guard
                val owned = jobs.lockLease(lease)
                if (owned is Result.Failed) return@run owned
                val job =
                    (owned as Result.Success).value
                        ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                if (job.cancellationRequested)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                    )

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

                val permission =
                    live.requirePermission("documents.inventory").flatMap {
                        live.requirePermission("jobs.retry")
                    }
                if (permission is Result.Failed) return@run permission
                val found = inventory.find(company, id)
                if (found is Result.Failed) return@run found
                val run =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.NOT_FOUND, "document_inventory_not_found")
                        )
                if (
                    run.jobId != request.id ||
                        run.status != DocumentInventoryStatus.SCANNING ||
                        run.pages >= DOCUMENT_INVENTORY_MAX_PAGES ||
                        request.totalItems != DOCUMENT_INVENTORY_MAX_PAGES - run.attemptBasePages ||
                        job.completedItems != run.pages - run.attemptBasePages
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_inventory_obsolete")
                    )

                Result.Success(run)
            }
        if (prepared is Result.Failed) return prepared
        val work = (prepared as Result.Success).value
        // No SQL transaction or lock is held while reading a bounded provider page.
        val listed = storage.list(company, work.lastKey, DOCUMENT_INVENTORY_PAGE_SIZE)
        if (listed is Result.Failed) return listed
        val page = (listed as Result.Success).value
        return transactions.run(actor) {
            val guard = documents.lock(company)
            if (guard is Result.Failed) return@run guard
            val owned = jobs.lockLease(lease)
            if (owned is Result.Failed) return@run owned
            val job =
                (owned as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            if (job.cancellationRequested)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "job_cancellation_requested")
                )

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

            val permission =
                live.requirePermission("documents.inventory").flatMap {
                    live.requirePermission("jobs.retry")
                }
            if (permission is Result.Failed) return@run permission
            val found = inventory.find(company, id)
            if (found is Result.Failed) return@run found
            val run =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_inventory_not_found")
                    )
            if (
                run.jobId != request.id ||
                    run.status != DocumentInventoryStatus.SCANNING ||
                    run.pages >= DOCUMENT_INVENTORY_MAX_PAGES ||
                    request.totalItems != DOCUMENT_INVENTORY_MAX_PAGES - run.attemptBasePages ||
                    job.completedItems != run.pages - run.attemptBasePages
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_inventory_obsolete")
                )

            if (run.version != work.version)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_inventory_obsolete")
                )
            val known = inventory.references(company, documentInventoryCandidateKeys(page.entries))
            if (known is Result.Failed) return@run known
            val references = (known as Result.Success).value.associateBy { it.key }
            val now = clock.instant()
            val dispositions =
                page.entries.map { entry ->
                    classifyDocumentInventoryEntry(entry, references[entry.key], run.cutoff, now)
                }
            for ((index, disposition) in dispositions.withIndex()) {
                if (disposition != DocumentInventoryDisposition.SCHEDULE) continue
                val reference = references.getValue(page.entries[index].key)
                val scheduled =
                    cleanup.schedule(
                        ObjectCleanupRequest(
                            reference.attemptId,
                            company,
                            reference.revisionId,
                            reference.key,
                            reference.size,
                            actor.accountId,
                            now.plusSeconds(300),
                        )
                    )
                if (scheduled is Result.Failed) return@run scheduled
                val recorded =
                    inventory.recordRecovery(actor, run, reference, page.entries[index], now)
                if (recorded is Result.Failed) return@run recorded
            }
            val status =
                when {
                    !page.hasMore -> DocumentInventoryStatus.COMPLETED
                    run.pages + 1 == DOCUMENT_INVENTORY_MAX_PAGES ->
                        DocumentInventoryStatus.LIMIT_REACHED
                    else -> DocumentInventoryStatus.SCANNING
                }
            val evidence =
                DocumentInventoryPage(
                    run.pages + 1,
                    request.id,
                    documentInventoryCounts(dispositions),
                    page.hasMore,
                    now,
                )
            val saved =
                inventory.checkpoint(
                    actor,
                    run,
                    evidence,
                    page.entries.lastOrNull()?.key ?: run.lastKey,
                    status,
                )
            if (saved is Result.Failed) return@run saved
            val completed = evidence.number - run.attemptBasePages
            val checkpoint =
                jobs.checkpoint(
                    lease,
                    JobProgress(completed, mapOf("pages" to evidence.number.toString())),
                )
            if (checkpoint is Result.Failed) return@run checkpoint
            if (!(checkpoint as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val finished = status != DocumentInventoryStatus.SCANNING
            if (finished) {
                val done = jobs.complete(lease, JobStatus.SUCCEEDED)
                if (done is Result.Failed) return@run done
                if (!(done as Result.Success).value)
                    return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            }
            journal
                .record(
                    actor,
                    ChangeRecord(
                        "document_inventory",
                        id,
                        "documents.inventory_page",
                        mapOf(
                            "jobId" to request.id.toString(),
                            "page" to evidence.number.toString(),
                            "scanned" to evidence.counts.scanned.toString(),
                            "scheduled" to evidence.counts.scheduled.toString(),
                            "status" to status.name,
                        ),
                    ),
                )
                .map { JobStep(completed, finished) }
        }
    }
}
