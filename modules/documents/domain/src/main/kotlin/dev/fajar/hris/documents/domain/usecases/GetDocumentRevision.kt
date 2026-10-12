package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class GetDocumentRevision(
    private val documents: DocumentRepository,
    private val jobs: JobRepository,
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<DocumentRevisionDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val documentGuard = documents.lock(company, shared = true)
            if (documentGuard is Result.Failed) return@run documentGuard
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access = identities.access(actor.accountId, company)
            if (access is Result.Failed) return@run access
            val identity = (access as Result.Success).value
            val current = validateCompanySessionActor(actor, identity, clock.instant(), security)
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            val found = documents.revision(company, id)
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
            val ownerAccountId = (profile as Result.Success).value?.profile?.accountId
            if (!canReadDocument(live, document.classification, ownerAccountId))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            val jobId = revision.validationJobId
            val job =
                if (revision.status in ACTIVE_DOCUMENT_STATUSES && jobId != null)
                    jobs.find(company, jobId)
                else Result.Success(null)
            if (job is Result.Failed) return@run job
            val currentJob = (job as Result.Success).value
            val upload =
                if (
                    canManageDocument(live, document.classification, ownerAccountId) &&
                        documentStatus(revision, clock.instant()) ==
                            DocumentRevisionStatus.UPLOADING &&
                        revision.uploadedBytes < revision.size
                )
                    documents.chunkAt(
                        company,
                        id,
                        (revision.uploadedBytes / DOCUMENT_CHUNK_BYTES + 1).toInt(),
                    )
                else Result.Success(null)
            if (upload is Result.Failed) return@run upload
            val currentUpload = (upload as Result.Success).value
            if (
                currentUpload != null &&
                    (currentUpload.revisionId != revision.id ||
                        currentUpload.offset != revision.uploadedBytes ||
                        currentUpload.size.toLong() !=
                            minOf(
                                DOCUMENT_CHUNK_BYTES.toLong(),
                                revision.size - revision.uploadedBytes,
                            ))
            )
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "document_upload_state_invalid")
                )
            val now = clock.instant()
            val assurance = validateCompanySessionActor(actor, identity, now, security)
            if (assurance is Result.Failed) return@run assurance
            val actions =
                documentRevisionActions(
                    live,
                    document.classification,
                    ownerAccountId,
                    revision,
                    currentJob?.status,
                    now,
                    currentUpload,
                )
            Result.Success(
                DocumentRevisionDetails(
                    projectDocumentRevision(revision, currentJob, now),
                    actions,
                    if (DocumentRevisionAction.UPLOAD_CONTENT in actions)
                        currentUpload?.let {
                            DocumentUploadContinuation(
                                it.id,
                                it.offset,
                                it.size,
                                it.sha256,
                                it.attempts,
                                it.leaseUntil?.takeIf { expiry -> expiry.isAfter(now) },
                            )
                        }
                    else null,
                )
            )
        }
    }
}
