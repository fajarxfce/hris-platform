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
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import java.time.Clock

class AdvanceDocumentValidation(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val jobs: JobRepository,
    private val inspection: DocumentInspectionRepository,
    private val cleanup: ObjectCleanupRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, lease: JobLease): Result<JobStep> {
        val request = lease.job.request
        if (
            request.kind != JobKind.DOCUMENT_VALIDATE ||
                actor.companyId != request.companyId ||
                actor.accountId != request.actorId
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "job_scope_mismatch"))
        val company = request.companyId
        val prepared =
            transactions.run(actor) {
                val documentLock = documents.lock(company)
                if (documentLock is Result.Failed) return@run documentLock
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
                val current =
                    identities.access(actor.accountId, company).flatMap {
                        validateDocumentActor(actor, it)
                    }
                if (current is Result.Failed) return@run current
                val live = (current as Result.Success).value
                val found = documents.validationRevision(company, request.id)
                if (found is Result.Failed) return@run found
                val revision =
                    (found as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "document_validation_obsolete")
                        )
                if (documentStatus(revision, clock.instant()) != DocumentRevisionStatus.VALIDATING)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_validation_obsolete")
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
                    return@run Result.Failed(
                        Failure(FailureKind.FORBIDDEN, "document_access_denied")
                    )
                val chunks = documents.chunks(company, revision.id)
                if (chunks is Result.Failed) return@run chunks
                val values = (chunks as Result.Success).value
                documentContentParts(revision, values).map {
                    DocumentValidationWork(revision, values, it)
                }
            }
        if (prepared is Result.Failed) return prepared
        val work = (prepared as Result.Success).value
        // The transaction and its locks end before storage/scanner I/O.
        val inspected = inspection.inspect(work.parts)
        if (inspected is Result.Failed) return inspected
        val evidence = (inspected as Result.Success).value
        return transactions.run(actor) {
            val documentLock = documents.lock(company)
            if (documentLock is Result.Failed) return@run documentLock
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
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateDocumentActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value
            val found = documents.validationRevision(company, request.id)
            if (found is Result.Failed) return@run found
            val revision =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_validation_obsolete")
                    )
            if (documentStatus(revision, clock.instant()) != DocumentRevisionStatus.VALIDATING)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_validation_obsolete")
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
            if (revision.version != work.revision.version)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "document_validation_obsolete")
                )
            val decision = validateDocumentInspection(revision, evidence)
            val code = (decision as? Result.Failed)?.failure?.code
            val status =
                if (decision is Result.Success) DocumentRevisionStatus.READY
                else DocumentRevisionStatus.REJECTED
            if (status == DocumentRevisionStatus.READY) {
                val accepted = work.chunks.map { requireNotNull(it.attemptId) }.toSet()
                val retained = cleanup.retain(company, accepted)
                if (retained is Result.Failed) return@run retained
                if ((retained as Result.Success).value != accepted.size)
                    return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "document_content_expired")
                    )
            } else {
                val discarded =
                    cleanup.advanceDeletion(company, revision.id, clock.instant().plusSeconds(300))
                if (discarded is Result.Failed) return@run discarded
            }
            val finished =
                documents.finishValidation(
                    company,
                    revision,
                    status,
                    evidence,
                    code,
                    clock.instant(),
                )
            if (finished is Result.Failed) return@run finished
            if (status == DocumentRevisionStatus.READY) {
                val published = documents.publish(company, document, revision.id)
                if (published is Result.Failed) return@run published
            }
            val checkpoint = jobs.checkpoint(lease, JobProgress(1, emptyMap()))
            if (checkpoint is Result.Failed) return@run checkpoint
            if (!(checkpoint as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            val completed = jobs.complete(lease, JobStatus.SUCCEEDED)
            if (completed is Result.Failed) return@run completed
            if (!(completed as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
            journal
                .record(
                    actor,
                    ChangeRecord(
                        "document_revision",
                        revision.id,
                        "documents.validation_completed",
                        mapOf(
                            "jobId" to request.id.toString(),
                            "status" to status.name,
                            "failureCode" to (code ?: ""),
                        ),
                    ),
                )
                .map { JobStep(1, true) }
        }
    }
}
