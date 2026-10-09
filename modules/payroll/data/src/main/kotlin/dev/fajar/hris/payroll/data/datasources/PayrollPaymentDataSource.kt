package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID

interface PayrollPaymentDataSource {
    fun progressHead(
        company: UUID,
        assessment: UUID,
        allowedEmployees: Set<UUID>?,
    ): PayrollPaymentHeadRow?

    fun progress(company: UUID, assessment: UUID): List<PayrollPaymentProgressRow>

    fun lock(company: UUID, shared: Boolean)

    fun capacity(company: UUID): PayrollPaymentCapacityRow

    fun candidates(company: UUID, assessments: Set<UUID>): List<PayrollPayableCandidatesRecord>

    fun occupiedAssessments(company: UUID, assessments: Set<UUID>): Set<UUID>

    fun attempts(company: UUID, assessments: Set<UUID>): Map<UUID, Int>

    fun payables(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<PayrollPayableCandidatesRecord>

    fun find(company: UUID, id: UUID): PayrollPaymentBatchesRecord?

    fun items(company: UUID, id: UUID): List<PayrollPaymentItemsRecord>

    fun list(
        company: UUID,
        from: OffsetDateTime,
        until: OffsetDateTime,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<PayrollPaymentSummaryRow>

    fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): List<PayrollPaymentActionsRecord>

    fun results(company: UUID, id: UUID): List<PayrollPaymentResultsRecord>

    fun insert(row: PayrollPaymentBatchesRecord)

    fun insertItems(rows: List<PayrollPaymentItemsRecord>)

    fun action(row: PayrollPaymentActionsRecord)

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

    fun recordResults(rows: List<PayrollPaymentResultsRecord>)
}
