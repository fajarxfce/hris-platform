package dev.fajar.hris.expenses.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import java.time.Instant
import java.util.UUID

interface ExpenseClaimRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun capacity(companyId: UUID, accountId: UUID): Result<ExpenseClaimCapacity>

    fun find(companyId: UUID, id: UUID): Result<ExpenseClaim?>

    fun draft(companyId: UUID, id: UUID, revision: Int): Result<ExpenseDraft?>

    fun list(
        companyId: UUID,
        employmentId: UUID?,
        status: ExpenseClaimStatus?,
        from: Instant,
        until: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpenseClaimSummary>>

    fun drafts(companyId: UUID, id: UUID, after: Int?, limit: Int): Result<Page<ExpenseDraft>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpenseClaimChange>>

    fun saveDraft(
        actor: Actor,
        claim: ExpenseClaim,
        draft: ExpenseDraft,
        expectedVersion: Long?,
    ): Result<MutationReceipt>

    fun cancel(
        actor: Actor,
        claim: ExpenseClaim,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt>
}
