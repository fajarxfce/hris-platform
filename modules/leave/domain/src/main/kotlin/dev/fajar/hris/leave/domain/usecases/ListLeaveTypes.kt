package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.LeaveType
import dev.fajar.hris.leave.domain.policies.canReadLeaveTypes
import dev.fajar.hris.leave.domain.repositories.LeavePolicyRepository
import java.time.LocalDate

class ListLeaveTypes(
    private val policies: LeavePolicyRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): Result<Page<LeaveType>> {
        if (!canReadLeaveTypes(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        if (limit !in 1..200 || (after?.length ?: 0) > 32 || asOf.year !in 1900..2200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
        return transactions.run(actor) {
            policies.list(requireNotNull(actor.companyId), asOf, after, limit)
        }
    }
}
