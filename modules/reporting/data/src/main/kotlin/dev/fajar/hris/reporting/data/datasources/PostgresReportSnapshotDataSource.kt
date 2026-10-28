package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.*
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

class PostgresReportSnapshotDataSource(private val sql: DSLContext) : ReportSnapshotDataSource {
    private val fields =
        "company_id,account_id,id,definition,columns,evaluated_at,expires_at,row_count,stored_bytes"

    override fun begin(row: ReportSnapshotHeaderRow): Int =
        sql.query(
                """insert into report_snapshots(company_id,account_id,id,definition,columns,evaluated_at,expires_at)
        values(?,?,?,?::jsonb,?::jsonb,?::timestamptz,?::timestamptz)
        on conflict(company_id,account_id,id) do nothing""",
                row.companyId,
                row.accountId,
                row.id,
                row.definition,
                row.columns,
                row.evaluatedAt.atOffset(ZoneOffset.UTC),
                row.expiresAt.atOffset(ZoneOffset.UTC),
            )
            .execute()

    override fun append(
        company: UUID,
        account: UUID,
        id: UUID,
        after: Int,
        rows: List<String>,
    ): Int =
        sql.query(
                """insert into report_snapshot_rows(company_id,account_id,snapshot_id,row_number,cells)
            select ?,?,?,?+ordinality,cells from jsonb_array_elements(?::jsonb) with ordinality r(cells,ordinality)""",
                company,
                account,
                id,
                after,
                rows.joinToString(",", "[", "]"),
            )
            .execute()

    override fun seal(company: UUID, account: UUID, id: UUID): ReportSnapshotHeaderRow? =
        sql.fetchOne(
                """update report_snapshots set row_count=staged_rows,stored_bytes=staged_bytes
        where company_id=? and account_id=? and id=? and row_count is null returning $fields""",
                company,
                account,
                id,
            )
            ?.let(::header)

    override fun find(company: UUID, account: UUID, id: UUID): ReportSnapshotHeaderRow? =
        sql.fetchOne(
                "select $fields from report_snapshots where company_id=? and account_id=? and id=?",
                company,
                account,
                id,
            )
            ?.let(::header)

    override fun rows(
        company: UUID,
        account: UUID,
        id: UUID,
        after: Int,
        limit: Int,
        maxBytes: Int,
    ): List<ReportSnapshotCellsRow> =
        sql.fetch(
                """with candidates as materialized (
                    select row_number,cells,byte_length from report_snapshot_rows where company_id=? and account_id=?
                    and snapshot_id=? and row_number>? order by row_number limit ?
                ), bounded as (
                    select row_number,cells,sum(byte_length) over(order by row_number rows unbounded preceding) page_bytes from candidates
                ) select row_number,cells from bounded where page_bytes<=? order by row_number""",
                company,
                account,
                id,
                after,
                limit,
                maxBytes,
            )
            .map {
                ReportSnapshotCellsRow(
                    it.get("row_number", Int::class.java),
                    it.get("cells", org.jooq.JSONB::class.java).data(),
                )
            }

    override fun deleteExpiredRows(
        company: UUID,
        account: UUID,
        id: UUID,
        at: Instant,
        limit: Int,
    ): Int =
        sql.query(
                """with selected as materialized (
            select r.company_id,r.account_id,r.snapshot_id,r.row_number
            from report_snapshot_rows r join report_snapshots s
                on s.company_id=r.company_id and s.account_id=r.account_id and s.id=r.snapshot_id
            where r.company_id=? and r.account_id=? and r.snapshot_id=? and s.expires_at<=?::timestamptz
                and s.expires_at<=clock_timestamp() and s.row_count is not null
            order by r.row_number limit ? for update of r skip locked
        ) delete from report_snapshot_rows r using selected s where
            (r.company_id,r.account_id,r.snapshot_id,r.row_number)=(s.company_id,s.account_id,s.snapshot_id,s.row_number)""",
                company,
                account,
                id,
                at.atOffset(ZoneOffset.UTC),
                limit,
            )
            .execute()

    private fun header(row: Record) =
        ReportSnapshotHeaderRow(
            row.get("company_id", UUID::class.java),
            row.get("account_id", UUID::class.java),
            row.get("id", UUID::class.java),
            row.get("definition", org.jooq.JSONB::class.java).data(),
            row.get("columns", org.jooq.JSONB::class.java).data(),
            row.get("evaluated_at", OffsetDateTime::class.java).toInstant(),
            row.get("expires_at", OffsetDateTime::class.java).toInstant(),
            row.get("row_count", Int::class.javaObjectType),
            row.get("stored_bytes", Long::class.javaObjectType),
        )
}
