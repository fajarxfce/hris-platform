package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.CompanyRoleTemplate
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.identity.domain.repositories.RoleTemplateRepository
import java.util.UUID

class ListRoleTemplates(
    private val roles: RoleTemplateRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<CompanyRoleTemplate>> {
        val access = actor.requirePermission("identity.manage")
        if (access is Result.Failed) return access
        if (limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
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
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("identity.manage")
            if (permission is Result.Failed) return@run permission
            roles.list(company, after, limit)
        }
    }
}
