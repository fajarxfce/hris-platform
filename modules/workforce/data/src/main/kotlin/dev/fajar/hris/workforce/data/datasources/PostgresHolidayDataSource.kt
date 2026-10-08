package dev.fajar.hris.workforce.data.datasources

import dev.fajar.hris.schema.tables.HolidayRevisions.HOLIDAY_REVISIONS as H
import dev.fajar.hris.schema.tables.WorkHolidays.WORK_HOLIDAYS as D
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID
import org.jooq.DSLContext

class PostgresHolidayDataSource(private val sql: DSLContext) : HolidayDataSource {
    override fun find(companyId: UUID, id: UUID): WorkHolidaysRecord? =
        sql.selectFrom(D).where(D.COMPANY_ID.eq(companyId), D.ID.eq(id)).fetchOne()

    override fun list(
        companyId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): List<WorkHolidaysRecord> =
        sql.selectFrom(D)
            .where(D.COMPANY_ID.eq(companyId))
            .and(D.WORK_DATE.between(from, until))
            .orderBy(D.WORK_DATE)
            .fetch()

    override fun insert(row: WorkHolidaysRecord) {
        sql.insertInto(D).set(row).execute()
    }

    override fun update(row: WorkHolidaysRecord, expectedVersion: Long): Long? =
        sql.update(D)
            .set(D.WORK_DATE, row.workDate)
            .set(D.NAME, row.name)
            .set(D.ACTIVE, row.active)
            .set(D.VERSION, expectedVersion + 1)
            .where(D.COMPANY_ID.eq(row.companyId))
            .and(D.ID.eq(row.id))
            .and(D.VERSION.eq(expectedVersion))
            .returning(D.VERSION)
            .fetchOne()
            ?.version

    override fun append(row: HolidayRevisionsRecord) {
        sql.insertInto(H).set(row).execute()
    }
}
