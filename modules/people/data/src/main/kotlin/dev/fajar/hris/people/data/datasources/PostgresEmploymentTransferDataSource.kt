package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.Tables.EMPLOYMENT_TRANSFERS as T
import dev.fajar.hris.schema.tables.records.EmploymentTransfersRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresEmploymentTransferDataSource(private val sql: DSLContext) :
    EmploymentTransferDataSource {
    override fun insert(record: EmploymentTransfersRecord) {
        sql.executeInsert(record)
    }

    override fun forEmployee(companyId: UUID, id: UUID): List<EmploymentTransfersRecord> =
        sql.selectFrom(T)
            .where(
                T.SOURCE_COMPANY_ID.eq(companyId)
                    .and(T.SOURCE_EMPLOYMENT_ID.eq(id))
                    .or(T.TARGET_COMPANY_ID.eq(companyId).and(T.TARGET_EMPLOYMENT_ID.eq(id)))
            )
            .orderBy(T.EFFECTIVE_DATE, T.ID)
            .limit(2)
            .fetch()
}
