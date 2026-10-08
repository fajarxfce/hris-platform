package dev.fajar.hris.expenses.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.data.datasources.ExpensePaymentDataSource
import dev.fajar.hris.expenses.data.mappers.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePaymentRepository
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StoredExpensePaymentRepository(private val source: ExpensePaymentDataSource) :
    ExpensePaymentRepository {
    override fun progress(companyId: UUID, claimId: UUID): Result<List<ExpensePaymentProgress>> =
        safeDatabaseCall {
            source.progress(companyId, claimId).map {
                ExpensePaymentProgress(
                    it.batchId,
                    it.itemId,
                    ExpensePaymentItemStatus.valueOf(it.status),
                    it.createdAt.toInstant(),
                    it.releasedAt?.toInstant(),
                    it.settledAt?.toInstant(),
                )
            }
        }

    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun capacity(companyId: UUID): Result<ExpensePaymentCapacity> = safeDatabaseCall {
        source.capacity(companyId).let { ExpensePaymentCapacity(it.totalBatches, it.openBatches) }
    }

    override fun candidates(
        companyId: UUID,
        submissionIds: Set<UUID>,
    ): Result<List<ExpensePayable>> = safeDatabaseCall {
        source.candidates(companyId, submissionIds).map { it.toPayable() }
    }

    override fun occupiedClaims(companyId: UUID, claimIds: Set<UUID>): Result<Set<UUID>> =
        safeDatabaseCall {
            source.occupiedClaims(companyId, claimIds)
        }

    override fun attempts(companyId: UUID, claimIds: Set<UUID>): Result<Map<UUID, Int>> =
        safeDatabaseCall {
            source.attempts(companyId, claimIds)
        }

    override fun payables(
        companyId: UUID,
        from: Instant,
        until: Instant,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpensePayable>> = safeDatabaseCall {
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
            if (rows.size > limit) rows[limit - 1].claimId.toString() else null,
        )
    }

    override fun find(companyId: UUID, id: UUID): Result<ExpensePaymentBatch?> = safeDatabaseCall {
        source.find(companyId, id)?.toBatch(source.items(companyId, id))
    }

    override fun list(
        companyId: UUID,
        from: Instant,
        until: Instant,
        status: ExpensePaymentBatchStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<ExpensePaymentSummary>> = safeDatabaseCall {
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
    ): Result<Page<ExpensePaymentAction>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toAction() },
            if (rows.size > limit) rows[limit - 1].version.toString() else null,
        )
    }

    override fun results(companyId: UUID, id: UUID): Result<List<ExpensePaymentReconciliation>> =
        safeDatabaseCall {
            val actions = source.history(companyId, id, null, 102).associateBy { it.version }
            source
                .results(companyId, id)
                .groupBy { requireNotNull(it.batchVersion) }
                .map { (version, rows) ->
                    val first = rows.first()
                    val action = requireNotNull(actions[version])
                    ExpensePaymentReconciliation(
                        id,
                        version,
                        requireNotNull(first.actorId),
                        requireNotNull(first.recordedAt).toInstant(),
                        ExpensePaymentBatchStatus.valueOf(requireNotNull(action.status)),
                        rows.map { it.toResult() },
                    )
                }
        }

    override fun prepare(
        actor: Actor,
        batch: ExpensePaymentBatch,
        reason: String,
    ): Result<MutationReceipt> = safeDatabaseCall {
        val company = requireNotNull(actor.companyId)
        source.insert(batch.toRecord(company))
        source.insertItems(batch.items.map { it.toRecord(company, batch.id) })
        source.action(
            ExpensePaymentAction(
                    batch.id,
                    0,
                    ExpensePaymentActionKind.PREPARED,
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
        batch: ExpensePaymentBatch,
        status: ExpensePaymentBatchStatus,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.advance(
                    requireNotNull(actor.companyId),
                    batch.id,
                    batch.version,
                    status.name,
                    if (status == ExpensePaymentBatchStatus.RELEASED) actor.accountId else null,
                    if (status == ExpensePaymentBatchStatus.RELEASED) at.atOffset(ZoneOffset.UTC)
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
                            if (status == ExpensePaymentBatchStatus.RELEASED) "PENDING"
                            else "CANCELLED",
                        ) == batch.itemCount
                    )
                    source.action(
                        ExpensePaymentAction(
                                batch.id,
                                version,
                                if (status == ExpensePaymentBatchStatus.RELEASED)
                                    ExpensePaymentActionKind.RELEASED
                                else ExpensePaymentActionKind.CANCELLED,
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
        batch: ExpensePaymentBatch,
        reconciliation: ExpensePaymentReconciliation,
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
                        ExpensePaymentAction(
                                batch.id,
                                version,
                                ExpensePaymentActionKind.RECONCILED,
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
