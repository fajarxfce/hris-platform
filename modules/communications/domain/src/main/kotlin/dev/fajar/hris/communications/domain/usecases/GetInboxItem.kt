package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.util.UUID

class GetInboxItem(
    private val inbox: InboxRepository,
    private val announcements: AnnouncementRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, id: UUID): Result<InboxItem> {
        val permission = actor.requirePermission("announcements.read")
        if (permission is Result.Failed) return permission
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val guard = announcements.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanyCommandActor(actor, it) }
                    .flatMap { it.requirePermission("announcements.read") }
            if (checked is Result.Failed) return@run checked
            inbox.find(company, actor.accountId, id).flatMap {
                if (it == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "inbox_item_not_found"))
                else Result.Success(it)
            }
        }
    }
}
