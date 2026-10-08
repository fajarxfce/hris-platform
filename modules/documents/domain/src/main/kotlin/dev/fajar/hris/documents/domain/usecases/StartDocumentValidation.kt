package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class StartDocumentValidation(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        revisionId: UUID,
        version: Long,
        reason: String,
    ): Result<MutationReceipt> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (version < 0 || reason.isBlank() || reason.length > 1000)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_validation"))
        val key =
            OperationKey(
                "documents.validate",
                operationId,
                listOf(revisionId.toString(), version.toString(), reason),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val documentLock = documents.lock(company)
            if (documentLock is Result.Failed) return@run documentLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val membershipLock = members.lock(company)
            if (membershipLock is Result.Failed) return@run membershipLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateDocumentActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value
            val found = documents.revision(company, revisionId)
            if (found is Result.Failed) return@run found
            val revision =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_revision_not_found")
                    )
            val foundDocument = documents.find(company, revision.documentId)
            if (foundDocument is Result.Failed) return@run foundDocument
            val document =
                (foundDocument as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val profile = profiles.findForEmployee(company, document.employmentId)
            if (profile is Result.Failed) return@run profile
            if (
                !canManageDocument(
                    live,
                    document.classification,
                    (profile as Result.Success).value?.profile?.accountId,
                )
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (revision.version != version)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            if (documentStatus(revision, clock.instant()) !in ACTIVE_DOCUMENT_STATUSES)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_upload_closed"))
            if (revision.validationAttempts >= 8)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_validation_attempts_exhausted")
                )
            val previousId = revision.validationJobId
            if (previousId != null) {
                val previous = jobs.find(company, previousId, true)
                if (previous is Result.Failed) return@run previous
                if (
                    (previous as Result.Success).value?.status !in
                        setOf(JobStatus.FAILED, JobStatus.CANCELLED)
                )
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_validation_active")
                    )
            }
            val chunks = documents.chunks(company, revisionId)
            if (chunks is Result.Failed) return@run chunks
            val manifest = documentContentParts(revision, (chunks as Result.Success).value)
            if (manifest is Result.Failed) return@run manifest
            val queueLock = jobs.lockQueue(company)
            if (queueLock is Result.Failed) return@run queueLock
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
                    JobKind.DOCUMENT_VALIDATE,
                    operationId,
                    mapOf("revisionId" to revisionId.toString()),
                    actor.authenticatedAt,
                    actor.credentialVersion ?: 0,
                    actor.correlationId,
                    now,
                    1,
                )
            jobs
                .create(job)
                .flatMap {
                    documents.scheduleValidation(
                        actor,
                        revision,
                        DocumentValidationAttempt(
                            job.id,
                            revision.validationAttempts + 1,
                            actor.accountId,
                            now,
                            reason,
                        ),
                    )
                }
                .flatMap { receipt ->
                    operations
                        .record(actor, key, receipt)
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "document_revision",
                                    revisionId,
                                    "documents.validation_started",
                                    mapOf(
                                        "jobId" to job.id.toString(),
                                        "attempt" to (revision.validationAttempts + 1).toString(),
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
