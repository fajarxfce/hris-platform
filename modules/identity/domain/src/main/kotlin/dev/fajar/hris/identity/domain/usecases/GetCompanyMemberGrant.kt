package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import java.util.UUID

class GetCompanyMemberGrant(
    private val members: MembershipRepository,
    private val roles: RoleTemplateRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, accountId: UUID): Result<CompanyMemberGrant> {
        val access = actor.requirePermission("identity.manage")
        if (access is Result.Failed) return access
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
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
