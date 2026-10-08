package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.Delegation
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock

class ListMyDelegations(
    private val approvals: ApprovalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<List<Delegation>> =
        actor.requirePermission("approvals.read").flatMap {
            transactions.run(actor) {
                approvals.delegations(
                    requireNotNull(actor.companyId),
                    actor.accountId,
                    clock.instant(),
                )
            }
        }
}
