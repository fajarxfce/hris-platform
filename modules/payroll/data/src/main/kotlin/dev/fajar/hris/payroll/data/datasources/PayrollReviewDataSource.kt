package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollRunTotalsRow
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface PayrollReviewDataSource {
    fun find(company: UUID, id: UUID): PayrollReviewsRecord?

    fun list(company: UUID, run: UUID, after: Int?, limit: Int): List<PayrollReviewsRecord>

    fun latest(company: UUID, run: UUID): PayrollReviewsRecord?

    fun totals(company: UUID, run: UUID): PayrollRunTotalsRow

    fun insert(row: PayrollReviewsRecord)

    fun update(company: UUID, id: UUID, version: Long, status: String): Long?

    fun append(row: PayrollReviewChangesRecord)

    fun changes(company: UUID, review: UUID): List<PayrollReviewChangesRecord>
}
