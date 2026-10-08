package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.policies.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.util.UUID

class GetDocumentDownload(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<DocumentDownload> {
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
            if (
                !canReadDocument(
                    live,
                    document.classification,
                    (profile as Result.Success).value?.profile?.accountId,
                )
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "document_access_denied"))
            if (revision.status != DocumentRevisionStatus.READY)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "document_not_ready"))
            Result.Success(
                DocumentDownload(
                    revision.id,
                    revision.fileName,
                    revision.mediaType,
                    revision.size,
                    revision.sha256,
                )
            )
        }
    }
}
