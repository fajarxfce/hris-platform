package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollPublicationReadinessRow
import dev.fajar.hris.schema.tables.records.PayrollFinalizationsRecord
import java.time.OffsetDateTime
import java.util.UUID

interface PayrollFinalizationDataSource {
    fun find(company: UUID, id: UUID): PayrollFinalizationsRecord?

    fun forJob(company: UUID, job: UUID): PayrollFinalizationsRecord?

    fun latest(company: UUID, run: UUID): PayrollFinalizationsRecord?

    fun list(company: UUID, run: UUID, after: Int?, limit: Int): List<PayrollFinalizationsRecord>

    fun readiness(company: UUID, run: UUID): PayrollPublicationReadinessRow

    fun insert(row: PayrollFinalizationsRecord)

    fun insertAssessments(company: UUID, finalization: UUID, at: OffsetDateTime)

    fun publish(company: UUID, id: UUID, at: OffsetDateTime): Boolean
}
