package dev.fajar.hris.organization.data.datasources

import dev.fajar.hris.schema.tables.Companies.COMPANIES
import dev.fajar.hris.schema.tables.records.CompaniesRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresCompanyDataSource(private val sql: DSLContext) : CompanyDataSource {
    override fun find(id: UUID): CompaniesRecord? =
        sql.selectFrom(COMPANIES).where(COMPANIES.ID.eq(id)).fetchOne()

    override fun insert(id: UUID, code: String, name: String, timezone: String): CompaniesRecord =
        sql.insertInto(COMPANIES)
            .set(COMPANIES.ID, id)
            .set(COMPANIES.CODE, code)
            .set(COMPANIES.NAME, name)
            .set(COMPANIES.TIMEZONE, timezone)
            .returning()
            .fetchSingle()

    override fun update(
        id: UUID,
        code: String,
        name: String,
        timezone: String,
        version: Long,
    ): CompaniesRecord? =
        sql.update(COMPANIES)
            .set(COMPANIES.CODE, code)
            .set(COMPANIES.NAME, name)
            .set(COMPANIES.TIMEZONE, timezone)
            .set(COMPANIES.VERSION, version + 1)
            .where(COMPANIES.ID.eq(id))
            .and(COMPANIES.VERSION.eq(version))
            .returning()
            .fetchOne()
}
