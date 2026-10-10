package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.ApprovalTemplate
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.time.Clock
import java.util.UUID

class GetApprovalTemplate(
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val security: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor, id: UUID, revision: Long?): Result<ApprovalTemplate> {
        val initial = actor.requirePermission("approvals.manage")
        if (initial is Result.Failed) return initial
        if (revision != null && revision < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_revision"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val approvalGuard = approvals.lock(company, shared = true)
            if (approvalGuard is Result.Failed) return@run approvalGuard
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
            val permission = (checked as Result.Success).value.requirePermission("approvals.manage")
            if (permission is Result.Failed) return@run permission
            val found =
                if (revision == null) approvals.findTemplate(company, id)
                else approvals.findTemplateRevision(company, id, revision)
            found.flatMap { template ->
                template?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.NOT_FOUND, "approval_template_not_found"))
            }
        }
    }
}
