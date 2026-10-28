package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

class PostgresReportExportDataSource(private val sql: DSLContext) : ReportExportDataSource {
    private val projection =
        """select company_id,account_id,id,definition::text,columns::text,sources::text,client::text,created_at,expires_at,
        job_id,status,attempt,version,updated_at,snapshot_id,completed_rows,part_count,byte_count,reservation_count,reserved_bytes,
        failure_code,manifest_tag,finished_at,retired_at from report_exports"""

    override fun lockOwner(company: UUID, account: UUID) {
        sql.query(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                "hris:report-exports:$company:$account",
            )
            .execute()
    }

    override fun quota(company: UUID, account: UUID): ReportExportQuotaRow {
        val r =
            requireNotNull(
                sql.fetchOne(
                    """select
            (select count(*)::integer from (select 1 from report_exports where company_id=? and account_id=? and status in ('QUEUED','RENDERING') limit 3) active) active,
            (select count(*)::integer from (select 1 from report_exports where company_id=? and account_id=? and retired_at is null limit 11) retained) retained""",
                    company,
                    account,
                    company,
                    account,
                )
            )
        return ReportExportQuotaRow(
            r.get("active", Int::class.java),
            r.get("retained", Int::class.java),
        )
    }

    override fun create(row: ReportExportRow): Int =
        sql.query(
                """insert into report_exports(company_id,account_id,id,definition,columns,sources,client,created_at,expires_at,job_id,updated_at)
        select ?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::timestamptz,?::timestamptz,?,?::timestamptz
        where not exists(select 1 from report_exports where company_id=? and account_id=? and id=?)
        on conflict(company_id,account_id,id) do nothing""",
                row.company,
                row.account,
                row.id,
                row.definition,
                row.columns,
                row.sources,
                row.client,
                row.createdAt.utc(),
                row.expiresAt.utc(),
                row.job,
                row.updatedAt.utc(),
                row.company,
                row.account,
                row.id,
            )
            .execute()

    override fun find(company: UUID, account: UUID, id: UUID, lock: Boolean) =
        sql.fetchOne(
                "$projection where company_id=? and account_id=? and id=?${if(lock) " for update" else ""}",
                company,
                account,
                id,
            )
            ?.let(::row)

    override fun list(
        company: UUID,
        account: UUID,
        beforeAt: Instant?,
        beforeId: UUID?,
        limit: Int,
    ) =
        sql.fetch(
                """$projection where company_id=? and account_id=?
        and (?::timestamptz is null or (created_at,id)<(?::timestamptz,?::uuid)) order by created_at desc,id desc limit ?""",
                company,
                account,
                beforeAt?.utc(),
                beforeAt?.utc(),
                beforeId,
                limit,
            )
            .map(::row)

    override fun captured(company: UUID, account: UUID, id: UUID, version: Long, at: Instant) =
        sql.query(
                """update report_exports set snapshot_id=id,status='RENDERING',version=version+1,updated_at=?::timestamptz
        where company_id=? and account_id=? and id=? and version=? and status='QUEUED' and retired_at is null""",
                at.utc(),
                company,
                account,
                id,
                version,
            )
            .execute()

    override fun reserve(row: ReportExportUploadRow, version: Long) =
        sql.query(
                """insert into report_export_uploads(company_id,account_id,export_id,id,part_number,after_row,row_count,byte_offset,byte_count,sha256,created_at)
        select ?,?,?,?,?,?,?,?,?,?,?::timestamptz from report_exports where company_id=? and account_id=? and id=? and version=? and status='RENDERING' and retired_at is null for update""",
                row.company,
                row.account,
                row.export,
                row.id,
                row.number,
                row.afterRow,
                row.rows,
                row.offset,
                row.bytes,
                row.sha256,
                row.createdAt.utc(),
                row.company,
                row.account,
                row.export,
                version,
            )
            .execute()

    override fun accept(row: ReportExportPartRow, version: Long, at: Instant): Int {
        val p = row.upload
        return sql.query(
                """insert into report_export_parts(company_id,account_id,export_id,part_number,upload_id,etag,accepted_at)
            select e.company_id,e.account_id,e.id,u.part_number,u.id,?,?::timestamptz from report_exports e
            join report_export_uploads u on u.company_id=e.company_id and u.account_id=e.account_id and u.export_id=e.id
            where e.company_id=? and e.account_id=? and e.id=? and e.version=? and e.status='RENDERING' and e.retired_at is null
            and u.id=? and u.part_number=? and u.after_row=? and u.row_count=? and u.byte_offset=? and u.byte_count=? and u.sha256=? and u.created_at=?::timestamptz
            for update of e""",
                row.etag,
                at.utc(),
                p.company,
                p.account,
                p.export,
                version,
                p.id,
                p.number,
                p.afterRow,
                p.rows,
                p.offset,
                p.bytes,
                p.sha256,
                p.createdAt.utc(),
            )
            .execute()
    }

