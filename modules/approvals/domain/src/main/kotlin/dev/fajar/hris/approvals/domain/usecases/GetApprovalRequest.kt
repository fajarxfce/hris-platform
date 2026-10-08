package dev.fajar.hris.approvals.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import java.time.Clock
import java.util.UUID

class GetApprovalRequest(
    private val approvals: ApprovalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, id: UUID): Result<ApprovalRequest> =
        transactions.run(actor) {
            approvals.find(requireNotNull(actor.companyId), id).flatMap { request ->
                if (request == null)
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "approval_not_found"))
                else
                    approvals
                        .delegations(
                            requireNotNull(actor.companyId),
                            actor.accountId,
                            clock.instant(),
                        )
                        .flatMap { delegations ->
                            val assigned = request.stages.flatMap { it.assignees }.toSet()
                            val now = clock.instant()
                            val involved =
                                actor.accountId == request.authorId ||
                                    actor.accountId == request.requesterId ||
                                    actor.accountId in assigned ||
                                    delegations.any {
                                        it.active &&
                                            it.toAccount == actor.accountId &&
                                            it.kind == request.kind &&
                                            it.fromAccount in assigned &&
                                            !now.isBefore(it.validFrom) &&
                                            now.isBefore(it.validUntil)
                                    }
                            if ("approvals.manage" in actor.permissions || involved)
                                Result.Success(request)
                            else Result.Failed(Failure(FailureKind.NOT_FOUND, "approval_not_found"))
                        }
            }
        }
}
