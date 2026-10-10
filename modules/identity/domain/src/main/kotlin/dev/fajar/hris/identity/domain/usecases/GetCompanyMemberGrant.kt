package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

class GetCompanyMemberGrant(
    private val members: MembershipRepository,
    private val roles: RoleTemplateRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor, accountId: UUID): Result<CompanyMemberGrant> {
        val access = actor.requirePermission("identity.manage")
        if (access is Result.Failed) return access
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val companyGuard = identities.lockCompany(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("identity.manage")
            if (permission is Result.Failed) return@run permission
            members.find(company, accountId).flatMap { member ->
                if (member == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "company_member_not_found"))
                else
                    roles.application(company, accountId, member.version).map { grant ->
                        CompanyMemberGrant(
                            member,
                            grant ?: MembershipGrant(member.permissions, emptyList()),
                        )
                    }
            }
        }
    }
}
