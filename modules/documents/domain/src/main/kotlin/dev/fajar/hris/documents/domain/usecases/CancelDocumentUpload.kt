package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import dev.fajar.hris.storage.domain.repositories.*
import java.time.Clock
import java.util.UUID

class CancelDocumentUpload(
    private val documents: DocumentRepository,
    private val jobs: JobRepository,
    private val profiles: PersonProfileRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val cleanup: ObjectCleanupRepository,
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
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_cancellation"))
        val key =
            OperationKey(
                "documents.upload_cancel",
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
            val documentResult = documents.find(company, revision.documentId)
            if (documentResult is Result.Failed) return@run documentResult
            val document =
                (documentResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val profileResult = profiles.findForEmployee(company, document.employmentId)
            if (profileResult is Result.Failed) return@run profileResult
            val profile =
                (profileResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (!canManageDocument(live, document.classification, profile.profile.accountId))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            if (revision.version != version || revision.status !in ACTIVE_DOCUMENT_STATUSES)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val validationId = revision.validationJobId
            if (validationId != null) {
                val previousJob = jobs.find(company, validationId, true)
                if (previousJob is Result.Failed) return@run previousJob
                val pendingJob = (previousJob as Result.Success).value
                if (
                    pendingJob != null &&
                        pendingJob.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING) &&
                        !pendingJob.cancellationRequested
                ) {
                    val stoppedJob =
                        jobs.requestCancellation(
                            company,
                            validationId,
                            pendingJob.version,
                            clock.instant(),
                        )
                    if (stoppedJob is Result.Failed) return@run stoppedJob
                    if ((stoppedJob as Result.Success).value == null)
                        return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                }
            }
            val changed =
                documents.transition(company, revisionId, version, DocumentRevisionStatus.CANCELLED)
            if (changed is Result.Failed) return@run changed
            val receipt =
                (changed as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            cleanup
                .advanceDeletion(company, revisionId, clock.instant().plusSeconds(300))
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "document_revision",
                            revisionId,
                            "documents.upload_cancelled",
                            mapOf("version" to receipt.version.toString()),
                            reason,
                        ),
                    )
                }
                .map { receipt }
        }
    }
}
