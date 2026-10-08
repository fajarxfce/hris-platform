package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

class ListApprovalInbox(
    private val approvals: ApprovalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<ApprovalRequest>> =
        actor.requirePermission("approvals.read").flatMap {
            if (limit !in 1..200) Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            else
                transactions.run(actor) {
                    approvals.inbox(
                        requireNotNull(actor.companyId),
                        actor.accountId,
                        "approvals.manage" in actor.permissions,
                        clock.instant(),
                        after,
                        limit,
                    )
                }
        }
}
