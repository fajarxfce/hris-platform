package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.ShiftRevisions.SHIFT_REVISIONS as H
import dev.fajar.hris.schema.tables.ShiftTemplates.SHIFT_TEMPLATES as S
import dev.fajar.hris.schema.tables.records.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class PostgresShiftDataSource(private val sql: DSLContext) : ShiftDataSource {
    override fun find(companyId: UUID, id: UUID): ShiftTemplatesRecord? =
        sql.selectFrom(S).where(S.COMPANY_ID.eq(companyId)).and(S.ID.eq(id)).fetchOne()

    override fun list(
        companyId: UUID,
        query: String,
        after: String?,
        limit: Int,
    ): List<ShiftTemplatesRecord> =
        sql.selectFrom(S)
            .where(S.COMPANY_ID.eq(companyId))
            .and(S.NAME.containsIgnoreCase(query).or(S.CODE.containsIgnoreCase(query)))
            .and(after?.let { S.CODE.gt(it) } ?: DSL.noCondition())
            .orderBy(S.CODE)
            .limit(limit)
            .fetch()

    override fun insert(row: ShiftTemplatesRecord) {
        sql.insertInto(S).set(row).execute()
    }

    override fun update(row: ShiftTemplatesRecord, expectedVersion: Long): Long? =
        sql.update(S)
            .set(S.DETAILS, row.details)
            .set(S.ACTIVE, row.active)
            .set(S.VERSION, expectedVersion + 1)
            .where(S.COMPANY_ID.eq(row.companyId))
            .and(S.ID.eq(row.id))
            .and(S.VERSION.eq(expectedVersion))
            .returning(S.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: ShiftRevisionsRecord) {
        sql.insertInto(H).set(row).execute()
    }
}
