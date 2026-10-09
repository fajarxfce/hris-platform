package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollPaymentDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPaymentRepository
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredPayrollPaymentRepository(private val source: PayrollPaymentDataSource) :
    PayrollPaymentRepository {
    override fun progress(
        companyId: UUID,
        assessmentId: UUID,
        allowedEmployees: Set<UUID>?,
    ): Result<PayrollPaymentProgress?> = safeDatabaseCall {
        source.progressHead(companyId, assessmentId, allowedEmployees)?.let { head ->
            PayrollPaymentProgress(
                head.assessmentId,
                head.version,
                source.progress(companyId, assessmentId).map {
                    PayrollPaymentAttempt(
                        it.batchId,
                        it.itemId,
                        PayrollPaymentItemStatus.valueOf(it.status),
                        it.createdAt.toInstant(),
                        it.releasedAt?.toInstant(),
                        it.resolvedAt?.toInstant(),
                    )
                },
            )
        }
    }

    override fun lock(companyId: UUID, shared: Boolean): Result<Unit> = safeDatabaseCall {
        source.lock(companyId, shared)
    }

    override fun capacity(companyId: UUID): Result<PayrollPaymentCapacity> = safeDatabaseCall {
        source.capacity(companyId).let { PayrollPaymentCapacity(it.totalBatches, it.openBatches) }
    }

    override fun candidates(
        companyId: UUID,
        assessmentIds: Set<UUID>,
    ): Result<List<PayrollPayable>> = safeDatabaseCall {
        source.candidates(companyId, assessmentIds).map { it.toPayable() }
    }

    override fun occupiedAssessments(companyId: UUID, assessmentIds: Set<UUID>): Result<Set<UUID>> =
        safeDatabaseCall {
            source.occupiedAssessments(companyId, assessmentIds)
        }

    override fun attempts(companyId: UUID, assessmentIds: Set<UUID>): Result<Map<UUID, Int>> =
        safeDatabaseCall {
            source.attempts(companyId, assessmentIds)
        }

    override fun payables(
        companyId: UUID,
        from: Instant,
        until: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPayable>> = safeDatabaseCall {
        val rows =
            source.payables(
                companyId,
                from.atOffset(ZoneOffset.UTC),
                until.atOffset(ZoneOffset.UTC),
                after,
                limit + 1,
            )
        Page(
            rows.take(limit).map { it.toPayable() },
            if (rows.size > limit) rows[limit - 1].assessmentId.toString() else null,
        )
    }

    override fun find(companyId: UUID, id: UUID): Result<PayrollPaymentBatch?> = safeDatabaseCall {
        source.find(companyId, id)?.toBatch(source.items(companyId, id))
    }

    override fun list(
        companyId: UUID,
        from: Instant,
        until: Instant,
        status: PayrollPaymentBatchStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPaymentSummary>> = safeDatabaseCall {
        val rows =
            source.list(
                companyId,
                from.atOffset(ZoneOffset.UTC),
                until.atOffset(ZoneOffset.UTC),
                status?.name,
                after,
                limit + 1,
            )
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].batch.id.toString() else null,
        )
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollPaymentAction>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toAction() },
            if (rows.size > limit) rows[limit - 1].version.toString() else null,
        )
    }

    override fun results(companyId: UUID, id: UUID): Result<List<PayrollPaymentReconciliation>> =
        safeDatabaseCall {
            val actions = source.history(companyId, id, null, 102).associateBy { it.version }
            source
                .results(companyId, id)
                .groupBy { requireNotNull(it.batchVersion) }
                .map { (version, rows) ->
                    val first = rows.first()
                    val action = requireNotNull(actions[version])
                    PayrollPaymentReconciliation(
                        id,
                        version,
                        requireNotNull(first.actorId),
                        requireNotNull(first.recordedAt).toInstant(),
                        PayrollPaymentBatchStatus.valueOf(requireNotNull(action.status)),
                        rows.map { it.toResult() },
                    )
                }
        }

    override fun prepare(
        actor: Actor,
        batch: PayrollPaymentBatch,
        reason: String,
    ): Result<MutationReceipt> = safeDatabaseCall {
        val company = requireNotNull(actor.companyId)
        source.insert(batch.toRecord(company))
        source.insertItems(batch.items.map { it.toRecord(company, batch.id) })
        source.action(
            PayrollPaymentAction(
                    batch.id,
                    0,
                    PayrollPaymentActionKind.PREPARED,
                    batch.status,
                    actor.accountId,
                    reason,
                    batch.createdAt,
                )
                .toRecord(company)
        )
        MutationReceipt(batch.id, 0)
    }

    override fun transition(
        actor: Actor,
        batch: PayrollPaymentBatch,
        status: PayrollPaymentBatchStatus,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advance(
                    requireNotNull(actor.companyId),
                    batch.id,
                    batch.version,
                    status.name,
                    if (status == PayrollPaymentBatchStatus.RELEASED) actor.accountId else null,
                    if (status == PayrollPaymentBatchStatus.RELEASED) at.atOffset(ZoneOffset.UTC)
                    else null,
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val company = requireNotNull(actor.companyId)
                    check(
                        source.transitionItems(
                            company,
                            batch.id,
                            version,
                            if (status == PayrollPaymentBatchStatus.RELEASED) "PENDING"
                            else "CANCELLED",
                        ) == batch.itemCount
                    )
                    source.action(
                        PayrollPaymentAction(
                                batch.id,
                                version,
                                if (status == PayrollPaymentBatchStatus.RELEASED)
                                    PayrollPaymentActionKind.RELEASED
                                else PayrollPaymentActionKind.CANCELLED,
                                status,
                                actor.accountId,
                                reason,
                                at,
                            )
                            .toRecord(company)
                    )
                    MutationReceipt(batch.id, version)
                }
            }

    override fun reconcile(
        actor: Actor,
        batch: PayrollPaymentBatch,
        reconciliation: PayrollPaymentReconciliation,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advance(
                    requireNotNull(actor.companyId),
                    batch.id,
                    batch.version,
                    reconciliation.resultingStatus.name,
                    batch.releasedBy,
                    batch.releasedAt?.atOffset(ZoneOffset.UTC),
                )
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    val company = requireNotNull(actor.companyId)
                    for (result in reconciliation.results) check(
                        source.reconcileItem(
                            company,
                            batch.id,
                            result.itemId,
                            version,
                            result.status.name,
                            result.transactionReference,
                            result.occurredAt.atOffset(ZoneOffset.UTC),
                        ) == 1
                    )
                    source.action(
                        PayrollPaymentAction(
                                batch.id,
                                version,
                                PayrollPaymentActionKind.RECONCILED,
                                reconciliation.resultingStatus,
                                actor.accountId,
                                reason,
                                reconciliation.recordedAt,
                            )
                            .toRecord(company)
                    )
                    source.recordResults(reconciliation.toRecords(company))
                    MutationReceipt(batch.id, version)
                }
            }
}
