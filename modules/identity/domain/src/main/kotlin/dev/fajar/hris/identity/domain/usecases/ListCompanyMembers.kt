package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import java.util.UUID

class ListCompanyMembers(
    private val members: MembershipRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<MemberAccount>> =
        actor.requirePermission("identity.manage").flatMap {
            if (limit !in 1..200) Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            else
                transactions.run(actor) {
                    members.list(requireNotNull(actor.companyId), after, limit)
                }
        }
}