    override fun parts(company: UUID, account: UUID, id: UUID, limit: Int) =
        sql.fetch(
                """select u.*,p.etag from report_export_parts p
        join report_export_uploads u on u.company_id=p.company_id and u.account_id=p.account_id and u.export_id=p.export_id and u.id=p.upload_id
        where p.company_id=? and p.account_id=? and p.export_id=? order by p.part_number limit ?""",
                company,
                account,
                id,
                limit,
            )
            .map { r ->
                ReportExportPartRow(
                    ReportExportUploadRow(
                        r.get("id", UUID::class.java),
                        r.get("company_id", UUID::class.java),
                        r.get("account_id", UUID::class.java),
                        r.get("export_id", UUID::class.java),
                        r.get("part_number", Int::class.java),
                        r.get("after_row", Int::class.java),
                        r.get("row_count", Int::class.java),
                        r.get("byte_offset", Long::class.java),
                        r.get("byte_count", Int::class.java),
                        r.get("sha256", String::class.java),
                        r.at("created_at"),
                    ),
                    r.get("etag", String::class.java),
                )
            }

    override fun publish(
        company: UUID,
        account: UUID,
        id: UUID,
        version: Long,
        tag: String,
        at: Instant,
    ) =
        sql.query(
                """update report_exports set status='READY',manifest_tag=?,finished_at=?::timestamptz,updated_at=?::timestamptz,version=version+1
        where company_id=? and account_id=? and id=? and version=? and status='RENDERING' and retired_at is null""",
                tag,
                at.utc(),
                at.utc(),
                company,
                account,
                id,
                version,
            )
            .execute()

    override fun stop(
        company: UUID,
        account: UUID,
        id: UUID,
        version: Long,
        code: String,
        at: Instant,
    ) =
        sql.query(
                """update report_exports set status='STOPPED',failure_code=?,finished_at=?::timestamptz,updated_at=?::timestamptz,version=version+1
        where company_id=? and account_id=? and id=? and version=? and status in ('QUEUED','RENDERING') and retired_at is null""",
                code,
                at.utc(),
                at.utc(),
                company,
                account,
                id,
                version,
            )
            .execute()

    override fun resume(
        company: UUID,
        account: UUID,
        id: UUID,
        version: Long,
        job: UUID,
        at: Instant,
    ) =
        sql.query(
                """update report_exports set job_id=?,attempt=attempt+1,status=case when snapshot_id is null then 'QUEUED' else 'RENDERING' end,
        failure_code=null,finished_at=null,updated_at=?::timestamptz,version=version+1
        where company_id=? and account_id=? and id=? and version=? and status in ('QUEUED','RENDERING','STOPPED') and retired_at is null""",
                job,
                at.utc(),
                company,
                account,
                id,
                version,
            )
            .execute()

    override fun retire(company: UUID, account: UUID, id: UUID, version: Long, at: Instant) =
        sql.query(
                """update report_exports set retired_at=?::timestamptz,updated_at=?::timestamptz,version=version+1
        where company_id=? and account_id=? and id=? and version=? and status in ('READY','STOPPED') and retired_at is null
        and expires_at<=?::timestamptz and expires_at<=clock_timestamp()
        and (reservation_count=0 or expires_at+interval '1 hour'<=clock_timestamp())
        and not exists(select 1 from report_snapshot_rows r where r.company_id=report_exports.company_id and r.account_id=report_exports.account_id and r.snapshot_id=report_exports.id)
        and not exists(select 1 from object_cleanup_queue q where q.company_id=report_exports.company_id and q.resource_id=report_exports.id)""",
                at.utc(),
                at.utc(),
                company,
                account,
                id,
                version,
                at.utc(),
            )
            .execute()

    private fun row(r: Record) =
        ReportExportRow(
            r.get("company_id", UUID::class.java),
            r.get("account_id", UUID::class.java),
            r.get("id", UUID::class.java),
            r.get("definition", String::class.java),
            r.get("columns", String::class.java),
            r.get("sources", String::class.java),
            r.get("client", String::class.java),
            r.at("created_at"),
            r.at("expires_at"),
            r.get("job_id", UUID::class.java),
            r.get("status", String::class.java),
            r.get("attempt", Int::class.java),
            r.get("version", Long::class.java),
            r.at("updated_at"),
            r.get("snapshot_id", UUID::class.java),
            r.get("completed_rows", Int::class.java),
            r.get("part_count", Int::class.java),
            r.get("byte_count", Long::class.java),
            r.get("reservation_count", Int::class.java),
            r.get("reserved_bytes", Long::class.java),
            r.get("failure_code", String::class.java),
            r.get("manifest_tag", String::class.java),
            r.optionalAt("finished_at"),
            r.optionalAt("retired_at"),
        )

    private fun Instant.utc() = atOffset(ZoneOffset.UTC)

    private fun Record.at(key: String) = get(key, OffsetDateTime::class.java).toInstant()

    private fun Record.optionalAt(key: String) = get(key, OffsetDateTime::class.java)?.toInstant()
}
