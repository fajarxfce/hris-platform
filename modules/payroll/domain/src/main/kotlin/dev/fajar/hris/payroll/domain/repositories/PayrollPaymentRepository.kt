package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.Instant
import java.util.UUID

interface PayrollPaymentRepository {
    fun progress(
        companyId: UUID,
        assessmentId: UUID,
        allowedEmployees: Set<UUID>?,
    ): Result<PayrollPaymentProgress?>

    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun capacity(companyId: UUID): Result<PayrollPaymentCapacity>

    fun candidates(companyId: UUID, assessmentIds: Set<UUID>): Result<List<PayrollPayable>>

    fun occupiedAssessments(companyId: UUID, assessmentIds: Set<UUID>): Result<Set<UUID>>

    fun attempts(companyId: UUID, assessmentIds: Set<UUID>): Result<Map<UUID, Int>>

    fun payables(
        companyId: UUID,
        from: Instant,
        until: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPayable>>

    fun find(companyId: UUID, id: UUID): Result<PayrollPaymentBatch?>

    fun list(
        companyId: UUID,
        from: Instant,
        until: Instant,
        status: PayrollPaymentBatchStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPaymentSummary>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollPaymentAction>>

    fun results(companyId: UUID, id: UUID): Result<List<PayrollPaymentReconciliation>>

    fun prepare(actor: Actor, batch: PayrollPaymentBatch, reason: String): Result<MutationReceipt>

    fun transition(
        actor: Actor,
        batch: PayrollPaymentBatch,
        status: PayrollPaymentBatchStatus,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt>

    fun reconcile(
        actor: Actor,
        batch: PayrollPaymentBatch,
        reconciliation: PayrollPaymentReconciliation,
        reason: String,
    ): Result<MutationReceipt>
}
