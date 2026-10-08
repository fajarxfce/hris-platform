package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.approvalPermissions
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class ListApprovalInbox(
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<ApprovalRequest>> {
        if (limit !in 1..200) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            live.requirePermission("approvals.read").flatMap {
                approvals.inbox(
                    company,
                    actor.accountId,
                    "approvals.manage" in live.permissions,
                    ApprovalKind.entries
                        .filter { kind -> approvalPermissions(kind).any { it in live.permissions } }
                        .toSet(),
                    clock.instant(),
                    after,
                    limit,
                )
            }
        }
    }
}
