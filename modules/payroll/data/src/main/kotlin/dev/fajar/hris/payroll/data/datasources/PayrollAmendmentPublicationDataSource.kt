package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.schema.tables.records.PayrollAmendmentPublicationsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface PayrollAmendmentPublicationDataSource {
    fun find(company: UUID, amendment: UUID, id: UUID): PayrollAmendmentPublicationsRecord?

    fun forJob(company: UUID, job: UUID): PayrollAmendmentPublicationsRecord?

    fun list(company: UUID, amendment: UUID): List<PayrollAmendmentPublicationsRecord>

    fun insert(row: PayrollAmendmentPublicationsRecord)

    fun finish(
        company: UUID,
        id: UUID,
        status: String,
        at: OffsetDateTime,
        actorId: UUID,
        code: String?,
    ): Boolean

    fun insertRevisions(company: UUID, publication: UUID, at: OffsetDateTime)

    fun advanceHeads(company: UUID, publication: UUID)
}
