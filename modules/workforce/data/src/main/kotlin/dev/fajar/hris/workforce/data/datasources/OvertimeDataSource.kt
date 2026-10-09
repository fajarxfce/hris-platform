package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID

interface OvertimeDataSource {
    fun lock(company: UUID, employee: UUID, shared: Boolean)

    fun find(company: UUID, id: UUID): OvertimeRequestsRecord?

    fun count(company: UUID, employee: UUID, from: LocalDate, until: LocalDate): Int

    fun overlaps(
        company: UUID,
        employee: UUID,
        startsAt: OffsetDateTime,
        endsAt: OffsetDateTime,
    ): Boolean

    fun unresolved(company: UUID, from: LocalDate, until: LocalDate): Boolean

    fun approved(
        company: UUID,
        employee: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<OvertimeRequestsRecord>

    fun list(
        company: UUID,
        employee: UUID?,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        after: UUID?,
        limit: Int,
    ): List<OvertimeRequestsRecord>

    fun history(company: UUID, id: UUID, after: Long?, limit: Int): List<OvertimeChangesRecord>

    fun insert(row: OvertimeRequestsRecord)

    fun update(row: OvertimeRequestsRecord, expectedVersion: Long): Long?

    fun append(row: OvertimeChangesRecord)
}
