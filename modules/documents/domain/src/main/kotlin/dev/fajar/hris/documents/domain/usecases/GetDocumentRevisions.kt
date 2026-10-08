package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class GetDocumentRevisions(
    private val documents: DocumentRepository,
    private val jobs: JobRepository,
    private val profiles: PersonProfileRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        documentId: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<DocumentRevision>> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val current =
                identities.access(actor.accountId, company).flatMap {
                    validateDocumentActor(actor, it)
                }
            if (current is Result.Failed) return@run current
            val live = (current as Result.Success).value

            if (limit !in 1..200 || (after != null && after !in 1..101))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "invalid_document_page"))
            val found = documents.find(company, documentId)
            if (found is Result.Failed) return@run found
            val document =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "document_not_found")
                    )
            val profile = profiles.findForEmployee(company, document.employmentId)
            if (profile is Result.Failed) return@run profile
            if (
                !canReadDocument(
                    live,
                    document.classification,
                    (profile as Result.Success).value?.profile?.accountId,
                )
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            val foundRevisions = documents.revisions(company, documentId, after, limit)
            if (foundRevisions is Result.Failed) return@run foundRevisions
            val page = (foundRevisions as Result.Success).value
            // The unique active-revision constraint bounds this lookup to one job per page.
            val validating =
                page.items.singleOrNull { it.status == DocumentRevisionStatus.VALIDATING }
            val jobId = validating?.validationJobId
            val job = if (jobId != null) jobs.find(company, jobId) else Result.Success(null)
            if (job is Result.Failed) return@run job
            Result.Success(
                Page(
                    page.items.map {
                        projectDocumentRevision(it, (job as Result.Success).value, clock.instant())
                    },
                    page.nextCursor,
                )
            )
        }
    }
}
