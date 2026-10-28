package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.SavedReportFilterRow
import java.time.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

class PostgresSavedReportFilterDataSource(private val sql: DSLContext) :
    SavedReportFilterDataSource {
    private val projection =
        """select company_id,account_id,id,version,created_at,updated_at,name,kind,
        definition_version,as_of,range_from,range_until,status,selected_companies from saved_report_filters"""

    override fun lock(company: UUID, account: UUID) {
        sql.query(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "hris:report-filters:$company:$account",
            )
            .execute()
    }

    override fun list(company: UUID, account: UUID, limit: Int): List<SavedReportFilterRow> =
        sql.fetch(
                "$projection where company_id=? and account_id=? and name is not null order by id limit ?",
                company,
                account,
                limit,
            )
            .map(::row)

    override fun countActive(company: UUID, account: UUID, limit: Int): Int =
        requireNotNull(
                sql.fetchOne(
                    """select count(*) as count from (select 1 from saved_report_filters
            where company_id=? and account_id=? and name is not null limit ?) bounded""",
                    company,
                    account,
                    limit,
                )
            )
            .get("count", Int::class.java)

    override fun find(company: UUID, account: UUID, id: UUID): SavedReportFilterRow? =
        sql.fetchOne(
                "$projection where company_id=? and account_id=? and id=?",
                company,
                account,
                id,
            )
            ?.let(::row)

    override fun save(row: SavedReportFilterRow, expectedVersion: Long?): Int {
        if (expectedVersion == null)
            return sql.query(
                    """
            insert into saved_report_filters(company_id,account_id,id,version,created_at,updated_at,name,kind,
                definition_version,as_of,range_from,range_until,status,selected_companies)
            values(?,?,?,?,?::timestamptz,?::timestamptz,?,?,?,?::date,?,?,?,?::uuid[]) on conflict(company_id,account_id,id) do nothing
        """,
                    row.companyId,
                    row.accountId,
                    row.id,
                    row.version,
                    row.createdAt.atOffset(ZoneOffset.UTC),
                    row.updatedAt.atOffset(ZoneOffset.UTC),
                    row.name,
                    row.kind,
                    row.definitionVersion,
                    row.asOf,
                    row.from,
                    row.until,
                    row.status,
                    row.companies.toTypedArray(),
                )
                .execute()
        return sql.query(
                """
            update saved_report_filters set version=?,updated_at=?::timestamptz,name=?,kind=?,definition_version=?,as_of=?::date,
                range_from=?,range_until=?,status=?,selected_companies=?::uuid[]
            where company_id=? and account_id=? and id=? and version=? and name is not null
        """,
                row.version,
                row.updatedAt.atOffset(ZoneOffset.UTC),
                row.name,
                row.kind,
                row.definitionVersion,
                row.asOf,
                row.from,
                row.until,
                row.status,
                row.companies.toTypedArray(),
                row.companyId,
                row.accountId,
                row.id,
                expectedVersion,
            )
            .execute()
    }

    private fun row(value: Record) =
        SavedReportFilterRow(
            value.get("company_id", UUID::class.java),
            value.get("account_id", UUID::class.java),
            value.get("id", UUID::class.java),
            value.get("version", Long::class.java),
            value.get("created_at", OffsetDateTime::class.java).toInstant(),
            value.get("updated_at", OffsetDateTime::class.java).toInstant(),
            value.get("name", String::class.java),
            value.get("kind", String::class.java),
            value.get("definition_version", String::class.java),
            value.get("as_of", LocalDate::class.java),
            value.get("range_from", String::class.java),
            value.get("range_until", String::class.java),
            value.get("status", String::class.java),
            value.get("selected_companies", Array<UUID>::class.java).toList(),
        )
}
