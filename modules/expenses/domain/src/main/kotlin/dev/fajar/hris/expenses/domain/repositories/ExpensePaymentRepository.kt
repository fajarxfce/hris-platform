package dev.fajar.hris.expenses.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import java.time.Instant
import java.util.UUID

interface ExpensePaymentRepository {
    fun progress(companyId: UUID, claimId: UUID): Result<List<ExpensePaymentProgress>>

    fun lock(companyId: UUID): Result<Unit>

    fun capacity(companyId: UUID): Result<ExpensePaymentCapacity>

    fun candidates(companyId: UUID, submissionIds: Set<UUID>): Result<List<ExpensePayable>>

    fun occupiedClaims(companyId: UUID, claimIds: Set<UUID>): Result<Set<UUID>>

    fun attempts(companyId: UUID, claimIds: Set<UUID>): Result<Map<UUID, Int>>

    fun payables(
        companyId: UUID,
        from: Instant,
        until: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpensePayable>>

    fun find(companyId: UUID, id: UUID): Result<ExpensePaymentBatch?>

    fun list(
        companyId: UUID,
        from: Instant,
        until: Instant,
        status: ExpensePaymentBatchStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpensePaymentSummary>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpensePaymentAction>>

    fun results(companyId: UUID, id: UUID): Result<List<ExpensePaymentReconciliation>>

    fun prepare(actor: Actor, batch: ExpensePaymentBatch, reason: String): Result<MutationReceipt>

    fun transition(
        actor: Actor,
        batch: ExpensePaymentBatch,
        status: ExpensePaymentBatchStatus,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt>

    fun reconcile(
        actor: Actor,
        batch: ExpensePaymentBatch,
        reconciliation: ExpensePaymentReconciliation,
        reason: String,
    ): Result<MutationReceipt>
}
