package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class GetAnnouncementDelivery(
    private val announcements: AnnouncementRepository,
    private val delivery: AnnouncementDeliveryRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID): Result<AnnouncementDelivery> {
        val allowed = actor.requirePermission("announcements.manage")
        if (allowed is Result.Failed) return allowed
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
            val identity = identities.access(actor.accountId, company)
            if (identity is Result.Failed) return@run identity
            val resolved = (identity as Result.Success).value
            val authorized =
                validateCompanySessionActor(actor, resolved, clock.instant(), security).flatMap {
                    it.requirePermission("announcements.manage")
                }
            if (authorized is Result.Failed) return@run authorized
            val found = announcements.find(company, id)
            if (found is Result.Failed) return@run found
            val announcement =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "announcement_not_found")
                    )
            val result = delivery.counts(company, id)
            if (result is Result.Failed) return@run result
            val counts = (result as Result.Success).value
            val now = clock.instant()
            val checked = validateCompanySessionActor(actor, resolved, now, security)
            if (checked is Result.Failed) return@run checked
            if (counts.trackedRecipients > announcement.recipientCount)
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "announcement_delivery_unavailable")
                )
            Result.Success(AnnouncementDelivery(id, announcement.recipientCount, counts, now))
        }
    }
}
