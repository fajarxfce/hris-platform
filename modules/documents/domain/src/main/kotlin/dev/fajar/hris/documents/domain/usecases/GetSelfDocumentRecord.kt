package dev.fajar.hris.documents.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.time.Clock
import java.util.UUID

class GetSelfDocumentRecord(
    private val documents: DocumentRepository,
    private val profiles: PersonProfileRepository,
    private val people: PeopleRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<DocumentCatalogRecord> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permission = actor.requirePermission("documents.self.read")
        if (permission is Result.Failed) return permission
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
            val checked = validateCompanySessionActor(actor, identity, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val authorized = live.requirePermission("documents.self.read")
            if (authorized is Result.Failed) return@run authorized
            val found = documents.publishedRecord(company, id)
            if (found is Result.Failed) return@run found
            val record = (found as Result.Success).value
            if (record == null || record.classification == DocumentClassification.HR_ONLY)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "document_evidence_not_found")
                )
            val foundProfile = profiles.findForEmployee(company, record.employmentId)
            if (foundProfile is Result.Failed) return@run foundProfile
            if ((foundProfile as Result.Success).value?.profile?.accountId != live.accountId)
                return@run Result.Failed(
                    Failure(FailureKind.NOT_FOUND, "document_evidence_not_found")
                )
            validateCompanySessionActor(actor, identity, clock.instant(), security).map { record }
        }
    }
}
