package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.util.UUID

interface PayrollAmendmentReviewDataSource {
    fun find(company: UUID, id: UUID): PayrollAmendmentReviewsRecord?

    fun latest(company: UUID, amendmentId: UUID): PayrollAmendmentReviewsRecord?

    fun list(company: UUID, amendmentId: UUID): List<PayrollAmendmentReviewsRecord>

    fun insert(row: PayrollAmendmentReviewsRecord)

    fun update(company: UUID, id: UUID, version: Long, status: String): Long?

    fun append(row: PayrollAmendmentReviewChangesRecord)

    fun changes(company: UUID, id: UUID): List<PayrollAmendmentReviewChangesRecord>
}
