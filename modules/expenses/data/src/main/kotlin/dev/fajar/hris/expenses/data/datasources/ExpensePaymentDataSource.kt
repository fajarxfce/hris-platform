package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID

interface ExpensePaymentDataSource {
    fun progress(company: UUID, claim: UUID): List<ExpensePaymentProgressRow>

    fun lock(company: UUID)

    fun capacity(company: UUID): ExpensePaymentCapacityRow

    fun candidates(company: UUID, submissions: Set<UUID>): List<ExpensePayableCandidatesRecord>

    fun occupiedClaims(company: UUID, claims: Set<UUID>): Set<UUID>

    fun attempts(company: UUID, claims: Set<UUID>): Map<UUID, Int>

    fun payables(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ExpensePayableCandidatesRecord>

    fun find(company: UUID, id: UUID): ExpensePaymentBatchesRecord?

    fun items(company: UUID, id: UUID): List<ExpensePaymentItemsRecord>

    fun list(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<ExpensePaymentSummaryRow>

    fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<ExpensePaymentActionsRecord>

    fun results(company: UUID, id: UUID): List<ExpensePaymentResultsRecord>

    fun insert(row: ExpensePaymentBatchesRecord)

    fun insertItems(rows: List<ExpensePaymentItemsRecord>)

    fun action(row: ExpensePaymentActionsRecord)

    fun advance(
        company: UUID,
        id: UUID,
        version: Long,
        status: String,
        releasedBy: UUID?,
        releasedAt: OffsetDateTime?,
    ): Long?

    fun transitionItems(company: UUID, id: UUID, batchVersion: Long, status: String): Int

    fun reconcileItem(
        company: UUID,
        batch: UUID,
        id: UUID,
        batchVersion: Long,
        status: String,
        reference: String?,
        occurredAt: OffsetDateTime,
    ): Int

    fun recordResults(rows: List<ExpensePaymentResultsRecord>)
}
