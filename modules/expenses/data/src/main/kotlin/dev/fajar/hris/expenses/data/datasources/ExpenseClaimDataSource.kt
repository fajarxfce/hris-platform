package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.time.OffsetDateTime
import java.util.UUID

interface ExpenseClaimDataSource {
    fun lock(company: UUID)

    fun capacity(company: UUID, account: UUID): ExpenseCapacityRow

    fun find(company: UUID, id: UUID): ExpenseClaimsRecord?

    fun draft(company: UUID, id: UUID, revision: Int): ExpenseDraftsRecord?

    fun list(
        company: UUID,
        employment: UUID?,
        status: String?,
        from: OffsetDateTime,
        until: OffsetDateTime,
        after: UUID?,
        limit: Int,
    ): List<ExpenseClaimSummaryRow>

    fun drafts(company: UUID, id: UUID, after: Int?, limit: Int): List<ExpenseDraftsRecord>

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<ExpenseClaimChangesRecord>

    fun lines(company: UUID, id: UUID, revisions: Set<Int>): List<ExpenseDraftLinesRecord>

    fun receipts(company: UUID, id: UUID, revisions: Set<Int>): List<ExpenseDraftReceiptsRecord>

    fun insert(row: ExpenseClaimsRecord)

    fun advance(company: UUID, id: UUID, version: Long, revision: Int): Long?

    fun cancel(company: UUID, id: UUID, version: Long): Long?

    fun append(row: ExpenseDraftsRecord)

    fun insertLines(rows: List<ExpenseDraftLinesRecord>)

    fun insertReceipts(rows: List<ExpenseDraftReceiptsRecord>)

    fun appendChange(row: ExpenseClaimChangesRecord)
}
