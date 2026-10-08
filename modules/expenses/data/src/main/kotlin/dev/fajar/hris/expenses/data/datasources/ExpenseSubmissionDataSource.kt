package dev.fajar.hris.expenses.data.datasources

import dev.fajar.hris.expenses.data.models.*
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface ExpenseSubmissionDataSource {
    fun contributors(company: UUID, claim: UUID): Set<UUID>

    fun find(company: UUID, id: UUID): ExpenseSubmissionsRecord?

    fun list(company: UUID, claim: UUID, after: Int?, limit: Int): List<ExpenseSubmissionSummaryRow>

    fun lines(company: UUID, id: UUID): List<ExpenseSubmittedLineRow>

    fun receipts(company: UUID, id: UUID): List<ExpenseSubmittedReceiptsRecord>

    fun duplicateDigests(company: UUID, exceptClaim: UUID, digests: Set<String>): Set<String>

    fun insert(row: ExpenseSubmissionsRecord)

    fun insertLines(rows: List<ExpenseSubmittedLinesRecord>)

    fun insertReceipts(rows: List<ExpenseSubmittedReceiptsRecord>)
}
